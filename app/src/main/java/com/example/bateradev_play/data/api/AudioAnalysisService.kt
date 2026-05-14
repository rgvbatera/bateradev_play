package com.example.bateradev_play.data.api

import android.content.Context
import com.example.bateradev_play.data.models.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Serviço de análise de áudio avançada
 * - Detecção de BPM e tonalidade
 * - Detecção de acordes sincronizados
 * - Detecção de seções (intro, verso, refrão, etc.)
 * - Detecção de beats/batidas
 */
class AudioAnalysisService(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: AudioAnalysisService? = null
        
        fun getInstance(context: Context): AudioAnalysisService {
            return instance ?: synchronized(this) {
                instance ?: AudioAnalysisService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    var baseUrl = BackendConfig.baseUrl
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // ==================== Response DTOs ====================
    
    data class FullAnalysisResponse(
        @SerializedName("bpm") val bpm: Float,
        @SerializedName("bpm_confidence") val bpmConfidence: Float,
        @SerializedName("key") val key: String,
        @SerializedName("key_confidence") val keyConfidence: Float,
        @SerializedName("time_signature") val timeSignature: String,
        @SerializedName("duration") val duration: Float,
        @SerializedName("sections") val sections: List<SectionDto>,
        @SerializedName("chords") val chords: List<ChordDto>,
        @SerializedName("beats") val beats: List<BeatDto>,
        @SerializedName("downbeats") val downbeats: List<Float>,
        @SerializedName("waveform_peaks") val waveformPeaks: List<Float>
    )
    
    data class SectionDto(
        @SerializedName("start") val start: Float,
        @SerializedName("end") val end: Float,
        @SerializedName("label") val label: String,
        @SerializedName("confidence") val confidence: Float
    )
    
    data class ChordDto(
        @SerializedName("start") val start: Float,
        @SerializedName("end") val end: Float,
        @SerializedName("chord") val chord: String,
        @SerializedName("confidence") val confidence: Float
    )
    
    data class BeatDto(
        @SerializedName("time") val time: Float,
        @SerializedName("beat_number") val beatNumber: Int,
        @SerializedName("bar_number") val barNumber: Int,
        @SerializedName("is_downbeat") val isDownbeat: Boolean
    )
    
    data class TaskResponse(
        @SerializedName("task_id") val taskId: String,
        @SerializedName("status") val status: String,
        @SerializedName("message") val message: String?
    )
    
    data class TaskStatusResponse(
        @SerializedName("task_id") val taskId: String,
        @SerializedName("status") val status: String,
        @SerializedName("progress") val progress: Int,
        @SerializedName("message") val message: String?,
        @SerializedName("result") val result: FullAnalysisResponse?,
        @SerializedName("error") val error: String?
    )
    
    // ==================== Análise Completa ====================
    
    /**
     * Inicia análise completa do áudio (assíncrona)
     * @return task_id para acompanhar o progresso
     */
    suspend fun startFullAnalysis(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/*".toMediaType())
                )
                .addFormDataPart("analyze_chords", "true")
                .addFormDataPart("analyze_sections", "true")
                .addFormDataPart("analyze_beats", "true")
                .build()
            
            val request = Request.Builder()
                .url("$baseUrl/api/analyze/full")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val taskResponse = gson.fromJson(body, TaskResponse::class.java)
                    Result.success(taskResponse.taskId)
                } else {
                    val error = response.body?.string() ?: "Erro desconhecido"
                    Result.failure(Exception("Erro ${response.code}: $error"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Verifica o status da análise
     */
    suspend fun getAnalysisStatus(taskId: String): Result<TaskStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/analyze/status/$taskId")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val status = gson.fromJson(body, TaskStatusResponse::class.java)
                    Result.success(status)
                } else {
                    Result.failure(Exception("Erro ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== Análises Individuais ====================
    
    /**
     * Análise rápida apenas de BPM
     */
    suspend fun analyzeBpm(audioFile: File): Result<Pair<Float, Float>> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/*".toMediaType())
                )
                .build()
            
            val request = Request.Builder()
                .url("$baseUrl/api/analyze/bpm")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val result = gson.fromJson(body, Map::class.java)
                    val bpm = (result["bpm"] as? Number)?.toFloat() ?: 120f
                    val confidence = (result["confidence"] as? Number)?.toFloat() ?: 0f
                    Result.success(Pair(bpm, confidence))
                } else {
                    Result.failure(Exception("Erro ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Análise rápida apenas de tonalidade
     */
    suspend fun analyzeKey(audioFile: File): Result<Pair<String, Float>> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/*".toMediaType())
                )
                .build()
            
            val request = Request.Builder()
                .url("$baseUrl/api/analyze/key")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val result = gson.fromJson(body, Map::class.java)
                    val key = result["key"] as? String ?: "C"
                    val confidence = (result["confidence"] as? Number)?.toFloat() ?: 0f
                    Result.success(Pair(key, confidence))
                } else {
                    Result.failure(Exception("Erro ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== Conversão de DTOs para Modelos ====================
    
    /**
     * Converte resposta completa para modelo de análise
     */
    fun convertToAnalysisResult(response: FullAnalysisResponse): AudioAnalysisResult {
        return AudioAnalysisResult(
            bpm = response.bpm,
            bpmConfidence = response.bpmConfidence,
            key = response.key,
            keyConfidence = response.keyConfidence,
            timeSignature = response.timeSignature,
            duration = (response.duration * 1000).toLong(),
            sections = response.sections.mapIndexed { index, section ->
                SongSection(
                    id = "section_$index",
                    name = formatSectionLabel(section.label),
                    type = parseSectionType(section.label),
                    startMs = (section.start * 1000).toLong(),
                    endMs = (section.end * 1000).toLong(),
                    color = getSectionColor(parseSectionType(section.label))
                )
            },
            chords = response.chords.map { chord ->
                ChordMarker(
                    chordName = chord.chord,
                    startMs = (chord.start * 1000).toLong(),
                    endMs = (chord.end * 1000).toLong(),
                    confidence = chord.confidence
                )
            },
            beats = response.beats.map { beat ->
                BeatMarker(
                    timeMs = (beat.time * 1000).toLong(),
                    beatNumber = beat.beatNumber,
                    barNumber = beat.barNumber,
                    isDownbeat = beat.isDownbeat
                )
            },
            waveformPeaks = response.waveformPeaks
        )
    }
    
    private fun formatSectionLabel(label: String): String {
        return when (label.lowercase()) {
            "intro" -> "Intro"
            "verse" -> "Verso"
            "pre-chorus", "prechorus" -> "Pré-Refrão"
            "chorus" -> "Refrão"
            "bridge" -> "Ponte"
            "outro" -> "Outro"
            "solo" -> "Solo"
            "instrumental" -> "Instrumental"
            "breakdown" -> "Breakdown"
            else -> label.replaceFirstChar { it.uppercase() }
        }
    }
    
    private fun parseSectionType(label: String): SectionType {
        return when (label.lowercase()) {
            "intro" -> SectionType.INTRO
            "verse" -> SectionType.VERSE
            "pre-chorus", "prechorus" -> SectionType.PRE_CHORUS
            "chorus" -> SectionType.CHORUS
            "bridge" -> SectionType.BRIDGE
            "outro" -> SectionType.OUTRO
            "solo" -> SectionType.SOLO
            "breakdown" -> SectionType.BREAKDOWN
            else -> SectionType.CUSTOM
        }
    }
    
    private fun getSectionColor(type: SectionType): Long {
        return when (type) {
            SectionType.INTRO -> 0xFF9C27B0       // Roxo
            SectionType.VERSE -> 0xFF2196F3       // Azul
            SectionType.PRE_CHORUS -> 0xFF03A9F4  // Azul claro
            SectionType.CHORUS -> 0xFFFF9800      // Laranja
            SectionType.BRIDGE -> 0xFF4CAF50      // Verde
            SectionType.OUTRO -> 0xFF607D8B       // Cinza azulado
            SectionType.SOLO -> 0xFFE91E63        // Rosa
            SectionType.BREAKDOWN -> 0xFF795548   // Marrom
            SectionType.CUSTOM -> 0xFF9E9E9E      // Cinza
        }
    }
}
