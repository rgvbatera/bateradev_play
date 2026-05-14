package com.example.bateradev_play.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.bateradev_play.data.models.LoopMarker
import com.example.bateradev_play.data.models.StemType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.pow

/**
 * Engine de áudio que gerencia múltiplos stems sincronizados
 * com controle de velocidade, pitch e volume individual
 */
@OptIn(UnstableApi::class)
class MultiStemAudioEngine(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Players para cada stem
    private val stemPlayers = mutableMapOf<StemType, ExoPlayer>()
    private val stemVolumes = mutableMapOf<StemType, Float>()
    private val stemMuted = mutableMapOf<StemType, Boolean>()
    
    // Estado do player
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    
    private val _pitchSemitones = MutableStateFlow(0)
    val pitchSemitones: StateFlow<Int> = _pitchSemitones.asStateFlow()
    
    // Loop
    private var activeLoop: LoopMarker? = null
    private var loopEnabled = false
    
    // Stem solado
    private var soloStem: StemType? = null
    
    // Job de atualização de posição
    private var positionUpdateJob: Job? = null
    
    // Callbacks
    var onPositionChanged: ((Long) -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onStemLoaded: ((StemType, Boolean) -> Unit)? = null
    
    /**
     * Carrega um stem individual
     */
    fun loadStem(stemType: StemType, filePath: String) {
        try {
            val player = ExoPlayer.Builder(context)
                .build()
                .apply {
                    val uri = if (filePath.startsWith("content://")) {
                        Uri.parse(filePath)
                    } else {
                        Uri.fromFile(File(filePath))
                    }
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                    playWhenReady = false
                    
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                _duration.value = maxOf(_duration.value, duration)
                                onStemLoaded?.invoke(stemType, true)
                            }
                        }
                    })
                }
            
            // Remover player anterior se existir
            stemPlayers[stemType]?.release()
            
            stemPlayers[stemType] = player
            stemVolumes[stemType] = 1.0f
            stemMuted[stemType] = false
            
        } catch (e: Exception) {
            onStemLoaded?.invoke(stemType, false)
        }
    }
    
    /**
     * Carrega múltiplos stems de uma vez
     */
    fun loadStems(stems: Map<StemType, String>) {
        stems.forEach { (type, path) ->
            loadStem(type, path)
        }
    }
    
    /**
     * Inicia a reprodução de todos os stems sincronizados
     */
    fun play() {
        if (stemPlayers.isEmpty()) return
        
        stemPlayers.values.forEach { player ->
            player.playWhenReady = true
        }
        _isPlaying.value = true
        onPlaybackStateChanged?.invoke(true)
        startPositionUpdates()
    }
    
    /**
     * Pausa todos os stems
     */
    fun pause() {
        stemPlayers.values.forEach { player ->
            player.playWhenReady = false
        }
        _isPlaying.value = false
        onPlaybackStateChanged?.invoke(false)
        stopPositionUpdates()
    }
    
    /**
     * Alterna entre play/pause
     */
    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }
    
    /**
     * Para e volta ao início
     */
    fun stop() {
        pause()
        seekTo(0)
    }
    
    /**
     * Busca posição específica em todos os stems
     */
    fun seekTo(positionMs: Long) {
        val clampedPosition = positionMs.coerceIn(0, _duration.value)
        stemPlayers.values.forEach { player ->
            player.seekTo(clampedPosition)
        }
        _currentPosition.value = clampedPosition
        onPositionChanged?.invoke(clampedPosition)
    }
    
    /**
     * Avança/retrocede por tempo relativo
     */
    fun seekBy(deltaMs: Long) {
        seekTo(_currentPosition.value + deltaMs)
    }
    
    /**
     * Define a velocidade de reprodução (0.25x a 2.0x)
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.25f, 2.0f)
        _playbackSpeed.value = clampedSpeed
        updatePlaybackParameters()
    }
    
    /**
     * Define o pitch em semitons (-12 a +12)
     */
    fun setPitchSemitones(semitones: Int) {
        val clampedSemitones = semitones.coerceIn(-12, 12)
        _pitchSemitones.value = clampedSemitones
        updatePlaybackParameters()
    }
    
    /**
     * Atualiza os parâmetros de playback em todos os players
     */
    private fun updatePlaybackParameters() {
        // Calcula o pitch baseado em semitons
        // pitch = 2^(semitones/12)
        val pitchFactor = 2f.pow(_pitchSemitones.value / 12f)
        
        val params = PlaybackParameters(_playbackSpeed.value, pitchFactor)
        stemPlayers.values.forEach { player ->
            player.playbackParameters = params
        }
    }
    
    /**
     * Define o volume de um stem específico (0.0 a 1.0)
     */
    fun setStemVolume(stemType: StemType, volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        stemVolumes[stemType] = clampedVolume
        updateStemVolume(stemType)
    }
    
    /**
     * Muta/desmuta um stem
     */
    fun toggleStemMute(stemType: StemType) {
        stemMuted[stemType] = !(stemMuted[stemType] ?: false)
        updateStemVolume(stemType)
    }
    
    /**
     * Define se um stem está mutado
     */
    fun setStemMuted(stemType: StemType, muted: Boolean) {
        stemMuted[stemType] = muted
        updateStemVolume(stemType)
    }
    
    /**
     * Solo de um stem (muta todos os outros)
     */
    fun setSoloStem(stemType: StemType?) {
        soloStem = stemType
        stemPlayers.keys.forEach { type ->
            updateStemVolume(type)
        }
    }
    
    /**
     * Atualiza o volume efetivo de um stem considerando mute e solo
     */
    private fun updateStemVolume(stemType: StemType) {
        val player = stemPlayers[stemType] ?: return
        
        val effectiveVolume = when {
            // Se há solo ativo, apenas o stem solado toca
            soloStem != null && soloStem != stemType -> 0f
            // Se está mutado
            stemMuted[stemType] == true -> 0f
            // Volume normal
            else -> stemVolumes[stemType] ?: 1f
        }
        
        player.volume = effectiveVolume
    }
    
    /**
     * Obtém o volume atual de um stem
     */
    fun getStemVolume(stemType: StemType): Float {
        return stemVolumes[stemType] ?: 1f
    }
    
    /**
     * Verifica se um stem está mutado
     */
    fun isStemMuted(stemType: StemType): Boolean {
        return stemMuted[stemType] ?: false
    }
    
    /**
     * Define um loop A-B
     */
    fun setLoop(loop: LoopMarker?) {
        activeLoop = loop
        loopEnabled = loop != null
    }
    
    /**
     * Ativa/desativa o loop atual
     */
    fun setLoopEnabled(enabled: Boolean) {
        loopEnabled = enabled && activeLoop != null
    }
    
    /**
     * Inicia atualizações de posição periódicas
     */
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive && _isPlaying.value) {
                updatePosition()
                checkLoopBounds()
                delay(16) // ~60fps
            }
        }
    }
    
    /**
     * Para as atualizações de posição
     */
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
    
    /**
     * Atualiza a posição atual baseada no primeiro player ativo
     */
    private fun updatePosition() {
        val masterPlayer = stemPlayers.values.firstOrNull() ?: return
        val position = masterPlayer.currentPosition
        _currentPosition.value = position
        onPositionChanged?.invoke(position)
    }
    
    /**
     * Verifica e aplica limites de loop
     */
    private fun checkLoopBounds() {
        if (!loopEnabled) return
        
        val loop = activeLoop ?: return
        val position = _currentPosition.value
        
        if (position >= loop.endMs) {
            seekTo(loop.startMs)
        }
    }
    
    /**
     * Aplica um preset de backing track
     */
    fun applyPreset(preset: Map<StemType, Float>) {
        soloStem = null
        preset.forEach { (stemType, volume) ->
            stemVolumes[stemType] = volume
            stemMuted[stemType] = volume <= 0f
            updateStemVolume(stemType)
        }
    }
    
    /**
     * Reseta todos os volumes para padrão
     */
    fun resetVolumes() {
        soloStem = null
        stemPlayers.keys.forEach { type ->
            stemVolumes[type] = 1f
            stemMuted[type] = false
            updateStemVolume(type)
        }
    }
    
    /**
     * Libera todos os recursos
     */
    fun release() {
        stopPositionUpdates()
        scope.cancel()
        stemPlayers.values.forEach { it.release() }
        stemPlayers.clear()
        stemVolumes.clear()
        stemMuted.clear()
    }
    
    /**
     * Sincroniza todos os players para a mesma posição
     */
    fun syncPlayers() {
        val masterPosition = stemPlayers.values.firstOrNull()?.currentPosition ?: return
        stemPlayers.values.forEach { player ->
            if (kotlin.math.abs(player.currentPosition - masterPosition) > 50) {
                player.seekTo(masterPosition)
            }
        }
    }
    
    /**
     * Verifica se há algum stem carregado
     */
    fun hasStems(): Boolean = stemPlayers.isNotEmpty()
    
    /**
     * Obtém os tipos de stems carregados
     */
    fun getLoadedStemTypes(): Set<StemType> = stemPlayers.keys.toSet()
}
