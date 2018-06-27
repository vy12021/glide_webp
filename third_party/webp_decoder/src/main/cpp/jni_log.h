//
// Created by Leo on 3/21/2018.
//

#ifndef GLIDE_PARENT_JNI_LOG_H
#define GLIDE_PARENT_JNI_LOG_H

#ifdef __ANDROID__
    #include <android/api-level.h>
    #if HAVE_LOG
        #include <android/log.h>
        #define HAVE_GL_LOG 0
        #define _LOGI(TAG, ...)  __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
        #define _LOGE(TAG, ...)  __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
        #define _LOGV(TAG, ...)  __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
        #define LOGI(TAG, ...)  _LOGI(TAG, __VA_ARGS__)
        #define LOGE(TAG, ...)  _LOGE(TAG, __VA_ARGS__)
        #define LOGV(TAG, ...)  _LOGV(TAG, __VA_ARGS__)
    #else
        #define HAVE_GL_LOG 0
        #define LOGI(TAG, ...)  do{}while(0)
        #define LOGE(TAG, ...)  do{}while(0)
        #define LOGV(TAG, ...)  do{}while(0)
    #endif
#else
    #if HAVE_LOG
		#define HAVE_GL_LOG 0
		#define LOGI(TAG, ...)  do{printf("%s: ", TAG); printf(__VA_ARGS__); printf("\n");}while(0)
		#define LOGV(TAG, ...)  do{printf("%s: ", TAG); printf(__VA_ARGS__); printf("\n");}while(0)
		#define LOGE(TAG, ...)  do{printf("%s: ", TAG); printf(__VA_ARGS__); printf("\n");}while(0)
	#else
		#define HAVE_GL_LOG 0
		#define LOGI(TAG, ...)  do{}while(0)
		#define LOGV(TAG, ...)  do{}while(0)
		#define LOGE(TAG, ...)  do{}while(0)
	#endif
#endif

#endif //GLIDE_PARENT_JNI_LOG_H
