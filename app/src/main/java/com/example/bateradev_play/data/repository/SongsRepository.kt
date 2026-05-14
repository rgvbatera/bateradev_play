package com.example.bateradev_play.data.repository

import android.content.Context
import com.example.bateradev_play.data.api.AudioAnalysisService
import com.example.bateradev_play.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Repositório para gerenciar músicas processadas com stems
 */
class SongsRepository(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: SongsRepository? = null
        
        fun getInstance(context: Context): SongsRepository {
            return instance ?: synchronized(this) {
                instance ?: SongsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val json = Json { 
        prettyPrint = true 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val analysisService = AudioAnalysisService.getInstance(context)
    
    // Cache de músicas em memória
    private val _songs = MutableStateFlow<Map<String, StemmedSong>>(emptyMap())
    val songs: StateFlow<Map<String, StemmedSong>> = _songs.asStateFlow()
    
    private val _allSongsList = MutableStateFlow<List<StemmedSong>>(emptyList())
    val allSongsList: StateFlow<List<StemmedSong>> = _allSongsList.asStateFlow()
    
    init {
        // Carregar músicas ao inicializar
        loadAllSongs()
    }
    
    /**
     * Diretório de dados de músicas
     */
    private fun getSongsDir(): File {
        val dir = File(context.filesDir, "songs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Diretório de stems
     */
    private fun getStemsDir(): File {
        val dir = File(context.filesDir, "stems")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Carrega todas as músicas do armazenamento
     */
    fun loadAllSongs() {
        try {
            val songsDir = getSongsDir()
            val songsList = songsDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        json.decodeFromString<StemmedSong>(file.readText())
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            
            _songs.value = songsList.associateBy { it.id }
            _allSongsList.value = songsList
        } catch (e: Exception) {
            // Ignorar erros de carregamento
        }
    }
    
    /**
     * Obtém uma música pelo ID
     */
    fun getSong(id: String): StemmedSong? {
        return _songs.value[id]
    }
    
    /**
     * Salva uma música
     */
    suspend fun saveSong(song: StemmedSong) = withContext(Dispatchers.IO) {
        try {
            val file = File(getSongsDir(), "${song.id}.json")
            file.writeText(json.encodeToString(song))
            
            // Atualizar cache
            _songs.value = _songs.value + (song.id to song)
            _allSongsList.value = _songs.value.values.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            throw Exception("Erro ao salvar música: ${e.message}")
        }
    }
    
    /**
     * Cria uma nova música a partir de um arquivo de áudio
     */
    suspend fun createSongFromFile(
        audioFile: File,
        title: String,
        artist: String = "Desconhecido"
    ): StemmedSong {
        val songId = UUID.randomUUID().toString()
        
        val song = StemmedSong(
            id = songId,
            title = title,
            artist = artist,
            originalFilePath = audioFile.absolutePath,
            stems = mapOf(StemType.ORIGINAL to audioFile.absolutePath)
        )
        
        saveSong(song)
        return song
    }
    
    /**
     * Adiciona stems a uma música existente
     */
    suspend fun addStemsToSong(
        songId: String,
        stems: Map<StemType, String>
    ): StemmedSong? {
        val song = getSong(songId) ?: return null
        
        val updatedSong = song.copy(
            stems = song.stems + stems
        )
        
        saveSong(updatedSong)
        return updatedSong
    }
    
    /**
     * Adiciona dados de análise a uma música
     */
    suspend fun addAnalysisToSong(
        songId: String,
        analysis: AudioAnalysisResult
    ): StemmedSong? {
        val song = getSong(songId) ?: return null
        
        val updatedSong = song.copy(
            duration = analysis.duration,
            bpm = analysis.bpm.toInt(),
            key = analysis.key,
            sections = analysis.sections,
            chords = analysis.chords,
            beats = analysis.beats
        )
        
        saveSong(updatedSong)
        return updatedSong
    }
    
    /**
     * Deleta uma música e seus arquivos
     */
    suspend fun deleteSong(songId: String) = withContext(Dispatchers.IO) {
        try {
            val song = getSong(songId) ?: return@withContext
            
            // Deletar arquivos de stem
            song.stems.values.forEach { path ->
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    // Ignorar erros de deleção de arquivo
                }
            }
            
            // Deletar arquivo JSON
            File(getSongsDir(), "${songId}.json").delete()
            
            // Atualizar cache
            _songs.value = _songs.value - songId
            _allSongsList.value = _songs.value.values.sortedByDescending { it.createdAt }
            
        } catch (e: Exception) {
            throw Exception("Erro ao deletar música: ${e.message}")
        }
    }
    
    /**
     * Pesquisa músicas por título ou artista
     */
    fun searchSongs(query: String): List<StemmedSong> {
        if (query.isBlank()) return _allSongsList.value
        
        val lowerQuery = query.lowercase()
        return _allSongsList.value.filter { song ->
            song.title.lowercase().contains(lowerQuery) ||
            song.artist.lowercase().contains(lowerQuery)
        }
    }
    
    /**
     * Filtra músicas que têm stems processados
     */
    fun getSongsWithStems(): List<StemmedSong> {
        return _allSongsList.value.filter { song ->
            song.stems.size > 1 || song.stems.containsKey(StemType.NO_DRUMS)
        }
    }
    
    /**
     * Filtra músicas por tipo de stem disponível
     */
    fun getSongsWithStemType(stemType: StemType): List<StemmedSong> {
        return _allSongsList.value.filter { song ->
            song.stems.containsKey(stemType)
        }
    }
    
    /**
     * Obtém músicas recentes
     */
    fun getRecentSongs(limit: Int = 10): List<StemmedSong> {
        return _allSongsList.value.take(limit)
    }
    
    /**
     * Atualiza metadados de uma música
     */
    suspend fun updateSongMetadata(
        songId: String,
        title: String? = null,
        artist: String? = null,
        bpm: Int? = null,
        key: String? = null
    ): StemmedSong? {
        val song = getSong(songId) ?: return null
        
        val updatedSong = song.copy(
            title = title ?: song.title,
            artist = artist ?: song.artist,
            bpm = bpm ?: song.bpm,
            key = key ?: song.key
        )
        
        saveSong(updatedSong)
        return updatedSong
    }
    
    /**
     * Adiciona/atualiza loops personalizados de uma música
     * (salvos no DataStore separadamente)
     */
    suspend fun saveCustomLoops(songId: String, loops: List<LoopMarker>) {
        // Implementar persistência de loops customizados
        // Pode usar DataStore ou arquivo JSON separado
    }
    
    /**
     * Carrega loops personalizados de uma música
     */
    suspend fun getCustomLoops(songId: String): List<LoopMarker> {
        // Implementar carregamento de loops customizados
        return emptyList()
    }
    
    /**
     * Exporta dados de uma música para compartilhamento
     */
    suspend fun exportSongData(songId: String): String? {
        val song = getSong(songId) ?: return null
        return json.encodeToString(song)
    }
    
    /**
     * Importa dados de uma música
     */
    suspend fun importSongData(jsonData: String): StemmedSong? {
        return try {
            val song = json.decodeFromString<StemmedSong>(jsonData)
            // Gerar novo ID para evitar conflitos
            val newSong = song.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )
            saveSong(newSong)
            newSong
        } catch (e: Exception) {
            null
        }
    }
}
