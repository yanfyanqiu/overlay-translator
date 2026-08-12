package com.jerometranslator.app

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var sourceLangTv: TextView
    private lateinit var targetLangTv: TextView
    private lateinit var swapBtn: ImageButton
    private lateinit var inputEt: EditText
    private lateinit var translateBtn: Button
    private lateinit var outputTv: TextView
    private lateinit var speakBtn: Button
    private lateinit var autoSpeakSw: Switch
    private lateinit var importBtn: Button
    private lateinit var modelStatusTv: TextView

    private lateinit var engine: InferenceEngine
    private var translateJob: Job? = null
    private var isModelReady = false
    private var sourceIsZh = true
    private var tts: TtsHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Default direction follows the system language
        sourceIsZh = Locale.getDefault().language == "zh"
        updateLanguageLabels()

        bindViews()
        setupTts()

        lifecycleScope.launch(Dispatchers.Default) {
            engine = AiChat.getInferenceEngine(applicationContext)

            // Restore previously imported model (after the engine is initialized)
            val savedPath = ModelManager.savedModelPath(this@MainActivity)
            if (savedPath != null && File(savedPath).exists()) {
                withContext(Dispatchers.Main) { loadModel(savedPath) }
            } else {
                withContext(Dispatchers.Main) { modelStatusTv.setText(R.string.no_model) }
            }
        }
    }

    private fun bindViews() {
        sourceLangTv = findViewById(R.id.source_lang)
        targetLangTv = findViewById(R.id.target_lang)
        swapBtn = findViewById(R.id.swap_btn)
        inputEt = findViewById(R.id.input_et)
        translateBtn = findViewById(R.id.translate_btn)
        outputTv = findViewById(R.id.output_tv)
        speakBtn = findViewById(R.id.speak_btn)
        autoSpeakSw = findViewById(R.id.auto_speak_sw)
        importBtn = findViewById(R.id.import_btn)
        modelStatusTv = findViewById(R.id.model_status)

        swapBtn.setOnClickListener {
            sourceIsZh = !sourceIsZh
            updateLanguageLabels()
            outputTv.text = ""
        }

        translateBtn.setOnClickListener { translate() }
        speakBtn.setOnClickListener {
            val t = tts
            if (t == null || !t.ready) {
                Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            t.speak(outputTv.text.toString(), targetLocale())
        }

        importBtn.setOnClickListener { openDocument.launch(arrayOf("*/*")) }
    }

    private fun setupTts() {
        tts = TtsHelper(this) { ready ->
            runOnUiThread {
                speakBtn.isEnabled = ready
            }
        }
    }

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importModel(it) }
    }

    /**
     * Copy the picked GGUF file into app storage and load it.
     */
    private fun importModel(uri: Uri) {
        modelStatusTv.setText(R.string.importing_model)
        importBtn.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val file = ModelManager.copyToStorage(this@MainActivity, uri)
            if (file == null) {
                withContext(Dispatchers.Main) {
                    modelStatusTv.setText(R.string.import_failed)
                    importBtn.isEnabled = true
                }
                return@launch
            }
            ModelManager.saveModelPath(this@MainActivity, file.path)
            withContext(Dispatchers.Main) {
                importBtn.isEnabled = true
                loadModel(file.path)
            }
        }
    }

    /**
     * Load the model into the inference engine and set the translation system prompt.
     */
    private fun loadModel(path: String) {
        modelStatusTv.setText(R.string.model_loading)
        lifecycleScope.launch {
            try {
                engine.loadModel(path)
                engine.setSystemPrompt(translationPrompt())
                isModelReady = true
                modelStatusTv.text = getString(R.string.model_ready, File(path).name)
            } catch (e: Exception) {
                isModelReady = false
                modelStatusTv.setText(R.string.model_load_failed)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.model_load_failed) + ": ${e.message ?: ""}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun translate() {
        val text = inputEt.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.empty_input, Toast.LENGTH_SHORT).show()
            return
        }
        if (!isModelReady) {
            Toast.makeText(this, R.string.model_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        translateBtn.isEnabled = false
        outputTv.text = ""
        translateJob?.cancel()
        translateJob = lifecycleScope.launch {
            engine.sendUserPrompt(text, MAX_PREDICT_LENGTH)
                .onCompletion {
                    withContext(Dispatchers.Main) {
                        translateBtn.isEnabled = true
                        val result = outputTv.text.toString()
                        if (autoSpeakSw.isChecked && result.isNotBlank()) {
                            tts?.speak(result, targetLocale())
                        }
                    }
                }
                .collect { token ->
                    withContext(Dispatchers.Main) {
                        outputTv.append(token)
                    }
                }
        }
    }

    private fun updateLanguageLabels() {
        sourceLangTv.setText(if (sourceIsZh) R.string.lang_chinese else R.string.lang_english)
        targetLangTv.setText(if (sourceIsZh) R.string.lang_english else R.string.lang_chinese)
    }

    private fun targetLocale(): Locale =
        if (sourceIsZh) Locale.ENGLISH else Locale.CHINESE

    private fun translationPrompt(): String =
        if (sourceIsZh) {
            "You are a professional translator. Translate the user's text from Chinese to English. " +
                "Output only the translated text, without any explanation, quotes, or extra content."
        } else {
            "You are a professional translator. Translate the user's text from English to Chinese. " +
                "Output only the translated text, without any explanation, quotes, or extra content."
        }

    override fun onStop() {
        translateJob?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        translateJob?.cancel()
        tts?.shutdown()
        if (::engine.isInitialized) {
            engine.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val MAX_PREDICT_LENGTH = 512
    }
}
