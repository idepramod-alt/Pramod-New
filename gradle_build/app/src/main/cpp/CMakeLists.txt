cmake_minimum_required(VERSION 3.22.1)
project(oboe_audio_engine CXX)

# Oboe delivered via Prefab from the oboe AAR (AGP downloads it automatically)
find_package(oboe REQUIRED CONFIG)

add_library(oboe_audio_engine SHARED audio_engine.cpp)

target_compile_options(oboe_audio_engine PRIVATE -std=c++17 -O2 -Wall)

target_link_libraries(oboe_audio_engine
    oboe::oboe
    log
    android)
