package com.example.bateradev_play.ui.viewmodels

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bateradev_play.data.api.ApiService
import com.example.bateradev_play.data.models.StemType
import com.example.bateradev_play.data.models.StemmedSong
import com.example.bateradev_play.data.repository.SongsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class DrumRemovalUiState(
    val selectedFile: File? = null,
    val selectedModel: String = "htdemucs",
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val outputFile: File? = null, // Mantido para compatibilidade
    val outputStems: Map<StemType, File> = emptyMap(), // Novo: mapa de stems
    val savedSong: StemmedSong? = null, // Música salva no repositório
    val error: String? = null,
    val taskId: String? = null
)

class DrumRemovalViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(DrumRemovalUiState())
    val uiState: StateFlow<DrumRemovalUiState> = _uiState.asStateFlow()
    
    private var apiService: ApiService? = null
    private var songsRepository: SongsRepository? = null
    
    private val outputDir: File
        get() {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                "BateraDevPlay/Stems"
            )
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    
    fun initialize(context: Context) {
        apiService = ApiService.getInstance(context)
        songsRepository = SongsRepository.getInstance(context)
    }
    
    fun selectFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Copiar arquivo para diretório temporário
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = getFileName(context, uri)
                val tempFile = File(context.cacheDir, fileName)
                
                withContext(Dispatchers.IO) {
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    selectedFile = tempFile,
                    outputFile = null,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Erro ao carregar arquivo: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Seleciona um arquivo diretamente (sem URI)
     * Usado quando vem da tela de download
     */
    fun selectFileDirectly(file: File) {
        _uiState.value = _uiState.value.copy(
            selectedFile = file,
            outputFile = null,
            outputStems = emptyMap(),
            savedSong = null,
            error = null,
            taskId = null,
            isProcessing = false,
            progress = 0f,
            statusMessage = ""
        )
    }
    
    fun selectModel(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
    }
    
    fun processAudio(context: Context, onComplete: (Boolean, String) -> Unit) {
        val file = _uiState.value.selectedFile ?: return
        val api = apiService ?: ApiService.getInstance(context).also { apiService = it }
        val repo = songsRepository ?: SongsRepository.getInstance(context).also { songsRepository = it }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                progress = 0f,
                error = null,
                outputFile = null,
                outputStems = emptyMap(),
                savedSong = null,
                taskId = null
            )
            
            try {
                // 1. Verificar se servidor está disponível
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Conectando ao servidor..."
                )
                
                val serverAvailable = api.isServerAvailable()
                if (!serverAvailable) {
                    throw Exception("Servidor não disponível. Verifique se o backend está rodando.")
                }
                
                // 2. Enviar arquivo para processamento
                _uiState.value = _uiState.value.copy(
                    progress = 0.1f,
                    statusMessage = "Enviando arquivo para processamento..."
                )
                
                val startResult = api.startDemucsProcess(
                    audioFile = file,
                    model = _uiState.value.selectedModel,
                    stems = "drums" // Remove drums keeping the rest
                )
                
                val taskId = startResult.getOrElse { 
                    throw Exception("Erro ao iniciar processamento: ${it.message}") 
                }
                
                _uiState.value = _uiState.value.copy(
                    taskId = taskId,
                    progress = 0.2f,
                    statusMessage = "Processando com Demucs..."
                )
                
                // 3. Polling para verificar status
                var completed = false
                while (!completed) {
                    delay(2000) // Verificar a cada 2 segundos
                    
                    val statusResult = api.getDemucsStatus(taskId)
                    val status = statusResult.getOrElse {
                        throw Exception("Erro ao verificar status: ${it.message}")
                    }
                    
                    when (status.status) {
                        "processing" -> {
                            _uiState.value = _uiState.value.copy(
                                progress = 0.2f + (status.progress / 100f * 0.6f),
                                statusMessage = status.message ?: "Processando..."
                            )
                        }
                        "completed" -> {
                            completed = true
                            _uiState.value = _uiState.value.copy(
                                progress = 0.85f,
                                statusMessage = "Download dos stems..."
                            )
                            
                            // Verificar se output_files contém drums e no_drums
                            val outputFiles = status.outputFiles
                            if (outputFiles.isNullOrEmpty()) {
                                throw Exception("Nenhum arquivo de saída encontrado")
                            }
                            if (!outputFiles.containsKey("drums") && !outputFiles.containsKey("no_drums")) {
                                throw Exception("Stems não encontrados nos arquivos de saída")
                            }
                        }
                        "error" -> {
                            throw Exception(status.error ?: status.message ?: "Erro no processamento")
                        }
                    }
                }
                
                // 4. Baixar AMBOS os stems (drums e no_drums)
                _uiState.value = _uiState.value.copy(
                    progress = 0.88f,
                    statusMessage = "Baixando stem de bateria..."
                )
                
                val downloadedStems = mutableMapOf<StemType, File>()
                
                // Baixar drums (bateria isolada)
                val drumsResult = api.downloadStem(taskId, "drums", outputDir)
                val drumsFile = drumsResult.getOrElse {
                    throw Exception("Erro ao baixar stem de bateria: ${it.message}")
                }
                downloadedStems[StemType.DRUMS] = drumsFile
                
                _uiState.value = _uiState.value.copy(
                    progress = 0.92f,
                    statusMessage = "Baixando stem sem bateria..."
                )
                
                // Baixar no_drums (tudo menos bateria)
                val noDrumsResult = api.downloadStem(taskId, "no_drums", outputDir)
                val noDrumsFile = noDrumsResult.getOrElse {
                    throw Exception("Erro ao baixar stem sem bateria: ${it.message}")
                }
                downloadedStems[StemType.NO_DRUMS] = noDrumsFile
                
                _uiState.value = _uiState.value.copy(
                    progress = 0.95f,
                    statusMessage = "Salvando música..."
                )
                
                // 5. Criar e salvar StemmedSong no repositório
                val songId = UUID.randomUUID().toString()
                val songTitle = file.nameWithoutExtension
                    .replace("_", " ")
                    .replace("-", " ")
                    .replaceFirstChar { it.uppercase() }
                
                val stemPaths = mutableMapOf<StemType, String>()
                stemPaths[StemType.ORIGINAL] = file.absolutePath
                downloadedStems.forEach { (type, stemFile) ->
                    stemPaths[type] = stemFile.absolutePath
                }
                
                val stemmedSong = StemmedSong(
                    id = songId,
                    title = songTitle,
                    artist = "Desconhecido",
                    originalFilePath = file.absolutePath,
                    stems = stemPaths
                )
                
                repo.saveSong(stemmedSong)
                
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progress = 1f,
                    statusMessage = "Processamento concluído!",
                    outputFile = noDrumsFile, // Mantido para compatibilidade
                    outputStems = downloadedStems,
                    savedSong = stemmedSong
                )
                
                onComplete(true, "Música '${stemmedSong.title}' salva com ${downloadedStems.size} stems!")
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = e.message
                )
                onComplete(false, "Erro: ${e.message}")
            }
        }
    }
    
    fun cancelProcessing() {
        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            statusMessage = "Cancelado"
        )
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        var name = "audio_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
