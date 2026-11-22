cmake_minimum_required(VERSION 3.22.1)
project("term_pty" C)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -O2 -fvisibility=hidden")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# Native PTY library
add_library(pty SHARED
    pty.c
)

target_link_libraries(pty
    android
    log
)

# Test proot binary packaged as a shared library for Android 10+ targetSdk 35 execution verification
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_executable(proot_bin
        proot_test.c
    )

    set_target_properties(proot_bin PROPERTIES
        OUTPUT_NAME "proot"
        PREFIX "lib"
        SUFFIX ".so"
    )

    target_link_libraries(proot_bin
        log
    )
endif()
