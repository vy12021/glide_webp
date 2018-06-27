//
// Created by LeoTesla on 2017/9/3.
//

#ifndef ANDROID_JNIRUNTIME_H
#define ANDROID_JNIRUNTIME_H

#include <jni.h>
#include "jni_log.h"

#define PACKAGE_ROOT com_bumptech_glide_webpdecoder

#define _JNI_METHOD(Package, Class, Name, Return)    JNIEXPORT Return JNICALL Java_##Package##_##Class##_native_1##Name

#define _JNI_STATIC_METHOD(Package, Class, Name, Return)    JNIEXPORT Return JNICALL Java_##Package##_##Class##_##Name

#define JNI_METHOD(Package, Class, Name, Return)    _JNI_METHOD(Package, Class, Name, Return)
#define JNI_STATIC_METHOD(Package, Class, Name, Return)    _JNI_STATIC_METHOD(Package, Class, Name, Return)

#endif //ANDROID_JNIRUNTIME_H
