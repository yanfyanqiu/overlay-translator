#pragma once
#include <android/log.h>

#define LOG_TAG "InferenceEngine"

#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__))
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

// Stub macros for compatibility with old code (no-op on older Android)
#define LOG_MIN_LEVEL ANDROID_LOG_VERBOSE
#define LOGi(...) ((void)0)
#define LOGd(...) ((void)0)
#define LOGw(...) ((void)0)
#define LOGe(...) ((void)0)
