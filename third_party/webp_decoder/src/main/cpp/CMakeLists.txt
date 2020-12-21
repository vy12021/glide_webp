cmake_minimum_required(VERSION 3.6.0)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall")

#set(CMAKE_CXX_FLAGS_DEBUG "$ENV{CXXFLAGS} -O0 -Wall -g -lldb")
#set(CMAKE_CXX_FLAGS_RELEASE "${ENV{CXXFLAGS} -O3 -Wall")

#set(CMAKE_BUILD_TYPE Debug)

# enable_language(ASM_NASM)

add_definitions(-DHAVE_LOG)

set(CURRENT_DIR ${CMAKE_CURRENT_SOURCE_DIR})

# set(LIBRARY_OUTPUT_PATH ${CURRENT_DIR}/output/${ANDROID_ABI})

if (${ANDROID_ABI} STREQUAL armeabi-v7a)
    set(ARCH_DIR arch-arm)
elseif (${ANDROID_ABI} STREQUAL arm64-v8a)
    set(ARCH_DIR arch-arm64)
endif ()

include_directories(${ANDROID_NDK}/platforms/${ANDROID_PLATFORM}/${ARCH_DIR}/usr/include
                    ${ANDROID_NDK}/sysroot/usr/include
                    ${CURRENT_DIR}/libwebp
                    ${CURRENT_DIR}/libwebp/include/webp)

add_subdirectory(libwebp)

add_library(webpparser SHARED
    webp_parser.c
)

target_link_libraries(webpparser webp android jnigraphics log)