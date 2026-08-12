package com.offlineinterpreter.lib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * llama.cpp JNI wrapper — loads local GGUF and runs HY-MT translation.
 * All native methods are thread-safe (protected by mutex in C++).
 */
object InferenceEngine {

    private val lock = Any()

    /** Load a GGUF file from the given absolute path. Thread-safe. */
    @Throws(Exception::class)
    suspend fun loadModel(path: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            nativeLoadModel(path)
        }
    }

    /** Run translation. Thread-safe. */
    suspend fun translate(text: String, srcLang: String, tgtLang: String): String =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                nativeTranslate(text, srcLang, tgtLang)
            }
        }

    val isModelLoaded: Boolean
        get() = synchronized(lock) { nativeIsModelLoaded() }

    fun unloadModel() = synchronized(lock) { nativeUnloadModel() }

    // ─── Native declarations ────────────────────────────────────────────────
    @JvmStatic
    private external fun nativeLoadModel(path: String): Boolean

    @JvmStatic
    private external fun nativeTranslate(text: String, srcLang: String, tgtLang: String): String

    @JvmStatic
    private external fun nativeIsModelLoaded(): Boolean

    @JvmStatic
    private external fun nativeUnloadModel(): Unit
}
