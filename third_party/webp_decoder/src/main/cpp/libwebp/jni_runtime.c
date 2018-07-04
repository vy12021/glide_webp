//
// Created by LeoTesla on 2018/7/1.
//

#include "jni_runtime.h"

extern int RedirectStdout(const char * file) {
#if __ANDROID_API__ >= 24
    if (NULL != freopen64(file, "w", stdout)) {
        return 1;
    }
#else
    if (NULL != freopen(file, "w", stdout)) {
        return 1;
    }
#endif
    return 0;
}