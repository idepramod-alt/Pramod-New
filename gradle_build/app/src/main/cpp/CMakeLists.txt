cmake_minimum_required(VERSION 3.22.1)
project(oboe_audio_engine CXX)

add_library(oboe_audio_engine SHARED audio_engine.cpp)

target_compile_options(oboe_audio_engine PRIVATE -std=c++17 -O2 -Wall)

target_link_libraries(oboe_audio_engine
    OpenSLES
    log
    android)
