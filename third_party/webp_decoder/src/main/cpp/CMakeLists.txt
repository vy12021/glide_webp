################ CMAKE 预定义变量 #######################
# PROJECT_SOURCE_DIR cmake命令后紧跟的目录，一般是工程的根目录
# PROJECT_BINARY_DIR 执行cmake命令的目录,通常是${PROJECT_SOURCE_DIR}/build
# CMAKE_INCLUDE_PATH 系统环境变量,非cmake变量
# CMAKE_LIBRARY_PATH 系统环境变量,非cmake变量
# CMAKE_CURRENT_SOURCE_DIR 当前处理的CMakeLists.txt所在的路径
# CMAKE_CURRENT_BINARY_DIR target编译目录
# 使用ADD_SURDIRECTORY(src bin)可以更改此变量的值
# SET(EXECUTABLE_OUTPUT_PATH <新路径>)并不会对此变量有影响,只是改变了最终目标文件的存储路径
# CMAKE_CURRENT_LIST_FILE 输出调用这个变量的CMakeLists.txt的完整路径
# CMAKE_CURRENT_LIST_LINE 输出这个变量所在的行
# CMAKE_MODULE_PATH 定义自己的cmake模块所在的路径
# 这个变量用于定义自己的cmake模块所在的路径，如果你的工程比较复杂，有可能自己编写一些cmake模块，
# 比如SET(CMAKE_MODULE_PATH ${PROJECT_SOURCE_DIR}/cmake),然后可以用INCLUDE命令来调用自己的模块
# EXECUTABLE_OUTPUT_PATH 重新定义目标二进制可执行文件的存放位置
# LIBRARY_OUTPUT_PATH 重新定义目标链接库文件的存放位置
# PROJECT_NAME 返回通过PROJECT指令定义的项目名称
# CMAKE_ALLOW_LOOSE_LOOP_CONSTRUCTS 用来控制IF ELSE语句的书写方式

cmake_minimum_required(VERSION 3.6.0)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall")

# 不同编译模式下编译参数
#set(CMAKE_CXX_FLAGS_DEBUG "$ENV{CXXFLAGS} -O0 -Wall -g -lldb")
#set(CMAKE_CXX_FLAGS_RELEASE "${ENV{CXXFLAGS} -O3 -Wall")
# 编译类型 Debug, Release
#set(CMAKE_BUILD_TYPE Debug)


# enable_language(ASM_NASM)

set(CURRENT_DIR ${CMAKE_CURRENT_SOURCE_DIR})

# set(LIBRARY_OUTPUT_PATH ${CURRENT_DIR}/output/${ANDROID_ABI})

# 设置输出目录
# set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/Lib)
# set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${LIBRARY_OUTPUT_PATH})
# set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/Bin)

# 指定头文件搜索目录
if (${ANDROID_ABI} STREQUAL armeabi-v7a)
    set(ARCH_DIR arch-arm)
elseif (${ANDROID_ABI} STREQUAL arm64-v8a)
    set(ARCH_DIR arch-arm64)
endif ()
include_directories(${ANDROID_NDK}/platforms/${ANDROID_PLATFORM}/${ARCH_DIR}/usr/include
                    ${ANDROID_NDK}/sysroot/usr/include
                    ${CURRENT_DIR}
                    ${CURRENT_DIR}/libwebp)

# 搜索编译子目录CMakeLists.txt
add_subdirectory(libwebp)

add_library(webpjni SHARED jni_declare.c)

target_link_libraries(webpjni android jnigraphics log)