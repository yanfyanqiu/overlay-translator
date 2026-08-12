// logging.h — minSdk 29 compatible
#ifndef OFFLINE_INTERPRETER_LOGGING_H
#define OFFLINE_INTERPRETER_LOGGING_H

#include <android/log.h>

#ifndef LOG_TAG
#define LOG_TAG "oi-lib"
#endif

// __android_log_is_loggable is API 30+; guard it
static inline int oi_should_log(int prio) {
#if __ANDROID_API__ >= 30
    return __android_log_is_loggable(prio, LOG_TAG);
#else
    (void)prio;
    return 1;
#endif
}

#define OI_LOG(prio, fmt, ...) do { \
    if (oi_should_log(ANDROID_LOG_##prio)) { \
        __android_log_print(ANDROID_LOG_##prio, LOG_TAG, fmt, ##__VA_ARGS__); \
    } \
} while(0)

#define LOGV(fmt, ...) OI_LOG(VERBOSE, fmt, ##__VA_ARGS__)
#define LOGD(fmt, ...) OI_LOG(DEBUG,   fmt, ##__VA_ARGS__)
#define LOGI(fmt, ...) OI_LOG(INFO,    fmt, ##__VA_ARGS__)
#define LOGW(fmt, ...) OI_LOG(WARN,    fmt, ##__VA_ARGS__)
#define LOGE(fmt, ...) OI_LOG(ERROR,   fmt, ##__VA_ARGS__)

#endif // OFFLINE_INTERPRETER_LOGGING_H
