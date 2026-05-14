package com.example.bateradev_play.ui.screens

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bateradev_play.ui.viewmodels.GenerationMode
import com.example.bateradev_play.ui.viewmodels.MusicGenres
import com.example.bateradev_play.ui.viewmodels.MusicGenerationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 🎵 Tela de Geração de Música com IA
 * 
 * Permite:
 * - Gerar backing tracks para prática
 * - Gerar músicas a partir de prompts
 * - Escolher gênero, BPM, tonalidade
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicGenerationScreen(
    viewModel: MusicGenerationViewModel = viewModel(),
    onMusicGenerated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // Player de áudio
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Limpar MediaPlayer quando sair da tela
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }
    
    // Inicializar ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "🎵 Gerar Música com IA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Card explicativo
            InfoCard()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Seletor de modo
            ModeSelector(
                selectedMode = uiState.mode,
                onModeSelected = { viewModel.updateMode(it) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Conteúdo baseado no modo
            when (uiState.mode) {
                GenerationMode.BACKING_TRACK -> {
                    BackingTrackForm(
                        genre = uiState.genre,
                        bpm = uiState.bpm,
                        key = uiState.key,
                        duration = uiState.duration,
                        onGenreChange = { viewModel.updateGenre(it) },
                        onBpmChange = { viewModel.updateBpm(it) },
                        onKeyChange = { viewModel.updateKey(it) },
                        onDurationChange = { viewModel.updateDuration(it) }
                    )
                }
                GenerationMode.FREE_PROMPT -> {
                    FreePromptForm(
                        prompt = uiState.prompt,
                        duration = uiState.duration,
                        onPromptChange = { viewModel.updatePrompt(it) },
                        onDurationChange = { viewModel.updateDuration(it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botão gerar
            GenerateButton(
                isGenerating = uiState.isGenerating,
                onClick = {
                    viewModel.generateMusic { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        if (success && uiState.generatedUrl != null) {
                            onMusicGenerated(uiState.generatedUrl!!)
                        }
                    }
                }
            )
            
            // Loading indicator
            if (uiState.isGenerating) {
                Spacer(modifier = Modifier.height(16.dp))
                LoadingIndicator()
            }
            
            // Resultado
            uiState.generatedUrl?.let { url ->
                Spacer(modifier = Modifier.height(24.dp))
                ResultCard(
                    url = url,
                    isPlaying = isPlaying,
                    onPlayClick = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                if (isPlaying) {
                                    // Parar
                                    mediaPlayer?.stop()
                                    mediaPlayer?.release()
                                    mediaPlayer = null
                                    isPlaying = false
                                } else {
                                    // Tocar
                                    mediaPlayer?.release()
                                    mediaPlayer = MediaPlayer().apply {
                                        setDataSource(url)
                                        prepare()
                                        start()
                                        setOnCompletionListener {
                                            isPlaying = false
                                        }
                                    }
                                    isPlaying = true
                                }
                            } catch (e: Exception) {
                                scope.launch(Dispatchers.Main) {
                                    Toast.makeText(context, "Erro ao reproduzir: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                isPlaying = false
                            }
                        }
                    }
                )
            }
            
            // Erro
            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                ErrorCard(error = error)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Crie músicas com Inteligência Artificial",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gere backing tracks para praticar bateria ou crie músicas completas a partir de uma descrição.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: GenerationMode,
    onModeSelected: (GenerationMode) -> Unit
) {
    Text(
        text = "Modo de Geração",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeCard(
            title = "Backing Track",
            description = "Para praticar bateria",
            icon = "🥁",
            selected = selectedMode == GenerationMode.BACKING_TRACK,
            onClick = { onModeSelected(GenerationMode.BACKING_TRACK) },
            modifier = Modifier.weight(1f)
        )
        
        ModeCard(
            title = "Prompt Livre",
            description = "Descreva a música",
            icon = "✨",
            selected = selectedMode == GenerationMode.FREE_PROMPT,
            onClick = { onModeSelected(GenerationMode.FREE_PROMPT) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(
                2.dp,
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackingTrackForm(
    genre: String,
    bpm: Int,
    key: String,
    duration: Int,
    onGenreChange: (String) -> Unit,
    onBpmChange: (Int) -> Unit,
    onKeyChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit
) {
    // Gênero
    Text(
        text = "Gênero Musical",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = MusicGenres.ALL.find { it.first == genre }?.second ?: genre,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MusicGenres.ALL.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onGenreChange(id)
                        expanded = false
                    }
                )
            }
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // BPM
    Text(
        text = "Tempo (BPM): $bpm",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    
    Slider(
        value = bpm.toFloat(),
        onValueChange = { onBpmChange(it.toInt()) },
        valueRange = 60f..200f,
        steps = 139
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Tonalidade (opcional)
    Text(
        text = "Tonalidade (opcional)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Opção "Auto"
        FilterChip(
            selected = key.isEmpty(),
            onClick = { onKeyChange("") },
            label = { Text("Automático") }
        )
        
        // Opções de tonalidade
        MusicGenres.KEYS.take(6).forEach { k ->
            FilterChip(
                selected = key == k,
                onClick = { onKeyChange(k) },
                label = { Text(k) }
            )
        }
    }
    
    // Duração
    Spacer(modifier = Modifier.height(16.dp))
    
    Text(
        text = "Duração: ${duration}s",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    
    Slider(
        value = duration.toFloat(),
        onValueChange = { onDurationChange(it.toInt()) },
        valueRange = 10f..60f,
        steps = 49
    )
}

@Composable
private fun FreePromptForm(
    prompt: String,
    duration: Int,
    onPromptChange: (String) -> Unit,
    onDurationChange: (Int) -> Unit
) {
    Text(
        text = "Descreva a música desejada",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    OutlinedTextField(
        value = prompt,
        onValueChange = onPromptChange,
        placeholder = { Text("Ex: rock drumless backing track, energetic, 120 bpm") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 5
    )
    
    Spacer(modifier = Modifier.height(8.dp))
    
    // Sugestões
    Text(
        text = "Sugestões:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    val suggestions = listOf(
        "🎸 Rock drumless, 130 bpm",
        "🎷 Jazz quartet without drums, smooth",
        "🕺 Funk groove, guitar and bass",
        "🤘 Metal instrumental, aggressive"
    )
    
    suggestions.forEach { suggestion ->
        TextButton(
            onClick = { onPromptChange(suggestion.drop(2)) }
        ) {
            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Duração
    Text(
        text = "Duração: ${duration}s",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium
    )
    
    Slider(
        value = duration.toFloat(),
        onValueChange = { onDurationChange(it.toInt()) },
        valueRange = 5f..30f,
        steps = 24
    )
}

@Composable
private fun GenerateButton(
    isGenerating: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isGenerating,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Gerando música...")
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Gerar Música",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Isso pode levar alguns segundos...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultCard(
    url: String,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable { onPlayClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Música gerada com sucesso!",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPlaying) "Tocando... (toque para parar)" else "Toque para ouvir",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Parar" else "Tocar",
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
