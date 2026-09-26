cmake_minimum_required(VERSION 3.22.1)

project(aillm_diffusion CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# stable-diffusion.cpp is fetched at configure time and pinned to a commit so
# the build is reproducible. Nothing is vendored into the repository. Its
# ggml is a patched fork shipped as a git submodule, so only that submodule is
# pulled (libwebp/libwebm are disabled below and never needed).
# ---------------------------------------------------------------------------
include(FetchContent)

set(SD_BUILD_EXAMPLES       OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
set(SD_WEBP                 OFF CACHE BOOL "" FORCE)
set(SD_WEBM                 OFF CACHE BOOL "" FORCE)

# Only the CPU backend exists on Android here: no CUDA/Metal/Vulkan/OpenCL/SYCL.
set(SD_CUDA                 OFF CACHE BOOL "" FORCE)
set(SD_METAL                OFF CACHE BOOL "" FORCE)
set(SD_VULKAN               OFF CACHE BOOL "" FORCE)
set(SD_OPENCL               OFF CACHE BOOL "" FORCE)
set(SD_SYCL                 OFF CACHE BOOL "" FORCE)
set(SD_HIPBLAS              OFF CACHE BOOL "" FORCE)
set(SD_MUSA                 OFF CACHE BOOL "" FORCE)

set(BUILD_SHARED_LIBS       OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP             OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS        OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES     OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
        stable_diffusion
        GIT_REPOSITORY https://github.com/leejet/stable-diffusion.cpp.git
        GIT_TAG        2f886889e6e8b78738d6b87f7191f6018557c551
        # The patched ggml is a submodule; pull only it.
        GIT_SUBMODULES "ggml"
)

FetchContent_MakeAvailable(stable_diffusion)

# ---------------------------------------------------------------------------
# Our JNI bridge, built as the single shared library the app loads.
#
# `--exclude-libs,ALL` localises every symbol from the static archives
# (stable-diffusion and its ggml) so this library cannot collide at runtime
# with the *other* ggml that :llm brings in through llama.cpp.
# ---------------------------------------------------------------------------
add_library(aillm_diffusion SHARED sd_bridge.cpp)

target_include_directories(aillm_diffusion PRIVATE
        ${stable_diffusion_SOURCE_DIR}/include
)

target_compile_options(aillm_diffusion PRIVATE -O3 -fexceptions -frtti)

target_link_options(aillm_diffusion PRIVATE -Wl,--exclude-libs,ALL)

target_link_libraries(aillm_diffusion PRIVATE
        stable-diffusion
        ggml
        android
        log
)
