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

JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, jniMethod, void)(JNIEnv *env, jclass clazz) {

}


JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, webpDemux, void)(JNIEnv *env, jclass clazz,
                                                                      jstring file) {
    const char *in_file;
    jboolean flag = JNI_TRUE;
    in_file = (*env)->GetStringUTFChars(env, file, &flag);
    int quiet = 0, show_diag = 0, show_summary = 0;
    int parse_bitstream = 0;
    WebPInfoStatus webp_info_status = WEBP_INFO_OK;
    WebPInfo webp_info;
    WebPData webp_data;
    WebPInfoInit(&webp_info);
    webp_info.quiet_ = quiet;
    webp_info.show_diagnosis_ = show_diag;
    webp_info.show_summary_ = show_summary;
    webp_info.parse_bitstream_ = parse_bitstream;
    if (in_file == NULL || !ReadFileToWebPData(in_file, &webp_data)) {
        webp_info_status = WEBP_INFO_INVALID_COMMAND;
        fprintf(stderr, "Failed to open input file %s.\n", in_file);
    }
    if (!webp_info.quiet_) printf("File: %s\n", in_file);
    webp_info_status = AnalyzeWebP(&webp_info, &webp_data);
    WebPDataClear(&webp_data);
    LOGE(PACKAGE_ROOT, "输入文件: %s", in_file);
}


JNI_STATIC_METHOD(PACKAGE_ROOT, StandardWebpDecoder, webpMux, void)(JNIEnv *env, jclass clazz) {

}

#ifdef __cplusplus
}
#endif