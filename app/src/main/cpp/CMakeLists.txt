cmake_minimum_required(VERSION 3.22.1)
project("ds_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(lsplt REQUIRED CONFIG)

add_library(ds_native SHARED
    native_hooks.cpp
    property_hooks.cpp
    system_hooks.cpp
    dlopen_hook.cpp
    zygisk_entry.cpp
    companion.cpp
)

target_link_libraries(ds_native
    PRIVATE
    lsplt::lsplt
    log
)

target_compile_options(ds_native PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -ffunction-sections
    -fdata-sections
)

target_link_options(ds_native PRIVATE
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)
