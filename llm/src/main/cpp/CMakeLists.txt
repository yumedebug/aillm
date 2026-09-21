cmake_minimum_required(VERSION 3.22.1)

project(aillm_llama CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# llama.cpp is fetched at configure time and pinned to a release tag so the
# build is reproducible. Nothing is vendored into the repository.
# ---------------------------------------------------------------------------
include(FetchContent)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# libmtmd (multimodal: image/audio projectors) as a library only. Upstream
# supports this standalone configuration explicitly, and it skips the whole
# tools/ tree — building the CLI executables would waste CI time.
set(LLAMA_BUILD_MTMD     ON  CACHE BOOL "" FORCE)

FetchContent_Declare(
        llama
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG        v0.4.1
        GIT_SHALLOW    TRUE
)

FetchContent_MakeAvailable(llama)

# ---------------------------------------------------------------------------
# Our JNI bridge. Built as the single shared library the app loads.
# ---------------------------------------------------------------------------
add_library(aillm_llama SHARED llama_bridge.cpp)

target_include_directories(aillm_llama PRIVATE
        ${llama_SOURCE_DIR}/include
        ${llama_SOURCE_DIR}/ggml/include
        ${llama_SOURCE_DIR}/tools/mtmd
)

target_compile_options(aillm_llama PRIVATE -O3 -fexceptions -frtti)

target_link_libraries(aillm_llama PRIVATE
        llama
        mtmd
        ggml
        android
        log
)
