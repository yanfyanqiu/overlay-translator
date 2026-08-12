package com.offlineinterpreter.app.llm

import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import androidx.documentfile.provider.DocumentFile
import com.offlineinterpreter.lib.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages local GGUF model: user picks via SAF, copies to app-internal,
 * persists path, exposes load/unload.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val PREF_MODEL_URI   = "gguf_model_uri"
        private const val PREF_MODEL_NAME  = "gguf_model_name"
        private const val MODEL_DIR        = "models"
    }

    val modelDir: File get() = File(context.filesDir, MODEL_DIR).also { it.mkdirs() }

    val savedUri: Uri?
        get() {
            val uriStr = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_MODEL_URI, null) ?: return null
            return try { Uri.parse(uriStr) } catch (_: Exception) { null }
        }

    val savedName: String
        get() = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_MODEL_NAME, "") ?: ""

    val isModelLoaded: Boolean get() = InferenceEngine.isModelLoaded

    /** Copy from SAF uri to internal storage, return internal file path. */
    suspend fun importModel(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val doc    = DocumentFile.fromSingleUri(context, uri) ?: return@withContext null
            val name   = doc.name ?: "model.gguf"
            val dest   = File(modelDir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            // Persist
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_MODEL_URI,  uri.toString())
                .putString(PREF_MODEL_NAME, name)
                .apply()
            dest.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val path = savedUri?.let { uri ->
                // Re-copy from SAF (in case file moved / after reboot)
                val doc  = DocumentFile.fromSingleUri(context, uri) ?: return@withContext Result.failure(Exception("文件不存在"))
                val name = doc.name ?: "model.gguf"
                val dest = File(modelDir, name)
                if (dest.exists() && dest.length() == doc.length()) {
                    dest.absolutePath
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    dest.absolutePath
                }
            } ?: return@withContext Result.failure(Exception("未选择模型文件"))

            val ok = InferenceEngine.loadModel(path)
            if (ok) Result.success(Unit) else Result.failure(Exception("模型加载失败"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun unloadModel() = InferenceEngine.unloadModel()
}
