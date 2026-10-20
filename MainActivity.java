package com.neoxtractor.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Android UI prototype for NeoXtractor.
 * It reads NPK headers/index metadata and can export a raw payload archive.
 * Full NeoX decryption/decompression parity is not yet implemented in this Android port.
 */
public class MainActivity extends Activity {
    static final int PICK_NPK = 21;
    Uri selectedUri;
    TextView status;
    ListView listView;
    ArrayAdapter<String> adapter;
    ArrayList<String> rows = new ArrayList<>();
    ArrayList<Entry> entries = new ArrayList<>();
    String displayName = "archive.npk";

    static class Entry {
        long signature, offset, length, originalLength, crc;
        int zipFlag, encryptFlag;
        String name;
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 28, 24, 16);
        root.setBackgroundColor(0xFFF5F7FA);

        TextView title = new TextView(this);
        title.setText("NeoXtractor Mobile");
        title.setTextSize(25);
        title.setTextColor(0xFF172033);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = new TextView(this);
        sub.setText("NeoX / EXPK NPK 文件浏览与导出（测试版）");
        sub.setTextSize(14);
        sub.setTextColor(0xFF596579);
        sub.setPadding(0, 8, 0, 18);
        root.addView(sub);

        Button choose = new Button(this);
        choose.setText("选择 .npk 文件");
        choose.setOnClickListener(v -> pickFile());
        root.addView(choose, new LinearLayout.LayoutParams(-1, -2));

        Button export = new Button(this);
        export.setText("导出原始数据包（ZIP）");
        export.setOnClickListener(v -> exportRaw());
        root.addView(export, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("请选择一个 NPK 文件。");
        status.setTextSize(13);
        status.setTextColor(0xFF374151);
        status.setPadding(0, 12, 0, 12);
        root.addView(status);

        listView = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView note = new TextView(this);
        note.setText("注意：当前为 Android 移植原型，能读取部分标准 NPK 索引并导出原始条目；尚未实现原版全部解密、解压、贴图/模型转换功能。");
        note.setTextSize(12);
        note.setTextColor(0xFF8A4B16);
        note.setPadding(0, 8, 0, 4);
        root.addView(note);
        setContentView(root);
    }

    void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, PICK_NPK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == 22) {
            try { writeZip(data.getData()); status.setText("原始条目 ZIP 已导出。"); }
            catch (Exception ex) { status.setText("导出失败：" + ex.getMessage()); }
            return;
        }
        if (requestCode != PICK_NPK) return;
        selectedUri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        displayName = queryName(selectedUri);
        parseNpk();
    }

    String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "archive.npk";
    }

    long u32(ByteBuffer b) { return Integer.toUnsignedLong(b.getInt()); }

    void parseNpk() {
        rows.clear(); entries.clear(); adapter.notifyDataSetChanged();
        try (InputStream in = getContentResolver().openInputStream(selectedUri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("无法打开文件");
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            byte[] data = out.toByteArray();
            if (data.length < 24) throw new IOException("文件太小，不像有效 NPK");
            ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[4]; b.get(magic);
            String type = new String(magic, "US-ASCII");
            if (!type.equals("NXPK") && !type.equals("EXPK")) throw new IOException("无效文件头：" + type);
            long count = u32(b); long var1 = u32(b); long enc = u32(b);
            long hash = u32(b); long indexOffset = u32(b);
            if (count > 100000 || indexOffset < 24 || indexOffset >= data.length) throw new IOException("索引参数异常，可能是特殊加密格式");
            int infoSize;
            if (enc == 256 || hash == 2) infoSize = 28;
            else if (var1 == 1) infoSize = 32;
            else infoSize = (int)((data.length - indexOffset) / Math.max(1, count));
            if (infoSize != 28 && infoSize != 32) throw new IOException("暂不支持此索引结构（条目大小 " + infoSize + "）");
            long end = indexOffset + count * infoSize;
            if (end > data.length) throw new IOException("索引超出文件范围");
            for (int k = 0; k < count; k++) {
                Entry e = new Entry();
                int entryStart = (int)indexOffset + k * infoSize;
                ByteBuffer eb = ByteBuffer.wrap(data, entryStart, infoSize).slice().order(ByteOrder.LITTLE_ENDIAN);
                e.signature = infoSize == 28 ? Integer.toUnsignedLong(eb.getInt()) : eb.getLong();
                e.offset = Integer.toUnsignedLong(eb.getInt());
                e.length = Integer.toUnsignedLong(eb.getInt());
                e.originalLength = Integer.toUnsignedLong(eb.getInt());
                e.crc = Integer.toUnsignedLong(eb.getInt());
                eb.getInt(); // crc
                e.zipFlag = Short.toUnsignedInt(eb.getShort());
                e.encryptFlag = Short.toUnsignedInt(eb.getShort());
                e.name = String.format(Locale.ROOT, "条目 %04d  |  %08X  |  %s", k, e.signature, category(e.signature));
                entries.add(e); rows.add(e.name + "\n偏移 " + e.offset + " · 大小 " + e.length + " · 压缩标记 " + e.zipFlag + " · 加密标记 " + e.encryptFlag);
            }
            adapter.notifyDataSetChanged();
            status.setText(type + " · " + count + " 个条目 · 索引已读取。特殊加密索引可能无法正确解析。");
        } catch (Exception ex) {
            status.setText("读取失败：" + ex.getMessage());
            Toast.makeText(this, "读取失败：" + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    String category(long sig) { return "签名"; }

    void exportRaw() {
        if (selectedUri == null || entries.isEmpty()) {
            Toast.makeText(this, "请先选择并成功读取 NPK 文件", Toast.LENGTH_SHORT).show(); return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, displayName.replaceAll("(?i)\\\\.npk$", "") + "_raw.zip");
        startActivityForResult(i, 22);
    }

    @Override protected void onResume() { super.onResume(); }

    void writeZip(Uri dest) throws Exception {
        if (dest == null || selectedUri == null) throw new IOException("目标文件不可用");
        try (InputStream in = getContentResolver().openInputStream(selectedUri);
             ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("无法读取源文件");
            byte[] block = new byte[65536]; int n;
            while ((n = in.read(block)) > 0) buf.write(block, 0, n);
            byte[] archive = buf.toByteArray();
            try (OutputStream raw = getContentResolver().openOutputStream(dest);
                 ZipOutputStream zip = new ZipOutputStream(raw)) {
                if (raw == null) throw new IOException("无法创建目标文件");
                for (int i = 0; i < entries.size(); i++) {
                    Entry e = entries.get(i);
                    if (e.offset < 0 || e.length < 0 || e.offset + e.length > archive.length) continue;
                    String name = String.format(Locale.ROOT, "%04d_%08X.bin", i, e.signature);
                    zip.putNextEntry(new ZipEntry(name));
                    zip.write(archive, (int)e.offset, (int)e.length);
                    zip.closeEntry();
                }
            }
        }
    }
}
