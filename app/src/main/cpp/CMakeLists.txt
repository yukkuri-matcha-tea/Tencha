cmake_minimum_required(VERSION 3.22.1)
project(tencha_call LANGUAGES CXX)

add_library(tencha_call SHARED tencha_call.cpp)
target_link_libraries(tencha_call PRIVATE android log dl)
