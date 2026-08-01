cmake_minimum_required(VERSION 3.20)
project(av_native_sdk LANGUAGES CXX)

if(MSVC)
    add_compile_options(/W4 /WX)
else()
    add_compile_options(-Wall -Wextra -Wpedantic -Werror)
endif()

# MinGW executables must not resolve an arbitrary libstdc++ from the user's PATH.
# Static GCC runtimes make the host, tests and module self-contained and prevent
# entry-point failures when another product ships an older libstdc++-6.dll.
if(MINGW OR CMAKE_CXX_COMPILER MATCHES "mingw")
    add_link_options(-static -static-libgcc -static-libstdc++)
endif()

enable_testing()
add_subdirectory(example_module)

add_library(av_module_host STATIC host/av_module_host.cpp)
target_compile_features(av_module_host PUBLIC cxx_std_17)
target_include_directories(av_module_host PUBLIC ../../specification/plugin-abi-v1 host)
if(UNIX AND NOT CMAKE_CXX_COMPILER MATCHES "mingw")
    target_link_libraries(av_module_host PUBLIC dl)
endif()

add_executable(av_module_host_test host/host_test.cpp)
target_compile_features(av_module_host_test PRIVATE cxx_std_17)
target_link_libraries(av_module_host_test PRIVATE av_module_host)
add_test(NAME av_module_host_test COMMAND av_module_host_test $<TARGET_FILE:av_example_module>)
