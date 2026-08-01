cmake_minimum_required(VERSION 3.20)
project(av_example_module LANGUAGES CXX)

add_library(av_example_module SHARED example_module.cpp)
target_compile_features(av_example_module PRIVATE cxx_std_17)
target_include_directories(av_example_module PRIVATE ../../../specification/plugin-abi-v1)
set_target_properties(av_example_module PROPERTIES PREFIX "")

add_executable(av_module_smoke_test smoke_test.cpp)
target_compile_features(av_module_smoke_test PRIVATE cxx_std_17)
target_include_directories(av_module_smoke_test PRIVATE ../../../specification/plugin-abi-v1)
target_link_libraries(av_module_smoke_test PRIVATE av_example_module)

enable_testing()
add_test(NAME av_module_smoke_test COMMAND av_module_smoke_test)
