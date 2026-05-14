package com.example.bateradev_play.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import com.example.bateradev_play.data.models.BeatMarker
import com.example.bateradev_play.data.models.MetronomeSettings
import com.example.bateradev_play.data.models.MetronomeSoundPreset
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * Metrônomo inteligente com suporte a:
 * - Sincronização com música
 * - Count-in configurável (até 16 cliques)
 * - Subdivisões
 * - Presets de sons
 * - Tempo variável
 */
class MetronomeEngine(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Estado do metrônomo
    private val _settings = MutableStateFlow(MetronomeSettings())
    val settings: StateFlow<MetronomeSettings> = _settings.asStateFlow()
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _currentBeat = MutableStateFlow(0)
    val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()
    
    private val _currentBar = MutableStateFlow(0)
    val currentBar: StateFlow<Int> = _currentBar.asStateFlow()
    
    private val _countInRemaining = MutableStateFlow(0)
    val countInRemaining: StateFlow<Int> = _countInRemaining.asStateFlow()
    
    private val _isInCountIn = MutableStateFlow(false)
    val isInCountIn: StateFlow<Boolean> = _isInCountIn.asStateFlow()
    
    // SoundPool para reprodução de sons
    private var soundPool: SoundPool? = null
    private var accentSoundId: Int = 0
    private var normalSoundId: Int = 0
    private var subdivisionSoundId: Int = 0
    
    // AudioTrack pré-gerado para melhor latência
    private var accentSamples: ShortArray? = null
    private var normalSamples: ShortArray? = null
    private var subdivisionSamples: ShortArray? = null
    
    // AudioTrack para geração de som procedural
    private var audioTrack: AudioTrack? = null
    
    // Job do metrônomo
    private var metronomeJob: Job? = null
    
    // Marcadores de beat para sincronização
    private var beatMarkers: List<BeatMarker> = emptyList()
    private var syncEnabled = false
    private var currentSongPositionProvider: (() -> Long)? = null
    
    // Callbacks
    var onBeat: ((beat: Int, bar: Int, isAccent: Boolean) -> Unit)? = null
    var onCountInBeat: ((remaining: Int) -> Unit)? = null
    var onCountInComplete: (() -> Unit)? = null
    
    private val sampleRate = 44100
    
    init {
        initializeSoundPool()
        generateClickSounds()
    }
    
    /**
     * Inicializa o SoundPool
     */
    private fun initializeSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()
    }
    
    /**
     * Gera sons de click proceduralmente
     */
    private fun generateClickSounds() {
        // Gerar sons será feito quando trocar o preset
        updateSoundPreset(_settings.value.soundPreset)
    }
    
    /**
     * Atualiza as configurações do metrônomo
     */
    fun updateSettings(newSettings: MetronomeSettings) {
        val oldPreset = _settings.value.soundPreset
        _settings.value = newSettings
        
        if (oldPreset != newSettings.soundPreset) {
            updateSoundPreset(newSettings.soundPreset)
        }
    }
    
    /**
     * Define o BPM
     */
    fun setBpm(bpm: Int) {
        updateSettings(_settings.value.copy(bpm = bpm.coerceIn(30, 300)))
    }
    
    /**
     * Define beats por compasso
     */
    fun setBeatsPerBar(beats: Int) {
        updateSettings(_settings.value.copy(beatsPerBar = beats.coerceIn(1, 16)))
    }
    
    /**
     * Define volume
     */
    fun setVolume(volume: Float) {
        updateSettings(_settings.value.copy(volume = volume.coerceIn(0f, 1f)))
    }
    
    /**
     * Define cliques de count-in
     */
    fun setCountInBeats(beats: Int) {
        updateSettings(_settings.value.copy(countInBeats = beats.coerceIn(0, 16)))
    }
    
    /**
     * Define subdivisões
     */
    fun setSubdivisions(subdivisions: Int) {
        updateSettings(_settings.value.copy(subdivisions = subdivisions.coerceIn(1, 4)))
    }
    
    /**
     * Atualiza o preset de som
     */
    private fun updateSoundPreset(preset: MetronomeSoundPreset) {
        // Diferentes frequências e durações para cada preset
        val (accentFreq, normalFreq, duration) = when (preset) {
            MetronomeSoundPreset.CLASSIC -> Triple(1000f, 800f, 0.03f)
            MetronomeSoundPreset.WOOD_BLOCK -> Triple(800f, 600f, 0.05f)
            MetronomeSoundPreset.HI_HAT -> Triple(4000f, 3000f, 0.02f)
            MetronomeSoundPreset.COWBELL -> Triple(600f, 500f, 0.08f)
            MetronomeSoundPreset.RIMSHOT -> Triple(1500f, 1200f, 0.025f)
            MetronomeSoundPreset.ELECTRONIC -> Triple(440f, 330f, 0.04f)
            MetronomeSoundPreset.SILENT -> Triple(0f, 0f, 0f)
        }
        
        // Armazenar frequências para uso posterior
        accentFrequency = accentFreq
        normalFrequency = normalFreq
        clickDuration = duration
        
        // Pré-gerar samples para melhor performance
        if (preset != MetronomeSoundPreset.SILENT) {
            accentSamples = generateSamples(accentFreq, duration, 1f)
            normalSamples = generateSamples(normalFreq, duration, 1f)
            subdivisionSamples = generateSamples(normalFreq * 1.5f, duration * 0.5f, 0.5f)
        } else {
            accentSamples = null
            normalSamples = null
            subdivisionSamples = null
        }
    }
    
    /**
     * Gera array de samples para um tom
     */
    private fun generateSamples(frequency: Float, durationSeconds: Float, volume: Float): ShortArray {
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(numSamples)
        
        for (i in 0 until numSamples) {
            val time = i.toFloat() / sampleRate
            // Envelope de ataque rápido e decaimento exponencial para som mais natural
            val attack = minOf(time / 0.005f, 1f) // 5ms attack
            val decay = kotlin.math.exp(-time / (durationSeconds * 0.5f))
            val envelope = attack * decay * volume
            val sample = sin(2 * PI * frequency * time) * envelope
            samples[i] = (sample * Short.MAX_VALUE * 0.8).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return samples
    }
    
    private var accentFrequency = 1000f
    private var normalFrequency = 800f
    private var clickDuration = 0.03f
    
    /**
     * Inicia o metrônomo
     * @param withCountIn Se deve fazer count-in antes de começar
     */
    fun start(withCountIn: Boolean = false) {
        if (_isRunning.value) return
        
        _isRunning.value = true
        _currentBeat.value = 0
        _currentBar.value = 0
        
        metronomeJob = scope.launch {
            if (withCountIn && _settings.value.countInBeats > 0) {
                performCountIn()
            }
            
            runMetronomeLoop()
        }
    }
    
    /**
     * Inicia com count-in
     */
    fun startWithCountIn() {
        start(withCountIn = true)
    }
    
    /**
     * Para o metrônomo
     */
    fun stop() {
        metronomeJob?.cancel()
        metronomeJob = null
        _isRunning.value = false
        _isInCountIn.value = false
        _countInRemaining.value = 0
        _currentBeat.value = 0
        _currentBar.value = 0
    }
    
    /**
     * Alterna entre start/stop
     */
    fun toggle() {
        if (_isRunning.value) stop() else start()
    }
    
    /**
     * Executa o count-in
     */
    private suspend fun performCountIn() {
        _isInCountIn.value = true
        val countInBeats = _settings.value.countInBeats
        _countInRemaining.value = countInBeats
        
        val msPerBeat = (60_000f / _settings.value.bpm).toLong()
        
        for (i in countInBeats downTo 1) {
            if (!_isRunning.value) break
            
            _countInRemaining.value = i
            playClick(isAccent = i == countInBeats || (i - 1) % _settings.value.beatsPerBar == 0)
            onCountInBeat?.invoke(i)
            
            delay(msPerBeat)
        }
        
        _isInCountIn.value = false
        _countInRemaining.value = 0
        onCountInComplete?.invoke()
    }
    
    /**
     * Loop principal do metrônomo
     */
    private suspend fun runMetronomeLoop() = coroutineScope {
        while (_isRunning.value && isActive) {
            val settings = _settings.value
            val msPerBeat = (60_000f / settings.bpm).toLong()
            val msPerSubdivision = msPerBeat / settings.subdivisions
            
            // Incrementa o beat (1-indexed dentro do compasso)
            val newBeat = (_currentBeat.value % settings.beatsPerBar) + 1
            _currentBeat.value = newBeat
            
            if (newBeat == 1) {
                _currentBar.value = _currentBar.value + 1
            }
            
            val isAccent = newBeat == 1 && settings.accentFirstBeat
            
            // Toca o click principal
            playClick(isAccent)
            onBeat?.invoke(newBeat, _currentBar.value, isAccent)
            
            // Subdivisões
            if (settings.subdivisions > 1) {
                for (sub in 1 until settings.subdivisions) {
                    delay(msPerSubdivision)
                    if (!_isRunning.value) break
                    playSubdivisionClick()
                }
            } else {
                delay(msPerBeat)
            }
        }
    }
    
    /**
     * Sincroniza com marcadores de beat da música
     */
    fun syncWithSong(
        markers: List<BeatMarker>,
        positionProvider: () -> Long
    ) {
        beatMarkers = markers
        currentSongPositionProvider = positionProvider
        syncEnabled = true
    }
    
    /**
     * Desativa sincronização
     */
    fun disableSync() {
        syncEnabled = false
        beatMarkers = emptyList()
        currentSongPositionProvider = null
    }
    
    /**
     * Obtém o próximo beat marker baseado na posição atual
     */
    private fun getNextBeatMarker(currentPosition: Long): BeatMarker? {
        return beatMarkers.firstOrNull { it.timeMs > currentPosition }
    }
    
    /**
     * Toca o click do metrônomo
     */
    private fun playClick(isAccent: Boolean) {
        if (_settings.value.soundPreset == MetronomeSoundPreset.SILENT) return
        
        val samples = if (isAccent) accentSamples else normalSamples
        samples?.let { playSamples(it, _settings.value.volume) }
    }
    
    /**
     * Toca o click de subdivisão
     */
    private fun playSubdivisionClick() {
        if (_settings.value.soundPreset == MetronomeSoundPreset.SILENT) return
        
        subdivisionSamples?.let { playSamples(it, _settings.value.volume) }
    }
    
    /**
     * Reproduz samples de áudio
     */
    private fun playSamples(samples: ShortArray, volume: Float) {
        scope.launch(Dispatchers.Default) {
            try {
                // Aplicar volume aos samples
                val adjustedSamples = if (volume < 1f) {
                    samples.map { (it * volume).toInt().toShort() }.toShortArray()
                } else {
                    samples
                }
                
                val bufferSize = adjustedSamples.size * 2
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                
                track.write(adjustedSamples, 0, adjustedSamples.size)
                track.play()
                
                // Aguardar reprodução terminar e liberar
                val durationMs = (adjustedSamples.size * 1000L / sampleRate) + 20
                delay(durationMs)
                track.stop()
                track.release()
                
            } catch (e: Exception) {
                // Ignorar erros de áudio silenciosamente
            }
        }
    }
    
    /**
     * Gera e toca um tom (mantido para compatibilidade)
     */
    private fun playTone(frequency: Float, durationSeconds: Float, volume: Float) {
        val samples = generateSamples(frequency, durationSeconds, volume)
        playSamples(samples, 1f)
    }
    
    /**
     * Toca um tap de preview para ajuste
     */
    fun tapPreview() {
        playClick(isAccent = true)
    }
    
    /**
     * Detecta BPM por tap (máximo 4 taps)
     */
    private val tapTimes = mutableListOf<Long>()
    
    fun recordTap(): Int? {
        val now = System.currentTimeMillis()
        
        // Limpa taps antigas (mais de 2 segundos)
        tapTimes.removeAll { now - it > 2000 }
        
        tapTimes.add(now)
        
        // Precisa de pelo menos 2 taps para calcular BPM
        if (tapTimes.size < 2) return null
        
        // Calcula intervalo médio
        val intervals = mutableListOf<Long>()
        for (i in 1 until tapTimes.size) {
            intervals.add(tapTimes[i] - tapTimes[i - 1])
        }
        
        val avgInterval = intervals.average()
        val bpm = (60_000 / avgInterval).toInt().coerceIn(30, 300)
        
        // Se tiver 4 taps, reseta para próxima detecção
        if (tapTimes.size >= 4) {
            tapTimes.clear()
        }
        
        playClick(isAccent = true)
        return bpm
    }
    
    /**
     * Limpa histórico de tap tempo
     */
    fun clearTaps() {
        tapTimes.clear()
    }
    
    /**
     * Libera recursos
     */
    fun release() {
        stop()
        scope.cancel()
        soundPool?.release()
        soundPool = null
        audioTrack?.release()
        audioTrack = null
    }
}
