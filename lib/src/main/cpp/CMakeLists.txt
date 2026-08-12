cmake_minimum_required(VERSION 3.22)

project(offlineinterpreter-lib)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── Android / NDK defaults ───────────────────────────────────────────────────
include_directories(${ANDROID_NDK}/sources/cxx-stl/llvm-libc++/include)

# ── llama.cpp ────────────────────────────────────────────────────────────────
# LLAMA_SRC can be overridden from the environment / -DLLAMA_SRC=
if(NOT DEFINED LLAMA_SRC)
    set(LLAMA_SRC "$ENV{LLAMA_SRC}")
endif()
if(NOT LLAMA_SRC)
    # fallback: sibling directory llama.cpp
    get_filename_component(PARENT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../.. ABSOLUTE)
    set(LLAMA_SRC "${PARENT_DIR}/llama.cpp")
endif()

if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp source not found at ${LLAMA_SRC}. "
        "Set LLAMA_SRC environment variable or clone it:\n"
        "  git clone https://github.com/ggerganov/llama.cpp ${LLAMA_SRC}")
endif()

add_subdirectory(${LLAMA_SRC} llama.cpp.build EXCLUDE_FROM_ALL)

# ── Inference wrapper (our JNI layer) ────────────────────────────────────────
add_library(offlineinterpreter SHARED
    inference_jni.cpp
    logging.h)

target_link_libraries(offlineinterpreter
    PRIVATE
        llama
        log
        android
)
