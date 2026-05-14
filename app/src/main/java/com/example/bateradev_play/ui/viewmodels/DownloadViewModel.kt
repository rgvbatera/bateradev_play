package com.example.bateradev_play.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bateradev_play.data.models.VideoInfo
import com.example.bateradev_play.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DownloadUiState(
    val url: String = "",
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val videoInfo: VideoInfo? = null,
    val error: String? = null,
    val downloadComplete: Boolean = false,
    val downloadedFile: File? = null
)

class DownloadViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()
    
    private var repository: YouTubeRepository? = null
    
    fun initialize(context: Context) {
        if (repository == null) {
            repository = YouTubeRepository(context.applicationContext)
        }
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(statusMessage = "Inicializando...")
                repository?.initialize()
                _uiState.value = _uiState.value.copy(statusMessage = "")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Erro ao inicializar: ${e.message}")
            }
        }
    }
    
    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(
            url = url,
            error = null,
            videoInfo = null,
            downloadComplete = false,
            downloadedFile = null
        )
    }
    
    fun fetchVideoInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(statusMessage = "Buscando informações...")
            
            val info = repository?.getVideoInfo(_uiState.value.url)
            
            if (info != null) {
                _uiState.value = _uiState.value.copy(
                    videoInfo = info,
                    statusMessage = "",
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "",
                    error = "Não foi possível obter informações do vídeo"
                )
            }
        }
    }
    
    fun downloadAudio(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                progress = 0f,
                error = null,
                downloadComplete = false,
                downloadedFile = null
            )
            
            val result = repository?.downloadAudio(_uiState.value.url) { progress, status ->
                _uiState.value = _uiState.value.copy(
                    progress = progress,
                    statusMessage = status
                )
            }
            
            result?.fold(
                onSuccess = { file ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        progress = 1f,
                        statusMessage = "Download concluído!",
                        downloadComplete = true,
                        downloadedFile = file
                    )
                    onComplete(true, "Download concluído: ${file.name}")
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        error = e.message,
                        downloadComplete = false
                    )
                    onComplete(false, "Erro: ${e.message}")
                }
            ) ?: run {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    error = "Repository não inicializado"
                )
                onComplete(false, "Erro: Repository não inicializado")
            }
        }
    }
    
    /**
     * Retorna o último arquivo baixado para processar stems
     */
    fun getDownloadedFile(): File? {
        return _uiState.value.downloadedFile ?: repository?.getLastDownloadedFile()
    }
    
    /**
     * Limpa o estado de download completo
     */
    fun clearDownloadState() {
        _uiState.value = _uiState.value.copy(
            downloadComplete = false,
            downloadedFile = null
        )
    }
}
