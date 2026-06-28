cmake_minimum_required(VERSION 3.22.1)
project("ds_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(lsplt REQUIRED CONFIG)
find_package(lsplant REQUIRED CONFIG)

# ---- Dobby (inline hook backend for LSPlant) ----
# Fetched at configure time so the workspace stays clean and CI pulls it during
# the build. Example/test targets are disabled; Dobby builds as a static lib so
# it links into ds_native without an extra .so.
include(FetchContent)
set(DOBBY_BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(DOBBY_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    dobby
    GIT_REPOSITORY https://github.com/jmpews/Dobby.git
    GIT_TAG 9963b83302bd5d7a09d6217492c14edf32a7c6da
)
FetchContent_MakeAvailable(dobby)

add_library(ds_native SHARED
    native_hooks.cpp
    property_hooks.cpp
    system_hooks.cpp
    dlopen_hook.cpp
    zygisk_entry.cpp
    companion.cpp
    lsplant_init.cpp
    java_hooks.cpp
)

target_link_libraries(ds_native
    PRIVATE
    lsplt::lsplt
    lsplant::lsplant
    dobby
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
