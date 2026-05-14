package com.example.bateradev_play.data.api

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 🎵 Serviço de Geração de Música com IA
 * 
 * Conecta com o servidor Python para:
 * - Gerar músicas a partir de prompts
 * - Gerar backing tracks para prática
 * - Separar em 6 stems (com guitarra e piano)
 */
class MusicGenerationService(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: MusicGenerationService? = null
        
        fun getInstance(context: Context): MusicGenerationService {
            return instance ?: synchronized(this) {
                instance ?: MusicGenerationService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    var baseUrl = BackendConfig.baseUrl
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)  // Música demora mais
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // ==================== GERAÇÃO DE MÚSICA ====================
    
    /**
     * 🆕 Gera música a partir de descrição textual
     * 
     * Usa sistema de tasks assíncronas com polling:
     * 1. POST /api/music/generate → task_id
     * 2. GET /api/music/status/{task_id} → polling até completed
     * 3. Retorna URL de download
     * 
     * @param prompt Descrição da música (ex: "rock drumless backing track, 120 bpm")
     * @param duration Duração em segundos (máx 30)
     * @param onProgress Callback opcional para progresso (0-100)
     * @return URL do arquivo gerado
     */
    suspend fun generateMusic(
        prompt: String,
        duration: Int = 10,
        onProgress: ((Int, String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Iniciar geração (retorna task_id)
            val json = """
                {
                    "prompt": "$prompt",
                    "duration": $duration,
                    "model": "small"
                }
            """.trimIndent()
            
            val requestBody = json.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/api/music/generate")
                .post(requestBody)
                .build()
            
            val taskId = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Erro ao iniciar geração: ${response.code}")
                }
                val body = response.body?.string() ?: throw Exception("Resposta vazia")
                val result = gson.fromJson(body, Map::class.java)
                result["task_id"] as? String ?: throw Exception("task_id não retornado")
            }
            
            onProgress?.invoke(5, "Geração iniciada...")
            
            // 2. Polling do status até completar
            var attempts = 0
            val maxAttempts = 120 // 2 minutos máximo (polling a cada 1s)
            
            while (attempts < maxAttempts) {
                kotlinx.coroutines.delay(1000) // Espera 1 segundo entre verificações
                
                val statusRequest = Request.Builder()
                    .url("$baseUrl/api/music/status/$taskId")
                    .get()
                    .build()
                
                val statusResult = client.newCall(statusRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Erro ao verificar status: ${response.code}")
                    }
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    gson.fromJson(body, Map::class.java)
                }
                
                val status = statusResult["status"] as? String ?: "unknown"
                val progress = (statusResult["progress"] as? Double)?.toInt() ?: 0
                val message = statusResult["message"] as? String ?: ""
                
                onProgress?.invoke(progress, message)
                
                when (status) {
                    "completed" -> {
                        // 3. Retornar URL de download
                        val downloadUrl = "$baseUrl/api/music/download/$taskId"
                        return@withContext Result.success(downloadUrl)
                    }
                    "error" -> {
                        throw Exception(message.ifEmpty { "Erro na geração" })
                    }
                    "processing" -> {
                        // Continua polling
                        attempts++
                    }
                    else -> {
                        attempts++
                    }
                }
            }
            
            Result.failure(Exception("Timeout: geração demorou muito"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 🆕 Gera backing track específico para bateristas
     * 
     * Usa sistema de tasks assíncronas:
     * 1. POST /api/music/generate-backing → task_id
     * 2. GET /api/music/status/{task_id} → polling
     * 3. Retorna URL de download
     * 
     * @param genre Gênero musical (rock, jazz, funk, metal, bossa)
     * @param bpm Tempo em BPM
     * @param key Tonalidade opcional (C, D, E, etc.)
     * @param duration Duração em segundos (máx 30)
     * @param excludeDrums Se true, remove bateria do backing
     * @param onProgress Callback opcional para progresso
     */
    suspend fun generateBackingTrack(
        genre: String,
        bpm: Int,
        key: String? = null,
        duration: Int = 30,
        excludeDrums: Boolean = true,
        onProgress: ((Int, String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Iniciar geração
            val jsonMap = mutableMapOf<String, Any>(
                "genre" to genre,
                "bpm" to bpm,
                "duration" to duration,
                "exclude_drums" to excludeDrums
            )
            
            key?.let { jsonMap["key"] = it }
            
            val json = gson.toJson(jsonMap)
            val requestBody = json.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/api/music/generate-backing")  // URL corrigida!
                .post(requestBody)
                .build()
            
            val taskId = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Erro ao iniciar geração: ${response.code}")
                }
                val body = response.body?.string() ?: throw Exception("Resposta vazia")
                val result = gson.fromJson(body, Map::class.java)
                result["task_id"] as? String ?: throw Exception("task_id não retornado")
            }
            
            onProgress?.invoke(5, "Gerando backing track...")
            
            // 2. Polling do status
            var attempts = 0
            val maxAttempts = 120
            
            while (attempts < maxAttempts) {
                kotlinx.coroutines.delay(1000)
                
                val statusRequest = Request.Builder()
                    .url("$baseUrl/api/music/status/$taskId")
                    .get()
                    .build()
                
                val statusResult = client.newCall(statusRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Erro ao verificar status: ${response.code}")
                    }
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    gson.fromJson(body, Map::class.java)
                }
                
                val status = statusResult["status"] as? String ?: "unknown"
                val progress = (statusResult["progress"] as? Double)?.toInt() ?: 0
                val message = statusResult["message"] as? String ?: ""
                
                onProgress?.invoke(progress, message)
                
                when (status) {
                    "completed" -> {
                        val downloadUrl = "$baseUrl/api/music/download/$taskId"
                        return@withContext Result.success(downloadUrl)
                    }
                    "error" -> {
                        throw Exception(message.ifEmpty { "Erro na geração" })
                    }
                    else -> attempts++
                }
            }
            
            Result.failure(Exception("Timeout: geração demorou muito"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== SEPARAÇÃO 6 STEMS ====================
    
    /**
     * 🆕 Separa música em 6 stems (com guitarra e piano separados)
     * 
     * Stems retornados:
     * - vocals (voz)
     * - drums (bateria)
     * - bass (baixo)
     * - guitar (guitarra) 🆕
     * - piano (piano/teclados) 🆕
     * - other (outros instrumentos)
     */
    suspend fun separate6Stems(audioFile: File): Result<SixStemResult> = withContext(Dispatchers.IO) {
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
                .url("$baseUrl/api/separate/6stems")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val result = gson.fromJson(body, Map::class.java)
                    
                    if (result["status"] == "success") {
                        val stems = result["stems"] as Map<String, String>
                        val sixStemResult = SixStemResult(
                            vocals = stems["vocals"] ?: "",
                            drums = stems["drums"] ?: "",
                            bass = stems["bass"] ?: "",
                            guitar = stems["guitar"] ?: "",
                            piano = stems["piano"] ?: "",
                            other = stems["other"] ?: ""
                        )
                        Result.success(sixStemResult)
                    } else {
                        Result.failure(Exception(result["error"] as? String ?: "Erro desconhecido"))
                    }
                } else {
                    Result.failure(Exception("Erro ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== UTILITÁRIOS ====================
    
    /**
     * Verifica se o servidor está online
     */
    suspend fun checkHealth(): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/health")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    @Suppress("UNCHECKED_CAST")
                    val result = gson.fromJson(body, Map::class.java) as Map<String, Any>
                    Result.success(result)
                } else {
                    Result.failure(Exception("Servidor offline"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Lista modelos disponíveis no servidor
     */
    suspend fun listModels(): Result<Map<String, List<Map<String, Any>>>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/models")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    @Suppress("UNCHECKED_CAST")
                    val result = gson.fromJson(body, Map::class.java) as Map<String, List<Map<String, Any>>>
                    Result.success(result)
                } else {
                    Result.failure(Exception("Erro ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 🆕 Resultado da separação em 6 stems
 */
data class SixStemResult(
    val vocals: String,
    val drums: String,
    val bass: String,
    val guitar: String,  // 🆕 NOVO!
    val piano: String,   // 🆕 NOVO!
    val other: String
)
