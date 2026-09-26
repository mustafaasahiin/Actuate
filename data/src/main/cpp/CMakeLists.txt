cmake_minimum_required(VERSION 3.22.1)
project("whisper-actuate" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

# Enforce Android 15 16KB page-size alignment
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# Directories
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

# Subproject options for GGML
set(GGML_STANDALONE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_DIR}/ggml ${CMAKE_CURRENT_BINARY_DIR}/ggml EXCLUDE_FROM_ALL)

find_library(LOG_LIB log)
find_library(ANDROID_LIB android)

set(WHISPER_SOURCES
    ${WHISPER_DIR}/src/whisper.cpp
)

if (EXISTS ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.cpp)
    list(APPEND WHISPER_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/whisper_jni.cpp)
endif()

add_library(whisper SHARED ${WHISPER_SOURCES})

target_include_directories(whisper PUBLIC
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
    ${WHISPER_DIR}/ggml/src
    ${WHISPER_DIR}/ggml/src/ggml-cpu
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.7.4")

if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden)
    target_link_options(whisper PRIVATE -Wl,--gc-sections -flto)
endif()

# 16KB page size alignment for Android 15
target_link_options(whisper PRIVATE -Wl,-z,max-page-size=16384)

target_link_libraries(
    whisper
    PRIVATE
    ggml
    ${LOG_LIB}
    ${ANDROID_LIB}
)
