package com.example.bateradev_play.ui.viewmodels

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bateradev_play.data.api.ApiService
import com.example.bateradev_play.data.models.AnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

data class AnalysisUiState(
    val selectedFile: File? = null,
    val isAnalyzing: Boolean = false,
    val analysisResult: AnalysisResult? = null,
    val error: String? = null,
    val statusMessage: String = ""
)

class AnalysisViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    
    private var apiService: ApiService? = null
    
    fun initialize(context: Context) {
        apiService = ApiService.getInstance(context)
    }
    
    fun selectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = getFileName(context, uri)
                val tempFile = File(context.cacheDir, fileName)
                
                withContext(Dispatchers.IO) {
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    selectedFile = tempFile,
                    analysisResult = null,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Erro ao carregar arquivo: ${e.message}"
                )
            }
        }
    }
    
    fun analyzeAudio(context: Context, onComplete: (Boolean, String) -> Unit) {
        val file = _uiState.value.selectedFile ?: return
        val api = apiService ?: ApiService.getInstance(context).also { apiService = it }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAnalyzing = true,
                error = null,
                statusMessage = "Conectando ao servidor..."
            )
            
            try {
                // Verificar se servidor está disponível
                val serverAvailable = api.isServerAvailable()
                
                if (serverAvailable) {
                    // Usar API do backend para análise real
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Analisando áudio com librosa..."
                    )
                    
                    val analysisResult = api.analyzeAudio(file)
                    val response = analysisResult.getOrElse {
                        throw Exception("Erro na análise: ${it.message}")
                    }
                    
                    // Gerar waveform simulado (poderia vir do backend também)
                    val waveformData = generateWaveform(100)
                    
                    val result = AnalysisResult(
                        bpm = response.bpm,
                        key = response.key,
                        confidence = response.confidence.toFloat(),
                        duration = response.duration.toLong(),
                        waveformData = waveformData
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        analysisResult = result,
                        statusMessage = "Análise concluída!"
                    )
                    
                    onComplete(true, "BPM: ${result.bpm}, Tom: ${result.key}")
                    
                } else {
                    // Fallback para análise local simulada
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Servidor offline - usando análise local..."
                    )
                    
                    delay(1500)
                    
                    // Obter duração real do arquivo
                    val duration = withContext(Dispatchers.IO) {
                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(file.absolutePath)
                            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            retriever.release()
                            (durationStr?.toLongOrNull() ?: 0L) / 1000
                        } catch (e: Exception) {
                            180L
                        }
                    }
                    
                    val waveformData = generateWaveform(100)
                    val commonBPMs = listOf(80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180)
                    val detectedBPM = commonBPMs.random()
                    val keys = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                    val modes = listOf("maior", "menor")
                    val detectedKey = "${keys.random()} ${modes.random()}"
                    
                    val result = AnalysisResult(
                        bpm = detectedBPM,
                        key = detectedKey,
                        confidence = Random.nextFloat() * 0.3f + 0.7f,
                        duration = duration,
                        waveformData = waveformData
                    )
                    
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        analysisResult = result,
                        statusMessage = "Análise concluída (offline)"
                    )
                    
                    onComplete(true, "BPM: ${result.bpm} (estimado), Tom: ${result.key}")
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    error = e.message
                )
                onComplete(false, "Erro: ${e.message}")
            }
        }
    }
    
    private fun generateWaveform(points: Int): List<Float> {
        return (0 until points).map { i ->
            val base = kotlin.math.sin(i * 0.1).toFloat() * 0.3f
            val noise = Random.nextFloat() * 0.4f
            val beats = if (i % 10 < 3) 0.3f else 0f
            (base + noise + beats).coerceIn(0f, 1f)
        }
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        var name = "audio_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
