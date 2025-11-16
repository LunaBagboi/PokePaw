
cmake_minimum_required(VERSION 3.22.1)

set(CMAKE_CXX_STANDARD 23)

set(CMAKE_BUILD_TYPE Release)

set(CMAKE_CXX_FLAGS_RELEASE "-O3 -ffast-math -DNDEBUG -flto")
set(CMAKE_C_FLAGS_RELEASE "-O3 -ffast-math -DNDEBUG -flto")

project("pocketwalkerlib")

file(GLOB_RECURSE POCKETWALKER_SRC
        "PocketWalker/*.cpp"
        "PocketWalker/*.h"
)

add_library(${CMAKE_PROJECT_NAME} SHARED
        pocketwalkerlib.cpp ${POCKETWALKER_SRC})

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)