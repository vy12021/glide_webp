//
// Created by Leo on 3/20/2018.
//

#include <stdio.h>
#include <string.h>
#include "jni_runtime.h"
#include "libwebp/src/webp/mux_types.h"
#include "libwebp/tools/webpinfo.h"

#ifdef __cplusplus
extern "C" {
#endif

#define JAVA_METHOD_CONSTRUCTOR "<init>"
#define JAVA_CLS_WEBP_HEADER "com/bumptech/glide/webpdecoder/WebpHeader"
#define JAVA_CLS_WEBP_FRAME "com/bumptech/glide/webpdecoder/WebpFrame"

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, getWebpInfo, jobject)
(JNIEnv *env, jclass clazz, jstring file) {
    const char *in_file;
    jboolean flag = JNI_TRUE;
    in_file = (*env)->GetStringUTFChars(env, file, &flag);
    int quiet = 0, show_diag = 0, show_summary = 0;
    int parse_bitstream = 0;
    WebPInfoStatus webp_info_status;
    WebPInfo webp_info;
    WebPData webp_data;
    WebPInfoInit(&webp_info);
    webp_info.quiet_ = quiet;
    webp_info.show_diagnosis_ = show_diag;
    webp_info.show_summary_ = show_summary;
    webp_info.parse_bitstream_ = parse_bitstream;
    if (in_file == NULL || !ReadFileToWebPData(in_file, &webp_data)) {
        webp_info_status = WEBP_INFO_INVALID_COMMAND;
        LOGE(TO_STRING(PACKAGE_ROOT), "Failed to open input file %s.\n", in_file);
    }
    if (!webp_info.quiet_) printf("File: %s\n", in_file);
    webp_info_status = AnalyzeWebP(&webp_info, &webp_data);

    switch (webp_info_status) {
        case WEBP_INFO_OK:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse complete: %s", GetWebPInfoDesc(&webp_info, NULL));
            break;
        case WEBP_INFO_PARSE_ERROR:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp PARSE_ERROR");
            break;
        case WEBP_INFO_BITSTREAM_ERROR:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse BITSTREAM_ERROR");
            break;
        case WEBP_INFO_INVALID_COMMAND:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse INVALID_COMMAND");
            break;
        case WEBP_INFO_INVALID_PARAM:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse INVALID_PARAM");
            break;
        case WEBP_INFO_MISSING_DATA:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse MISSING_DATA");
            break;
        case WEBP_INFO_TRUNCATED_DATA:
            LOGE(TO_STRING(PACKAGE_ROOT), "webp parse TRUNCATED_DATA");
            break;
    }
    WebPDataClear(&webp_data);
    LOGE(TO_STRING(PACKAGE_ROOT), "输入文件: %s", in_file);
    jclass jcls = (*env)->FindClass(env, JAVA_CLS_WEBP_HEADER);
    jmethodID construct = (*env)->GetMethodID(env, jcls, JAVA_METHOD_CONSTRUCTOR, "()V");
    jobject jinfo = (*env)->NewObject(env, jcls, construct);
    return jinfo;
}

#ifdef __cplusplus
}
#endif