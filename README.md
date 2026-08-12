# 本地翻译 Local Translator

基于 **llama.cpp 官方 `llama.android` 示例**改造的 Android 端侧翻译 App。

- 完全离线：使用本地 GGUF 模型（llama.cpp 推理，无网络请求）
- 中英互译：翻译方向默认跟随系统语言，可手动交换
- 拟人化 TTS：调用系统 TTS，自动选择目标语言的高质量语音，可自动朗读译文
- 界面语言跟随系统（中文 / English）
- 模型导入：通过系统文件选择器导入手机上的本地 `.gguf` 模型（如 Hy-MT、Sakura 等）

## 构建

GitHub Actions 已配置 `debug-build.yml`：推送到 `main` 分支自动构建，APK 上传到 Release `v0.1.0`（`LocalTranslator-debug.apk`）。

本地构建（Android Studio）需要先准备 llama.cpp 源码（JNI 层引用）：

```bash
git clone --depth 1 https://github.com/ggml-org/llama.cpp.git llama.cpp
```

然后在 Android Studio 中打开本仓库构建即可（需要 NDK 29.0.13113456、CMake 3.31.6、SDK 36）。

## 使用

1. 安装 APK（debug 版无签名，需允许"未知来源"）
2. 打开 App → 点击 **导入 GGUF 模型**，选择手机上的模型文件（如 `hy-mt-1.5-1.8b.Q4_K_M.gguf`）
3. 等待模型加载完成（"模型已就绪"）
4. 输入文字 → 点击 **翻译**
5. 开启 **自动朗读** 可让翻译结果自动朗读；也可点 **朗读** 手动播放

## 技术栈

- Kotlin + AppCompat + Material3（XML 布局）
- llama.cpp（`ggml-org/llama.cpp` master @ `680a9ae`）+ JNI
- 模块：`:app`（界面）、`:lib`（llama.cpp JNI 封装，源自 Arm 官方 AI Chat 示例）
- minSdk 26 / targetSdk 36 / arm64-v8a（含 x86_64）

## 已知限制

- Debug 构建，无正式签名
- 翻译 prompt 为通用指令（Hy-MT 等翻译模型效果最佳）
- 模型需兼容 llama.cpp（GGUF 格式，Q4_K_M 等常见量化均可）
