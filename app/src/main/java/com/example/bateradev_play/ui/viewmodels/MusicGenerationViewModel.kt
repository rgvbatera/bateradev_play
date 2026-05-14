package com.example.bateradev_play.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bateradev_play.data.api.MusicGenerationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 🎵 ViewModel para Geração de Música com IA
 */
class MusicGenerationViewModel : ViewModel() {
    
    private lateinit var musicService: MusicGenerationService
    
    // Estado da UI
    private val _uiState = MutableStateFlow(MusicGenerationUiState())
    val uiState: StateFlow<MusicGenerationUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        musicService = MusicGenerationService.getInstance(context)
    }
    
    /**
     * Atualiza o prompt de geração
     */
    fun updatePrompt(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt)
    }
    
    /**
     * Atualiza o gênero musical
     */
    fun updateGenre(genre: String) {
        _uiState.value = _uiState.value.copy(genre = genre)
    }
    
    /**
     * Atualiza o BPM
     */
    fun updateBpm(bpm: Int) {
        _uiState.value = _uiState.value.copy(bpm = bpm)
    }
    
    /**
     * Atualiza a duração
     */
    fun updateDuration(duration: Int) {
        _uiState.value = _uiState.value.copy(duration = duration)
    }
    
    /**
     * Atualiza a tonalidade
     */
    fun updateKey(key: String) {
        _uiState.value = _uiState.value.copy(key = key)
    }
    
    /**
     * Atualiza modo (prompt livre ou backing track)
     */
    fun updateMode(mode: GenerationMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }
    
    /**
     * 🆕 Gera música a partir do prompt
     */
    fun generateMusic(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                error = null,
                generatedUrl = null
            )
            
            val result = if (_uiState.value.mode == GenerationMode.BACKING_TRACK) {
                // Modo backing track
                musicService.generateBackingTrack(
                    genre = _uiState.value.genre,
                    bpm = _uiState.value.bpm,
                    key = _uiState.value.key.ifEmpty { null },
                    duration = _uiState.value.duration,
                    excludeDrums = true
                )
            } else {
                // Modo prompt livre
                musicService.generateMusic(
                    prompt = _uiState.value.prompt,
                    duration = _uiState.value.duration
                )
            }
            
            result.fold(
                onSuccess = { url ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generatedUrl = url,
                        error = null
                    )
                    onComplete(true, "Música gerada com sucesso!")
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = error.message
                    )
                    onComplete(false, error.message ?: "Erro ao gerar música")
                }
            )
        }
    }
    
    /**
     * 🆕 Verifica saúde do servidor
     */
    fun checkServerHealth(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = musicService.checkHealth()
            onResult(result.isSuccess)
        }
    }
    
    /**
     * Limpa o estado
     */
    fun clearState() {
        _uiState.value = MusicGenerationUiState()
    }
}

/**
 * Estado da UI de geração de música
 */
data class MusicGenerationUiState(
    val mode: GenerationMode = GenerationMode.BACKING_TRACK,
    val prompt: String = "",
    val genre: String = "rock",
    val bpm: Int = 120,
    val key: String = "",
    val duration: Int = 30,
    val isGenerating: Boolean = false,
    val generatedUrl: String? = null,
    val error: String? = null
)

/**
 * Modos de geração
 */
enum class GenerationMode {
    FREE_PROMPT,    // Prompt livre
    BACKING_TRACK   // Backing track para bateristas
}

/**
 * Gêneros musicais suportados
 */
object MusicGenres {
    val ALL = listOf(
        "rock" to "🎸 Rock",
        "jazz" to "🎷 Jazz",
        "funk" to "🕺 Funk",
        "metal" to "🤘 Metal",
        "bossa" to "🌴 Bossa Nova",
        "blues" to "🎺 Blues",
        "pop" to "🎵 Pop",
        "latin" to "💃 Latin",
        "electronic" to "⚡ Eletrônica",
        "acoustic" to "🎻 Acústico"
    )
    
    val KEYS = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )
}
