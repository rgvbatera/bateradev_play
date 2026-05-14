package com.example.bateradev_play.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bateradev_play.audio.MetronomeEngine
import com.example.bateradev_play.audio.MultiStemAudioEngine
import com.example.bateradev_play.data.models.*
import com.example.bateradev_play.data.repository.SongsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Estado da UI da tela de prática
 */
data class PracticeUiState(
    // Música carregada
    val currentSong: StemmedSong? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    
    // Player
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val pitchSemitones: Int = 0,
    
    // Stems
    val loadedStems: Set<StemType> = emptySet(),
    val stemVolumes: Map<StemType, Float> = StemType.values().associateWith { 1f },
    val stemMuted: Map<StemType, Boolean> = StemType.values().associateWith { false },
    val soloStem: StemType? = null,
    val activePreset: BackingTrackPreset? = null,
    
    // Loops
    val loops: List<LoopMarker> = emptyList(),
    val activeLoop: LoopMarker? = null,
    val loopEnabled: Boolean = false,
    val isSettingLoopStart: Boolean = false,
    val tempLoopStartMs: Long? = null,
    
    // Seções
    val sections: List<SongSection> = emptyList(),
    val selectedSection: SongSection? = null,
    
    // Metrônomo
    val metronomeSettings: MetronomeSettings = MetronomeSettings(),
    val isMetronomeRunning: Boolean = false,
    val currentBeat: Int = 0,
    val currentBar: Int = 0,
    val countInRemaining: Int = 0,
    val isInCountIn: Boolean = false,
    
    // Acordes
    val chords: List<ChordMarker> = emptyList(),
    val currentChord: ChordMarker? = null,
    
    // Análise
    val detectedBpm: Int? = null,
    val detectedKey: String? = null,
    
    // Waveform
    val waveformData: List<Float> = emptyList()
) {
    /**
     * Verifica se a música atual tem stems processados (DRUMS e NO_DRUMS)
     * Necessário para os presets de backing track funcionarem
     */
    val hasProcessedStems: Boolean
        get() = currentSong?.stems?.let { stems ->
            stems.containsKey(StemType.DRUMS) && stems.containsKey(StemType.NO_DRUMS)
        } ?: false
}

/**
 * ViewModel para a tela de prática musical
 */
class PracticeViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(PracticeUiState())
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()
    
    private var audioEngine: MultiStemAudioEngine? = null
    private var metronomeEngine: MetronomeEngine? = null
    private var songsRepository: SongsRepository? = null
    
    private var isInitialized = false
    
    /**
     * Inicializa os engines de áudio
     */
    fun initialize(context: Context, songId: String? = null) {
        if (isInitialized) return
        
        songsRepository = SongsRepository.getInstance(context)
        
        audioEngine = MultiStemAudioEngine(context).apply {
            onPositionChanged = { position ->
                _uiState.update { it.copy(currentPositionMs = position) }
                updateCurrentChord(position)
            }
            onPlaybackStateChanged = { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
            onStemLoaded = { stemType, success ->
                if (success) {
                    _uiState.update { 
                        it.copy(loadedStems = it.loadedStems + stemType) 
                    }
                }
            }
        }
        
        metronomeEngine = MetronomeEngine(context).apply {
            onBeat = { beat, bar, isAccent ->
                _uiState.update { 
                    it.copy(currentBeat = beat, currentBar = bar) 
                }
            }
            onCountInBeat = { remaining ->
                _uiState.update { 
                    it.copy(countInRemaining = remaining) 
                }
            }
            onCountInComplete = {
                _uiState.update { 
                    it.copy(isInCountIn = false, countInRemaining = 0) 
                }
                // Iniciar reprodução após count-in
                audioEngine?.play()
            }
        }
        
        // Observar estados do metrônomo
        viewModelScope.launch {
            metronomeEngine?.isRunning?.collect { running ->
                _uiState.update { it.copy(isMetronomeRunning = running) }
            }
        }
        
        viewModelScope.launch {
            metronomeEngine?.isInCountIn?.collect { inCountIn ->
                _uiState.update { it.copy(isInCountIn = inCountIn) }
            }
        }
        
        viewModelScope.launch {
            audioEngine?.currentPosition?.collect { position ->
                _uiState.update { it.copy(currentPositionMs = position) }
            }
        }
        
        viewModelScope.launch {
            audioEngine?.duration?.collect { duration ->
                _uiState.update { it.copy(durationMs = duration) }
            }
        }
        
        isInitialized = true
        
        // Carregar música se songId foi fornecido
        songId?.let { id ->
            loadSongById(id)
        }
    }
    
    /**
     * Carrega uma música pelo ID
     */
    fun loadSongById(songId: String) {
        val song = songsRepository?.getSong(songId)
        if (song != null) {
            loadSong(song)
        } else {
            _uiState.update { 
                it.copy(error = "Música não encontrada") 
            }
        }
    }
    
    /**
     * Retorna a lista de músicas disponíveis
     */
    fun getAvailableSongs(): List<StemmedSong> {
        return songsRepository?.allSongsList?.value ?: emptyList()
    }
    
    /**
     * Retorna lista de arquivos de áudio baixados pelo app
     */
    fun getDownloadedFiles(): List<File> {
        val baseDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC
            ),
            "BateraDevPlay"
        )
        
        val allFiles = mutableListOf<File>()
        val audioExtensions = listOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "wma")
        
        // Downloads
        val downloadsDir = File(baseDir, "Downloads")
        if (downloadsDir.exists()) {
            downloadsDir.listFiles()?.filter { 
                it.isFile && it.extension.lowercase() in audioExtensions 
            }?.let { allFiles.addAll(it) }
        }
        
        // NoDrums (processados)
        val noDrumsDir = File(baseDir, "NoDrums")
        if (noDrumsDir.exists()) {
            noDrumsDir.listFiles()?.filter { 
                it.isFile && it.extension.lowercase() in audioExtensions 
            }?.let { allFiles.addAll(it) }
        }
        
        return allFiles.sortedByDescending { it.lastModified() }
    }
    
    /**
     * Carrega um arquivo de áudio diretamente (sem stems)
     */
    fun loadAudioFile(context: Context, file: File) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                // Criar um StemmedSong temporário com apenas o arquivo original
                val tempSong = StemmedSong(
                    id = file.absolutePath,
                    title = file.nameWithoutExtension,
                    artist = "Desconhecido",
                    originalFilePath = file.absolutePath,
                    stems = mapOf(StemType.ORIGINAL to file.absolutePath),
                    createdAt = file.lastModified()
                )
                
                audioEngine?.loadStems(tempSong.stems)
                
                _uiState.update { 
                    it.copy(
                        currentSong = tempSong,
                        isLoading = false
                    )
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "Erro ao carregar arquivo: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * Carrega uma música com stems
     */
    fun loadSong(song: StemmedSong) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                // Carrega os stems
                audioEngine?.loadStems(song.stems)
                
                _uiState.update { 
                    it.copy(
                        currentSong = song,
                        sections = song.sections,
                        chords = song.chords,
                        detectedBpm = song.bpm,
                        detectedKey = song.key,
                        isLoading = false
                    )
                }
                
                // Sincroniza metrônomo com BPM detectado
                song.bpm?.let { bpm ->
                    metronomeEngine?.setBpm(bpm)
                    _uiState.update { 
                        it.copy(
                            metronomeSettings = it.metronomeSettings.copy(bpm = bpm)
                        )
                    }
                }
                
                // Sincroniza com beats da música se disponível
                if (song.beats.isNotEmpty()) {
                    metronomeEngine?.syncWithSong(song.beats) {
                        _uiState.value.currentPositionMs
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "Erro ao carregar música: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    /**
     * Carrega stems de arquivos individuais
     */
    fun loadStemsFromFiles(stemFiles: Map<StemType, String>) {
        _uiState.update { it.copy(isLoading = true) }
        audioEngine?.loadStems(stemFiles)
        _uiState.update { it.copy(isLoading = false) }
    }
    
    // ==================== Controles de Reprodução ====================
    
    fun play() {
        audioEngine?.play()
    }
    
    fun pause() {
        audioEngine?.pause()
    }
    
    fun togglePlayPause() {
        audioEngine?.togglePlayPause()
    }
    
    fun stop() {
        audioEngine?.stop()
        metronomeEngine?.stop()
    }
    
    fun seekTo(positionMs: Long) {
        audioEngine?.seekTo(positionMs)
    }
    
    fun seekBy(deltaMs: Long) {
        audioEngine?.seekBy(deltaMs)
    }
    
    fun seekToSection(section: SongSection) {
        _uiState.update { it.copy(selectedSection = section) }
        audioEngine?.seekTo(section.startMs)
    }
    
    // ==================== Velocidade e Pitch ====================
    
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 2.0f)
        audioEngine?.setPlaybackSpeed(clampedSpeed)
        _uiState.update { it.copy(playbackSpeed = clampedSpeed) }
    }
    
    fun setPitchSemitones(semitones: Int) {
        val clamped = semitones.coerceIn(-12, 12)
        audioEngine?.setPitchSemitones(clamped)
        _uiState.update { it.copy(pitchSemitones = clamped) }
    }
    
    fun resetSpeedAndPitch() {
        setPlaybackSpeed(1.0f)
        setPitchSemitones(0)
    }
    
    // ==================== Controle de Stems ====================
    
    fun setStemVolume(stemType: StemType, volume: Float) {
        audioEngine?.setStemVolume(stemType, volume)
        _uiState.update { 
            it.copy(
                stemVolumes = it.stemVolumes + (stemType to volume)
            )
        }
    }
    
    fun toggleStemMute(stemType: StemType) {
        audioEngine?.toggleStemMute(stemType)
        val newMuted = !(uiState.value.stemMuted[stemType] ?: false)
        _uiState.update { 
            it.copy(
                stemMuted = it.stemMuted + (stemType to newMuted)
            )
        }
    }
    
    fun soloStem(stemType: StemType?) {
        audioEngine?.setSoloStem(stemType)
        _uiState.update { it.copy(soloStem = stemType) }
    }
    
    fun applyPreset(preset: BackingTrackPreset) {
        audioEngine?.applyPreset(preset.stemConfig)
        _uiState.update { 
            it.copy(
                activePreset = preset,
                stemVolumes = it.stemVolumes + preset.stemConfig,
                stemMuted = preset.stemConfig.mapValues { (_, volume) -> volume <= 0f },
                soloStem = null
            )
        }
    }
    
    fun resetStemVolumes() {
        audioEngine?.resetVolumes()
        _uiState.update { 
            it.copy(
                stemVolumes = StemType.values().associateWith { 1f },
                stemMuted = StemType.values().associateWith { false },
                soloStem = null,
                activePreset = null
            )
        }
    }
    
    // ==================== Loops ====================
    
    fun startSettingLoop() {
        _uiState.update { 
            it.copy(
                isSettingLoopStart = true,
                tempLoopStartMs = it.currentPositionMs
            )
        }
    }
    
    fun setLoopEnd() {
        val startMs = _uiState.value.tempLoopStartMs ?: return
        val endMs = _uiState.value.currentPositionMs
        
        if (endMs <= startMs) return
        
        val newLoop = LoopMarker(
            id = UUID.randomUUID().toString(),
            name = "Loop ${_uiState.value.loops.size + 1}",
            startMs = startMs,
            endMs = endMs
        )
        
        _uiState.update { 
            it.copy(
                loops = it.loops + newLoop,
                activeLoop = newLoop,
                loopEnabled = true,
                isSettingLoopStart = false,
                tempLoopStartMs = null
            )
        }
        
        audioEngine?.setLoop(newLoop)
    }
    
    fun cancelLoopSetting() {
        _uiState.update { 
            it.copy(
                isSettingLoopStart = false,
                tempLoopStartMs = null
            )
        }
    }
    
    fun setActiveLoop(loop: LoopMarker?) {
        _uiState.update { 
            it.copy(
                activeLoop = loop,
                loopEnabled = loop != null
            )
        }
        audioEngine?.setLoop(loop)
    }
    
    fun toggleLoopEnabled() {
        val enabled = !_uiState.value.loopEnabled
        _uiState.update { it.copy(loopEnabled = enabled) }
        audioEngine?.setLoopEnabled(enabled)
    }
    
    fun deleteLoop(loop: LoopMarker) {
        _uiState.update { 
            val newLoops = it.loops.filter { l -> l.id != loop.id }
            val newActiveLoop = if (it.activeLoop?.id == loop.id) null else it.activeLoop
            it.copy(
                loops = newLoops,
                activeLoop = newActiveLoop,
                loopEnabled = newActiveLoop != null
            )
        }
        
        if (_uiState.value.activeLoop == null) {
            audioEngine?.setLoop(null)
        }
    }
    
    fun loopSection(section: SongSection) {
        val loop = LoopMarker(
            id = "section_${section.id}",
            name = section.name,
            startMs = section.startMs,
            endMs = section.endMs,
            color = section.color
        )
        
        _uiState.update { 
            it.copy(
                activeLoop = loop,
                loopEnabled = true,
                selectedSection = section
            )
        }
        
        audioEngine?.setLoop(loop)
        audioEngine?.seekTo(section.startMs)
    }
    
    // ==================== Metrônomo ====================
    
    fun toggleMetronome() {
        metronomeEngine?.toggle()
    }
    
    fun startMetronomeWithCountIn() {
        metronomeEngine?.startWithCountIn()
        _uiState.update { it.copy(isInCountIn = true) }
    }
    
    fun stopMetronome() {
        metronomeEngine?.stop()
    }
    
    fun setMetronomeBpm(bpm: Int) {
        metronomeEngine?.setBpm(bpm)
        _uiState.update { 
            it.copy(
                metronomeSettings = it.metronomeSettings.copy(bpm = bpm)
            )
        }
    }
    
    fun setMetronomeBeatsPerBar(beats: Int) {
        metronomeEngine?.setBeatsPerBar(beats)
        _uiState.update { 
            it.copy(
                metronomeSettings = it.metronomeSettings.copy(beatsPerBar = beats)
            )
        }
    }
    
    fun setMetronomeVolume(volume: Float) {
        metronomeEngine?.setVolume(volume)
        _uiState.update { 
            it.copy(
                metronomeSettings = it.metronomeSettings.copy(volume = volume)
            )
        }
    }
    
    fun setCountInBeats(beats: Int) {
        metronomeEngine?.setCountInBeats(beats)
        _uiState.update { 
            it.copy(
                metronomeSettings = it.metronomeSettings.copy(countInBeats = beats)
            )
        }
    }
    
    fun setMetronomeSubdivisions(subdivisions: Int) {
        metronomeEngine?.setSubdivisions(subdivisions)
        _uiState.update { 
            it.copy(
                metronomeSettings = it.metronomeSettings.copy(subdivisions = subdivisions)
            )
        }
    }
    
    fun updateMetronomeSettings(settings: MetronomeSettings) {
        metronomeEngine?.updateSettings(settings)
        _uiState.update { it.copy(metronomeSettings = settings) }
    }
    
    fun tapTempo(): Int? {
        return metronomeEngine?.recordTap()?.also { bpm ->
            setMetronomeBpm(bpm)
        }
    }
    
    fun syncMetronomeWithSong() {
        _uiState.value.detectedBpm?.let { bpm ->
            setMetronomeBpm(bpm)
        }
    }
    
    // ==================== Play com Count-In ====================
    
    fun playWithCountIn() {
        if (_uiState.value.metronomeSettings.countInBeats > 0) {
            metronomeEngine?.startWithCountIn()
            // O onCountInComplete vai iniciar a reprodução
        } else {
            play()
        }
    }
    
    // ==================== Acordes ====================
    
    private fun updateCurrentChord(positionMs: Long) {
        val chord = _uiState.value.chords.find { 
            positionMs >= it.startMs && positionMs < it.endMs 
        }
        
        if (chord != _uiState.value.currentChord) {
            _uiState.update { it.copy(currentChord = chord) }
        }
    }
    
    // ==================== Seções ====================
    
    fun addCustomSection(name: String, startMs: Long, endMs: Long, type: SectionType = SectionType.CUSTOM) {
        val section = SongSection(
            id = UUID.randomUUID().toString(),
            name = name,
            type = type,
            startMs = startMs,
            endMs = endMs
        )
        
        _uiState.update { 
            it.copy(sections = it.sections + section)
        }
    }
    
    fun deleteSection(section: SongSection) {
        _uiState.update { 
            it.copy(
                sections = it.sections.filter { s -> s.id != section.id },
                selectedSection = if (it.selectedSection?.id == section.id) null else it.selectedSection
            )
        }
    }
    
    // ==================== Cleanup ====================
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    override fun onCleared() {
        super.onCleared()
        audioEngine?.release()
        metronomeEngine?.release()
    }
}
