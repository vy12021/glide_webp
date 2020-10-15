project(libwebp C)

set(WEBP_DEP_LIBRARIES)
set(WEBP_DEP_INCLUDE_DIRS)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE "Release" CACHE
    "Build type: Release, Debug or RelWithDebInfo" STRING FORCE
  )
endif()

################################################################################
# Options.
if(WEBP_ENABLE_SWAP_16BIT_CSP)
  add_definitions(-DWEBP_SWAP_16BIT_CSP=1)
endif()

################################################################################
# cpu neon支持
include_directories(${ANDROID_NDK}/sources/android/cpufeatures)
add_library(cpufeatures STATIC
${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c)
target_link_libraries(cpufeatures dl)
set(WEBP_DEP_LIBRARIES ${WEBP_DEP_LIBRARIES} cpufeatures)
set(WEBP_DEP_INCLUDE_DIRS ${WEBP_DEP_INCLUDE_DIRS}
${ANDROID_NDK}/sources/android/cpufeatures
)
add_definitions(-DHAVE_CPU_FEATURES_H=1)
set(HAVE_CPU_FEATURES_H 1)

################################################################################
# WebP source files.
set(WEBP_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/src)

### Define the mandatory libraries.
# Build the webpdecoder library.
add_definitions(-Wall)
include_directories(${WEBP_SRC_DIR}
                    ${CMAKE_CURRENT_SOURCE_DIR}
                    ${WEBP_DEP_INCLUDE_DIRS})

#add_definitions(-DHAVE_CONFIG_H)

# 引入源文件定义
include(SourceFiles.txt)

# 解码
add_library(webpdecode OBJECT
            ${WEBP_DEC_SRCS})
add_library(webpdspdecode OBJECT
            ${WEBP_DSP_DEC_SRCS})
add_library(webputilsdecode OBJECT
            ${WEBP_UTILS_DEC_SRCS})
add_library(webpdecoder
            $<TARGET_OBJECTS:webpdecode>
            $<TARGET_OBJECTS:webpdspdecode>
            $<TARGET_OBJECTS:webputilsdecode>)
# 解码器链接 cpu 特性库
target_link_libraries(webpdecoder
            ${WEBP_DEP_LIBRARIES})

# 编码 start
add_library(webpdspencode OBJECT
            ${WEBP_DSP_ENC_SRCS})
add_library(webpencode OBJECT
            ${WEBP_ENC_SRCS})
add_library(webpencoder
            $<TARGET_OBJECTS:webpencode>
            $<TARGET_OBJECTS:webpdspencode>)
# 编码 链接 cpu 特性库
target_link_libraries(webpencoder ${WEBP_DEP_LIBRARIES})

add_library(webpmuxer OBJECT
            ${WEBP_MUX_SRCS}
            ${WEBP_DEMUX_SRCS})

# dsp 模式编码+解码
add_library(webpdsp OBJECT
            ${WEBP_DSP_DEC_SRCS}
            ${WEBP_DSP_ENC_SRCS})

# 工具库
add_library(webputils OBJECT
            ${WEBP_UTILS_DEC_SRCS}
            ${WEBP_UTILS_ENC_SRCS})

add_library(webpimageio OBJECT ${WEBP_IMAGEIO_SRCS})
add_library(webpextras OBJECT ${WEBP_EXTRA_SRCS})
add_library(webpexample OBJECT ${WEBP_EXAMPLE_SRCS})

# 聚合库
add_library(webp SHARED
            jni_runtime.c
            libwebp_java_wrap.c
            $<TARGET_OBJECTS:webpdecode>
            $<TARGET_OBJECTS:webpdsp>
            $<TARGET_OBJECTS:webpencode>
            $<TARGET_OBJECTS:webpmuxer>
            $<TARGET_OBJECTS:webputils>
            $<TARGET_OBJECTS:webpimageio>
            $<TARGET_OBJECTS:webpextras>
            $<TARGET_OBJECTS:webpexample>)

target_link_libraries(webp ${WEBP_DEP_LIBRARIES} android log)

set_target_properties(webpdecode
                      webpencode
                      webpdsp
                      webpmuxer
                      webputils
                      webpimageio
                      webpextras
                      webpexample
                      PROPERTIES POSITION_INDEPENDENT_CODE ON)