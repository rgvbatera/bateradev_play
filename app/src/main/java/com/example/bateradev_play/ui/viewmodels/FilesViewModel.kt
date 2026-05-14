package com.example.bateradev_play.ui.viewmodels

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FilesUiState(
    val files: List<File> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FilesViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()
    
    private val baseDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "BateraDevPlay"
        )
    
    fun loadFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val files = withContext(Dispatchers.IO) {
                val allFiles = mutableListOf<File>()
                
                // Diretório de downloads
                val downloadsDir = File(baseDir, "Downloads")
                if (downloadsDir.exists()) {
                    downloadsDir.listFiles()?.filter { it.isFile && isAudioFile(it) }?.let {
                        allFiles.addAll(it)
                    }
                }
                
                // Diretório sem bateria
                val noDrumsDir = File(baseDir, "NoDrums")
                if (noDrumsDir.exists()) {
                    noDrumsDir.listFiles()?.filter { it.isFile && isAudioFile(it) }?.let {
                        allFiles.addAll(it)
                    }
                }
                
                // Ordenar por data de modificação (mais recente primeiro)
                allFiles.sortedByDescending { it.lastModified() }
            }
            
            _uiState.value = _uiState.value.copy(
                files = files,
                isLoading = false
            )
        }
    }
    
    fun deleteFile(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                file.delete()
            }
            loadFiles() // Recarregar lista
        }
    }
    
    private fun isAudioFile(file: File): Boolean {
        val audioExtensions = listOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "wma")
        return file.extension.lowercase() in audioExtensions
    }
}
