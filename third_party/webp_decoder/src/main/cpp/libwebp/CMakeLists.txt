cmake_minimum_required(VERSION 2.8.7)

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
# Android only.
if(ANDROID)
  include_directories(${ANDROID_NDK}/sources/android/cpufeatures)
  add_library(cpufeatures STATIC
    ${ANDROID_NDK}/sources/android/cpufeatures/cpu-features.c
  )
  target_link_libraries(cpufeatures dl)
  set(WEBP_DEP_LIBRARIES ${WEBP_DEP_LIBRARIES} cpufeatures)
  set(WEBP_DEP_INCLUDE_DIRS ${WEBP_DEP_INCLUDE_DIRS}
    ${ANDROID_NDK}/sources/android/cpufeatures
  )
  add_definitions(-DHAVE_CPU_FEATURES_H=1)
  set(HAVE_CPU_FEATURES_H 1)
else()
  set(HAVE_CPU_FEATURES_H 0)
endif()

################################################################################
# WebP source files.

# We expect the Makefiles to define the sources as defined in
# the first regex. E.g.:
# libimagedec_la_SOURCES  = image_dec.c image_dec.h

set(WEBP_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/src)

### Define the mandatory libraries.
# Build the webpdecoder library.
add_definitions(-Wall)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}
                    ${WEBP_DEP_INCLUDE_DIRS})
add_library(webpdecode OBJECT
            ${WEBP_DEC_SRCS})
add_library(webpdspdecode OBJECT
            ${WEBP_DSP_COMMON_SRCS}
            ${WEBP_DSP_DEC_SRCS})
add_library(webputilsdecode OBJECT
            ${WEBP_UTILS_COMMON_SRCS}
            ${WEBP_UTILS_DEC_SRCS})
add_library(webpdecoder
            $<TARGET_OBJECTS:webpdecode>
            $<TARGET_OBJECTS:webpdspdecode>
            $<TARGET_OBJECTS:webputilsdecode>)
target_link_libraries(webpdecoder
            ${WEBP_DEP_LIBRARIES})

# Build the webp library.
add_library(webpencode OBJECT
            ${WEBP_ENC_SRCS})
add_library(webpdsp OBJECT
            ${WEBP_DSP_COMMON_SRCS}
            ${WEBP_DSP_DEC_SRCS}
            ${WEBP_DSP_ENC_SRCS})
add_library(webputils OBJECT
            ${WEBP_UTILS_COMMON_SRCS}
            ${WEBP_UTILS_DEC_SRCS}
            ${WEBP_UTILS_ENC_SRCS})
add_library(webp
            $<TARGET_OBJECTS:webpdecode>
            $<TARGET_OBJECTS:webpdsp>
            $<TARGET_OBJECTS:webpencode>
            $<TARGET_OBJECTS:webputils>)
target_link_libraries(webp ${WEBP_DEP_LIBRARIES})

set_target_properties(webpdecode webpdspdecode webputilsdecode
  webpencode webpdsp webputils PROPERTIES POSITION_INDEPENDENT_CODE ON)