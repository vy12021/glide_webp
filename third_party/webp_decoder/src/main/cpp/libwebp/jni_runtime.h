//
// Created by LeoTesla on 2017/9/3.
//

#ifndef ANDROID_JNIRUNTIME_H
#define ANDROID_JNIRUNTIME_H

#include <jni.h>
#include <stdio.h>
#include "jni_log.h"

#define TO_STRING(ANY) #ANY

#define _JNI_METHOD(Package, Class, Name, Return)    JNIEXPORT Return JNICALL Java_##Package##_##Class##_##Name

#define _JNI_STATIC_METHOD(Package, Class, Name, Return)    JNIEXPORT Return JNICALL Java_##Package##_##Class##_##Name

#define JNI_METHOD(Package, Class, Name, Return)    _JNI_METHOD(Package, Class, Name, Return)
#define JNI_STATIC_METHOD(Package, Class, Name, Return)    _JNI_STATIC_METHOD(Package, Class, Name, Return)

extern int RedirectStdout(const char * file);

#endif //ANDROID_JNIRUNTIME_H
