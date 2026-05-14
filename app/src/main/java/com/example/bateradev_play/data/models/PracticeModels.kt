package com.example.bateradev_play.data.models

import kotlinx.serialization.Serializable

/**
 * Modelo para uma música com stems separados
 */
@Serializable
data class StemmedSong(
    val id: String,
    val title: String,
    val artist: String = "Desconhecido",
    val duration: Long = 0, // em milissegundos
    val bpm: Int? = null,
    val key: String? = null,
    val originalFilePath: String,
    val stems: Map<StemType, String> = emptyMap(), // tipo -> caminho do arquivo
    val sections: List<SongSection> = emptyList(),
    val chords: List<ChordMarker> = emptyList(),
    val beats: List<BeatMarker> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Tipos de stems suportados
 */
@Serializable
enum class StemType {
    ORIGINAL,   // Arquivo original
    DRUMS,      // Apenas bateria
    NO_DRUMS,   // Tudo menos bateria
    VOCALS,     // Apenas vocais
    NO_VOCALS,  // Tudo menos vocais
    BASS,       // Apenas baixo
    OTHER,      // Outros instrumentos (guitarras, teclados, etc.)
    PIANO,      // Piano/Teclados (se o modelo suportar)
    GUITAR      // Guitarras (se o modelo suportar)
}

/**
 * Seção da música detectada automaticamente ou marcada manualmente
 */
@Serializable
data class SongSection(
    val id: String,
    val name: String, // "Intro", "Verso", "Refrão", "Ponte", "Outro"
    val type: SectionType,
    val startMs: Long,
    val endMs: Long,
    val color: Long = 0xFF4CAF50 // Cor para visualização
)

@Serializable
enum class SectionType {
    INTRO,
    VERSE,
    PRE_CHORUS,
    CHORUS,
    BRIDGE,
    OUTRO,
    SOLO,
    BREAKDOWN,
    CUSTOM
}

/**
 * Marcador de acorde
 */
@Serializable
data class ChordMarker(
    val chordName: String, // "Am", "C", "G", "Dm7", etc.
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1f
)

/**
 * Marcador de batida/tempo
 */
@Serializable
data class BeatMarker(
    val timeMs: Long,
    val beatNumber: Int, // 1, 2, 3, 4 dentro do compasso
    val barNumber: Int,  // Número do compasso
    val isDownbeat: Boolean = false // Se é a primeira batida do compasso
)

/**
 * Loop definido pelo usuário
 */
@Serializable
data class LoopMarker(
    val id: String,
    val name: String = "",
    val startMs: Long,
    val endMs: Long,
    val color: Long = 0xFFFF9800,
    val repetitions: Int = -1 // -1 = infinito
)

/**
 * Configurações do metrônomo
 */
@Serializable
data class MetronomeSettings(
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val noteValue: Int = 4, // 4 = semínima, 8 = colcheia
    val accentFirstBeat: Boolean = true,
    val subdivisions: Int = 1, // 1 = nenhuma, 2 = colcheias, 3 = tercinas, 4 = semicolcheias
    val volume: Float = 0.8f,
    val soundPreset: MetronomeSoundPreset = MetronomeSoundPreset.CLASSIC,
    val countInBeats: Int = 4, // Cliques de entrada (até 16)
    val syncWithSong: Boolean = true,
    val isEnabled: Boolean = false
)

@Serializable
enum class MetronomeSoundPreset {
    CLASSIC,      // Click tradicional
    WOOD_BLOCK,   // Bloco de madeira
    HI_HAT,       // Hi-hat
    COWBELL,      // Cowbell
    RIMSHOT,      // Rimshot
    ELECTRONIC,   // Som eletrônico
    SILENT        // Visual apenas
}

/**
 * Estado do player de prática
 */
@Serializable
data class PracticePlayerState(
    val songId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val playbackSpeed: Float = 1.0f, // 0.25x a 2.0x
    val pitchSemitones: Int = 0, // -12 a +12
    val stemVolumes: Map<StemType, Float> = mapOf(
        StemType.DRUMS to 1.0f,
        StemType.VOCALS to 1.0f,
        StemType.BASS to 1.0f,
        StemType.OTHER to 1.0f
    ),
    val stemMuted: Map<StemType, Boolean> = mapOf(
        StemType.DRUMS to false,
        StemType.VOCALS to false,
        StemType.BASS to false,
        StemType.OTHER to false
    ),
    val stemSolo: StemType? = null,
    val activeLoop: LoopMarker? = null,
    val loopEnabled: Boolean = false,
    val metronomeSettings: MetronomeSettings = MetronomeSettings()
)

/**
 * Preset de backing track
 * Funciona com stems reais (DRUMS + NO_DRUMS do Demucs)
 */
@Serializable
data class BackingTrackPreset(
    val id: String,
    val name: String,
    val description: String,
    val stemConfig: Map<StemType, Float>, // volume por stem
    val icon: String = "🎵"
) {
    companion object {
        // Presets que funcionam com Demucs --two-stems drums (DRUMS + NO_DRUMS)
        val PRESETS = listOf(
            BackingTrackPreset(
                id = "no_drums",
                name = "Sem Bateria",
                description = "Pratique junto com a música",
                stemConfig = mapOf(
                    StemType.DRUMS to 0f,
                    StemType.NO_DRUMS to 1f,
                    StemType.OTHER to 1f,
                    StemType.ORIGINAL to 0f
                ),
                icon = "🥁"
            ),
            BackingTrackPreset(
                id = "drums_only",
                name = "Só Bateria",
                description = "Analise a bateria isolada",
                stemConfig = mapOf(
                    StemType.DRUMS to 1f,
                    StemType.NO_DRUMS to 0f,
                    StemType.OTHER to 0f,
                    StemType.ORIGINAL to 0f
                ),
                icon = "🎶"
            ),
            BackingTrackPreset(
                id = "drums_low",
                name = "Bateria Baixa",
                description = "Bateria em volume baixo para acompanhar",
                stemConfig = mapOf(
                    StemType.DRUMS to 0.3f,
                    StemType.NO_DRUMS to 1f,
                    StemType.OTHER to 1f,
                    StemType.ORIGINAL to 0f
                ),
                icon = "🔉"
            ),
            BackingTrackPreset(
                id = "half_drums",
                name = "Metade Bateria",
                description = "Bateria em volume médio",
                stemConfig = mapOf(
                    StemType.DRUMS to 0.5f,
                    StemType.NO_DRUMS to 1f,
                    StemType.OTHER to 1f,
                    StemType.ORIGINAL to 0f
                ),
                icon = "🔊"
            ),
            BackingTrackPreset(
                id = "original",
                name = "Original",
                description = "Música completa",
                stemConfig = mapOf(
                    StemType.DRUMS to 1f,
                    StemType.NO_DRUMS to 1f,
                    StemType.OTHER to 1f,
                    StemType.ORIGINAL to 1f
                ),
                icon = "🎵"
            ),
            BackingTrackPreset(
                id = "custom",
                name = "Personalizado",
                description = "Configure como quiser",
                stemConfig = mapOf(
                    StemType.DRUMS to 1f,
                    StemType.NO_DRUMS to 1f,
                    StemType.OTHER to 1f,
                    StemType.ORIGINAL to 1f
                ),
                icon = "⚙️"
            )
        )
    }
}

/**
 * Setlist de músicas
 */
@Serializable
data class Setlist(
    val id: String,
    val name: String,
    val description: String = "",
    val songIds: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isShared: Boolean = false,
    val shareCode: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Item de setlist com configurações específicas
 */
@Serializable
data class SetlistItem(
    val songId: String,
    val order: Int,
    val notes: String = "",
    val defaultSpeed: Float = 1.0f,
    val defaultPreset: String = "no_drums",
    val loopMarkers: List<LoopMarker> = emptyList()
)

/**
 * Resultado da análise de áudio
 */
@Serializable
data class AudioAnalysisResult(
    val bpm: Float,
    val bpmConfidence: Float,
    val key: String,
    val keyConfidence: Float,
    val timeSignature: String = "4/4",
    val duration: Long,
    val sections: List<SongSection> = emptyList(),
    val chords: List<ChordMarker> = emptyList(),
    val beats: List<BeatMarker> = emptyList(),
    val waveformPeaks: List<Float> = emptyList(),
    val analyzedAt: Long = System.currentTimeMillis()
)
