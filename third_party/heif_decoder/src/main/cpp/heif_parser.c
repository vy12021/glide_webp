//
// Created by Leo on 3/20/2018.
//

#include <stdio.h>
#include <string.h>
#include <android/bitmap.h>
#include <malloc.h>
#include "x265.h"
#include "jni_runtime.h"
#include "tag.h"

#ifdef __cplusplus
extern "C" {
#endif

#define JAVA_METHOD_CONSTRUCTOR "<init>"


JNI_STATIC_METHOD(PACKAGE_ROOT, StandardHeifDecoder, nativeInitHeifParser, jlong)
(JNIEnv *env, jclass class, jobject byte_buffer) {
    uint8_t* buffer = (*env)->GetDirectBufferAddress(env, byte_buffer);
    size_t capacity = (size_t) (*env)->GetDirectBufferCapacity(env, byte_buffer);
    NalUnitType type = NAL_UNIT_ACCESS_UNIT_DELIMITER;
    x265_picture picture;
    x265_param *param = x265_param_alloc();
    x265_param_free(param);
    return (jlong) 1;
}

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardHeifDecoder, nativeGetHeifFrame, jint)
(JNIEnv *env, jclass class, jlong parser_pointer, jobject bitmap, jint frame_index){
    int index = frame_index;
    AndroidBitmapInfo bitmapInfo;
    AndroidBitmap_getInfo(env, bitmap, &bitmapInfo);
    if (bitmapInfo.height * bitmapInfo.height == 0) {
        LOGE("webp_parser", "nativeGetWebpFrame: Invalid bitmap!");
        return 0;
    }
    return 1;
}

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardHeifDecoder, nativeReleaseParser, void)
(JNIEnv *env, jclass class, jlong demux_pointer) {
    if (demux_pointer) {
        free((void *)demux_pointer);
    }
}

#ifdef __cplusplus
}
#endif