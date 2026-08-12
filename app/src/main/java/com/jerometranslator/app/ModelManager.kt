package com.jerometranslator.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * Manages the local GGUF model file: import via SAF, copy into private storage,
 * and persist the selected model path.
 */
object ModelManager {
    private const val PREFS_NAME = "translator_prefs"
    private const val KEY_MODEL_PATH = "model_path"
    private const val MODELS_DIR = "models"
    private const val GGUF_EXT = ".gguf"

    fun modelsDir(context: Context): File =
        File(context.filesDir, MODELS_DIR).apply { mkdirs() }

    fun savedModelPath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_PATH, null)

    fun saveModelPath(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL_PATH, path).apply()
    }

    fun clearModelPath(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_MODEL_PATH).apply()
    }

    /**
     * Copy the picked document into app-private storage.
     * Returns the target file, or null on failure.
     */
    fun copyToStorage(context: Context, uri: Uri): File? {
        val name = queryDisplayName(context, uri) ?: "model-${System.currentTimeMillis()}"
        val safeName = if (name.lowercase().endsWith(GGUF_EXT)) name else "$name$GGUF_EXT"
        val target = File(modelsDir(context), safeName)
        if (target.exists()) return target
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { input.copyTo(it) }
                target
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val name = context.contentResolver
            .query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        return name?.takeIf { it.isNotBlank() }
    }
}
