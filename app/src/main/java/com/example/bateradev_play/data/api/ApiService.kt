package com.example.bateradev_play.data.api

import android.content.Context
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
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Serviço centralizado para comunicação com o backend API
 */
class ApiService(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: ApiService? = null
        
        fun getInstance(context: Context): ApiService {
            return instance ?: synchronized(this) {
                instance ?: ApiService(context.applicationContext).also { instance = it }
            }
        }
    }
    
    var baseUrl = BackendConfig.baseUrl
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS) // Demucs pode demorar muito
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // ==================== Health Check ====================
    
    suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== Demucs API ====================
    
    data class DemucsResponse(
        @SerializedName("task_id") val taskId: String,
        @SerializedName("status") val status: String,
        @SerializedName("message") val message: String?
    )
    
    data class DemucsStatusResponse(
        @SerializedName("task_id") val taskId: String?,
        @SerializedName("status") val status: String,
        @SerializedName("progress") val progress: Int,
        @SerializedName("message") val message: String?,
        @SerializedName("output_files") val outputFiles: Map<String, String>?,
        @SerializedName("requested_stems") val requestedStems: String?,
        @SerializedName("error") val error: String?
    )
    
    /**
     * Inicia separação de stems com Demucs
     * @param audioFile Arquivo de áudio para processar
     * @param model Modelo Demucs (htdemucs, htdemucs_ft, mdx_extra)
     * @param stems Stems para extrair (drums, vocals, bass, other, all)
     * @return task_id para acompanhar o progresso
     */
    suspend fun startDemucsProcess(
        audioFile: File,
        model: String = "htdemucs",
        stems: String = "drums"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/*".toMediaType())
                )
                .addFormDataPart("model", model)
                .addFormDataPart("stems", stems)
                .build()
            
            val request = Request.Builder()
                .url("$baseUrl/api/demucs/separate")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val demucsResponse = gson.fromJson(body, DemucsResponse::class.java)
                    Result.success(demucsResponse.taskId)
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
     * Verifica o status de uma tarefa Demucs
     */
    suspend fun getDemucsStatus(taskId: String): Result<DemucsStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/demucs/status/$taskId")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val status = gson.fromJson(body, DemucsStatusResponse::class.java)
                    Result.success(status)
                } else {
                    Result.failure(Exception("Erro ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Baixa o stem processado
     * @param taskId ID da tarefa
     * @param stem Nome do stem (drums, no_drums, vocals, etc.)
     * @param outputDir Diretório de destino
     * @return Arquivo baixado
     */
    suspend fun downloadStem(
        taskId: String,
        stem: String,
        outputDir: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/demucs/download/$taskId/$stem")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro ${response.code}"))
                }
                
                val contentDisposition = response.header("Content-Disposition")
                val fileName = contentDisposition?.substringAfter("filename=")?.trim('"')
                    ?: "${stem}_${taskId}.wav"
                
                val outputFile = File(outputDir, fileName)
                
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                Result.success(outputFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== Analysis API ====================
    
    data class AnalysisResponse(
        @SerializedName("bpm") val bpm: Int,
        @SerializedName("key") val key: String,
        @SerializedName("duration") val duration: Double,
        @SerializedName("confidence") val confidence: Double
    )
    
    /**
     * Analisa um arquivo de áudio
     * @return BPM, tom, duração e confiança
     */
    suspend fun analyzeAudio(audioFile: File): Result<AnalysisResponse> = withContext(Dispatchers.IO) {
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
                .url("$baseUrl/api/analyze")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val analysis = gson.fromJson(body, AnalysisResponse::class.java)
                    Result.success(analysis)
                } else {
                    val error = response.body?.string() ?: "Erro desconhecido"
                    Result.failure(Exception("Erro ${response.code}: $error"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ==================== Files API ====================
    
    data class FilesResponse(
        @SerializedName("files") val files: List<FileInfo>
    )
    
    data class FileInfo(
        @SerializedName("name") val name: String,
        @SerializedName("path") val path: String,
        @SerializedName("size") val size: Long,
        @SerializedName("modified") val modified: String
    )
    
    /**
     * Lista arquivos processados no servidor
     */
    suspend fun listFiles(): Result<List<FileInfo>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/files")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("Resposta vazia")
                    val filesResponse = gson.fromJson(body, FilesResponse::class.java)
                    Result.success(filesResponse.files)
                } else {
                    Result.failure(Exception("Erro ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
