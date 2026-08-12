# 离线同声传译

完全本地离线运行的 Android 翻译与同声传译应用。

## 功能模式

- **模式 A**：纯手机同声传译（持续收音→翻译→扬声器只播译文）
- **模式 B**：蓝牙耳机双声道（左右声道各一种语言译文）
- **模式 C**：耳机+扬声器分离（耳机一种语言，扬声器另一种）
- **模式 D**：文本翻译（输入文本→翻译→朗读译文）
- **模式 E**：按住说话（按住录音→松开翻译→播放译文）

## 技术栈

- 翻译：llama.cpp + 本地 GGUF（HY-MT1.5-1.8B）
- 流式 ASR：Sherpa-ONNX OnlineRecognizer
- TTS：Sherpa-ONNX + Piper（中文/英文）
- 全程无网络依赖

## 下载 APK

预构建 Release：https://github.com/yanfyanqiu/overlay-translator/releases

> 首次使用需在 App 内通过"文件夹"图标选择手机本地已有的 HY-MT1.5 GGUF 模型文件。
