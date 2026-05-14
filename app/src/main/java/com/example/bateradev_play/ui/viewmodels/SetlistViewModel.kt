package com.example.bateradev_play.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bateradev_play.data.models.Setlist
import com.example.bateradev_play.data.models.SetlistItem
import com.example.bateradev_play.data.models.StemmedSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Estado da UI de Setlists
 */
data class SetlistUiState(
    val setlists: List<Setlist> = emptyList(),
    val selectedSetlist: Setlist? = null,
    val setlistItems: List<SetlistItemWithSong> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEditing: Boolean = false,
    val shareCode: String? = null,
    val showShareDialog: Boolean = false,
    val showAddSongDialog: Boolean = false,
    val availableSongs: List<StemmedSong> = emptyList()
)

/**
 * Item de setlist com dados da música
 */
data class SetlistItemWithSong(
    val item: SetlistItem,
    val song: StemmedSong?
)

/**
 * ViewModel para gerenciamento de Setlists
 */
class SetlistViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(SetlistUiState())
    val uiState: StateFlow<SetlistUiState> = _uiState.asStateFlow()
    
    private var context: Context? = null
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    
    // Repositório de músicas (simplificado - em produção seria injetado)
    private var songsRepository: MutableMap<String, StemmedSong> = mutableMapOf()
    
    fun initialize(context: Context) {
        this.context = context
        loadSetlists()
        loadAvailableSongs()
    }
    
    /**
     * Carrega todas as setlists do armazenamento
     */
    private fun loadSetlists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val setlistsDir = getSetlistsDir()
                val setlists = setlistsDir.listFiles()
                    ?.filter { it.extension == "json" }
                    ?.mapNotNull { file ->
                        try {
                            json.decodeFromString<Setlist>(file.readText())
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()
                
                _uiState.update { 
                    it.copy(
                        setlists = setlists.sortedByDescending { s -> s.updatedAt },
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "Erro ao carregar setlists: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * Carrega músicas disponíveis
     */
    private fun loadAvailableSongs() {
        viewModelScope.launch {
            try {
                val songsDir = getSongsDir()
                val songs = songsDir.listFiles()
                    ?.filter { it.extension == "json" }
                    ?.mapNotNull { file ->
                        try {
                            json.decodeFromString<StemmedSong>(file.readText())
                        } catch (e: Exception) {
                            null
                        }
                    } ?: emptyList()
                
                songs.forEach { song ->
                    songsRepository[song.id] = song
                }
                
                _uiState.update { it.copy(availableSongs = songs) }
            } catch (e: Exception) {
                // Ignorar erros de carregamento de músicas
            }
        }
    }
    
    /**
     * Cria uma nova setlist
     */
    fun createSetlist(name: String, description: String = "") {
        val setlist = Setlist(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdBy = "local_user"
        )
        
        saveSetlist(setlist)
        
        _uiState.update { 
            it.copy(
                setlists = listOf(setlist) + it.setlists,
                selectedSetlist = setlist
            )
        }
    }
    
    /**
     * Seleciona uma setlist para visualização/edição
     */
    fun selectSetlist(setlist: Setlist?) {
        _uiState.update { it.copy(selectedSetlist = setlist) }
        
        if (setlist != null) {
            loadSetlistItems(setlist)
        }
    }
    
    /**
     * Carrega os itens de uma setlist
     */
    private fun loadSetlistItems(setlist: Setlist) {
        viewModelScope.launch {
            val items = setlist.songIds.mapIndexed { index, songId ->
                SetlistItemWithSong(
                    item = SetlistItem(songId = songId, order = index),
                    song = songsRepository[songId]
                )
            }
            
            _uiState.update { it.copy(setlistItems = items) }
        }
    }
    
    /**
     * Adiciona uma música à setlist selecionada
     */
    fun addSongToSetlist(songId: String) {
        val setlist = _uiState.value.selectedSetlist ?: return
        
        if (setlist.songIds.contains(songId)) return
        
        val updatedSetlist = setlist.copy(
            songIds = setlist.songIds + songId,
            updatedAt = System.currentTimeMillis()
        )
        
        saveSetlist(updatedSetlist)
        
        _uiState.update { state ->
            state.copy(
                selectedSetlist = updatedSetlist,
                setlists = state.setlists.map { 
                    if (it.id == updatedSetlist.id) updatedSetlist else it 
                },
                setlistItems = state.setlistItems + SetlistItemWithSong(
                    item = SetlistItem(songId = songId, order = state.setlistItems.size),
                    song = songsRepository[songId]
                )
            )
        }
    }
    
    /**
     * Remove uma música da setlist
     */
    fun removeSongFromSetlist(songId: String) {
        val setlist = _uiState.value.selectedSetlist ?: return
        
        val updatedSetlist = setlist.copy(
            songIds = setlist.songIds.filter { it != songId },
            updatedAt = System.currentTimeMillis()
        )
        
        saveSetlist(updatedSetlist)
        
        _uiState.update { state ->
            state.copy(
                selectedSetlist = updatedSetlist,
                setlists = state.setlists.map { 
                    if (it.id == updatedSetlist.id) updatedSetlist else it 
                },
                setlistItems = state.setlistItems.filter { it.item.songId != songId }
            )
        }
    }
    
    /**
     * Reordena músicas na setlist
     */
    fun reorderSetlist(fromIndex: Int, toIndex: Int) {
        val setlist = _uiState.value.selectedSetlist ?: return
        
        val songIds = setlist.songIds.toMutableList()
        val item = songIds.removeAt(fromIndex)
        songIds.add(toIndex, item)
        
        val updatedSetlist = setlist.copy(
            songIds = songIds,
            updatedAt = System.currentTimeMillis()
        )
        
        saveSetlist(updatedSetlist)
        selectSetlist(updatedSetlist)
    }
    
    /**
     * Atualiza nome/descrição da setlist
     */
    fun updateSetlist(name: String, description: String) {
        val setlist = _uiState.value.selectedSetlist ?: return
        
        val updatedSetlist = setlist.copy(
            name = name,
            description = description,
            updatedAt = System.currentTimeMillis()
        )
        
        saveSetlist(updatedSetlist)
        
        _uiState.update { state ->
            state.copy(
                selectedSetlist = updatedSetlist,
                setlists = state.setlists.map { 
                    if (it.id == updatedSetlist.id) updatedSetlist else it 
                },
                isEditing = false
            )
        }
    }
    
    /**
     * Deleta uma setlist
     */
    fun deleteSetlist(setlist: Setlist) {
        viewModelScope.launch {
            try {
                val file = File(getSetlistsDir(), "${setlist.id}.json")
                file.delete()
                
                _uiState.update { state ->
                    state.copy(
                        setlists = state.setlists.filter { it.id != setlist.id },
                        selectedSetlist = if (state.selectedSetlist?.id == setlist.id) null else state.selectedSetlist
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao deletar setlist: ${e.message}") }
            }
        }
    }
    
    /**
     * Duplica uma setlist
     */
    fun duplicateSetlist(setlist: Setlist) {
        val newSetlist = setlist.copy(
            id = UUID.randomUUID().toString(),
            name = "${setlist.name} (Cópia)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isShared = false,
            shareCode = null
        )
        
        saveSetlist(newSetlist)
        
        _uiState.update { 
            it.copy(setlists = listOf(newSetlist) + it.setlists)
        }
    }
    
    /**
     * Gera código de compartilhamento
     */
    fun shareSetlist() {
        val setlist = _uiState.value.selectedSetlist ?: return
        
        // Gera código único de 8 caracteres
        val shareCode = UUID.randomUUID().toString().take(8).uppercase()
        
        val updatedSetlist = setlist.copy(
            isShared = true,
            shareCode = shareCode,
            updatedAt = System.currentTimeMillis()
        )
        
        saveSetlist(updatedSetlist)
        
        _uiState.update { state ->
            state.copy(
                selectedSetlist = updatedSetlist,
                setlists = state.setlists.map { 
                    if (it.id == updatedSetlist.id) updatedSetlist else it 
                },
                shareCode = shareCode,
                showShareDialog = true
            )
        }
    }
    
    /**
     * Importa setlist por código
     */
    fun importSetlistByCode(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Em produção, isso faria uma requisição ao servidor
            // Por enquanto, simula a importação
            try {
                // Simular delay de rede
                kotlinx.coroutines.delay(1000)
                
                // Em produção: buscar setlist do servidor pelo código
                _uiState.update { 
                    it.copy(
                        error = "Funcionalidade de importação requer conexão com servidor",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "Erro ao importar: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * Salva setlist no armazenamento
     */
    private fun saveSetlist(setlist: Setlist) {
        viewModelScope.launch {
            try {
                val file = File(getSetlistsDir(), "${setlist.id}.json")
                file.writeText(json.encodeToString(setlist))
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao salvar setlist: ${e.message}") }
            }
        }
    }
    
    /**
     * Obtém diretório de setlists
     */
    private fun getSetlistsDir(): File {
        val dir = File(context?.filesDir, "setlists")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Obtém diretório de músicas
     */
    private fun getSongsDir(): File {
        val dir = File(context?.filesDir, "songs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    // Controles de UI
    fun setEditing(editing: Boolean) {
        _uiState.update { it.copy(isEditing = editing) }
    }
    
    fun showAddSongDialog(show: Boolean) {
        _uiState.update { it.copy(showAddSongDialog = show) }
    }
    
    fun dismissShareDialog() {
        _uiState.update { it.copy(showShareDialog = false, shareCode = null) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
