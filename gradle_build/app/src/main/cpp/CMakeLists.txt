cmake_minimum_required(VERSION 3.22.1)
project(oboe_audio_engine CXX)

# Oboe delivered via Prefab from the oboe AAR (AGP downloads it automatically)
find_package(oboe REQUIRED CONFIG)

# Include both C++ engine and Sonic C library (for pitch-preserving speed control)
add_library(oboe_audio_engine SHARED audio_engine.cpp sonic.c)

# sonic.c is plain C, audio_engine.cpp is C++17
set_source_files_properties(sonic.c PROPERTIES LANGUAGE C)
target_compile_options(oboe_audio_engine PRIVATE
    $<$<COMPILE_LANGUAGE:CXX>:-std=c++17 -O2 -Wall>
    $<$<COMPILE_LANGUAGE:C>:-O2 -Wall>)

target_link_libraries(oboe_audio_engine
    oboe::oboe
    log
    android)
