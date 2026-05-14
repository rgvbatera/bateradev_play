package com.example.bateradev_play.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.bateradev_play.data.api.BackendConfig
import com.example.bateradev_play.data.models.DownloadState
import com.example.bateradev_play.data.models.VideoInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class YouTubeRepository(private val context: Context) {
    
    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    private var baseUrl = BackendConfig.baseUrl
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS) // Downloads podem demorar
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    val downloadDir: File
        get() {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "BateraDevPlay/Downloads"
            )
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    
    fun setServerUrl(url: String) {
        baseUrl = url
    }
    
    suspend fun initialize() {
        // Verificar se o servidor está disponível
        withContext(Dispatchers.IO) {
            try {
                _downloadState.value = _downloadState.value.copy(
                    statusMessage = "Conectando ao servidor..."
                )
                
                val request = Request.Builder()
                    .url("$baseUrl/health")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        _downloadState.value = _downloadState.value.copy(
                            statusMessage = "Servidor conectado!"
                        )
                    } else {
                        _downloadState.value = _downloadState.value.copy(
                            statusMessage = "Servidor offline - usando modo local"
                        )
                    }
                }
            } catch (e: Exception) {
                _downloadState.value = _downloadState.value.copy(
                    statusMessage = "Modo offline"
                )
            }
        }
    }
    
    suspend fun getVideoInfo(url: String): VideoInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/youtube/info?url=${java.net.URLEncoder.encode(url, "UTF-8")}")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@withContext null
                        val data = gson.fromJson(body, VideoInfoResponse::class.java)
                        VideoInfo(
                            title = data.title,
                            channel = data.channel,
                            duration = data.duration,
                            thumbnailUrl = data.thumbnail,
                            viewCount = data.view_count
                        )
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun downloadAudio(url: String, onProgress: (Float, String) -> Unit): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                _downloadState.value = DownloadState(
                    url = url,
                    isDownloading = true,
                    progress = 0f,
                    statusMessage = "Iniciando download..."
                )
                
                // Fazer request para o backend
                val request = Request.Builder()
                    .url("$baseUrl/api/youtube/download?url=${java.net.URLEncoder.encode(url, "UTF-8")}&format=mp3")
                    .get()
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Erro no servidor: ${response.code}")
                    }
                    
                    // Obter nome do arquivo do header ou gerar um
                    val contentDisposition = response.header("Content-Disposition")
                    val fileName = contentDisposition?.substringAfter("filename=")?.trim('"')
                        ?: "download_${System.currentTimeMillis()}.mp3"
                    
                    val outputFile = File(downloadDir, fileName)
                    
                    val body = response.body ?: throw Exception("Resposta vazia")
                    val contentLength = body.contentLength()
                    
                    var downloadedSize = 0L
                    
                    body.byteStream().use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedSize += bytesRead
                                
                                if (contentLength > 0) {
                                    val progress = downloadedSize.toFloat() / contentLength
                                    _downloadState.value = _downloadState.value.copy(
                                        progress = progress,
                                        statusMessage = "Baixando... ${(progress * 100).toInt()}%"
                                    )
                                    onProgress(progress, "")
                                }
                            }
                        }
                    }
                    
                    _downloadState.value = DownloadState(
                        isDownloading = false,
                        progress = 1f,
                        statusMessage = "Download concluído: ${outputFile.name}",
                        lastDownloadedFile = outputFile
                    )
                    
                    // Notificar MediaStore sobre o novo arquivo
                    notifyMediaStore(outputFile)
                    
                    Result.success(outputFile)
                }
                
            } catch (e: Exception) {
                _downloadState.value = DownloadState(
                    isDownloading = false,
                    error = "Erro: ${e.message}"
                )
                Result.failure(e)
            }
        }
    }
    
    /**
     * Notifica o MediaStore sobre um novo arquivo de áudio
     * Isso faz o arquivo aparecer em outros apps de música
     */
    private fun notifyMediaStore(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+: Usar MediaStore API
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/BateraDevPlay/Downloads")
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                
                context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    values
                )
            } else {
                // Android 9 e anterior: Usar MediaScanner
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    arrayOf("audio/mpeg"),
                    null
                )
            }
        } catch (e: Exception) {
            // Ignorar erros de MediaStore, o arquivo já foi salvo
        }
    }
    
    /**
     * Obtém o último arquivo baixado
     */
    fun getLastDownloadedFile(): File? {
        return _downloadState.value.lastDownloadedFile
    }
    
    fun resetState() {
        _downloadState.value = DownloadState()
    }
    
    // Classes para parsing JSON
    private data class VideoInfoResponse(
        val title: String,
        val channel: String,
        val duration: Long,
        val thumbnail: String,
        val view_count: Long
    )
}
