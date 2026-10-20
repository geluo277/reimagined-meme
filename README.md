# NeoXtractor Android（移植原型）

这是基于用户提供的 NeoXtractor Python 项目制作的 Android Studio 工程原型，不是原版 EXE 的直接转换。

## 当前功能
- Android 文件选择器选择 `.npk`
- 尝试读取 NXPK / EXPK 文件头和常见索引布局
- 展示条目签名、偏移、大小、压缩/加密标记
- 提供后续原始条目导出的 UI 入口

## 重要限制
原项目 GUI 使用 PySide6，不能直接打包成 Android APK。原项目的解密、压缩、EXPK 密钥配置、贴图/模型转换等逻辑依赖 Python 包及桌面 GUI。本原型尚未移植完整引擎，**不能视为可完整解包蛋仔派对 `script.npk` 的成品**。特殊加密或压缩格式会失败；ZIP 导出按钮目前只是界面入口，导出写入逻辑仍待完成。

## 构建 APK
1. 在电脑上使用 Android Studio 打开本目录。
2. 使用 JDK 17、Android Gradle Plugin 8.7.3、Android SDK 35。
3. 同步 Gradle 后选择 Build > Build APK(s)。

也可把工程上传到 GitHub 后配置 Actions 构建；当前工程还需要进一步实现完整条目导出并在真机上测试后，才适合发布。

## 上游项目
NeoXtractor： https://github.com/MarcosVLl2/NeoXtractor
