package com.example.bateradev_play.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bateradev_play.data.models.*
import com.example.bateradev_play.ui.viewmodels.PracticeUiState
import com.example.bateradev_play.ui.viewmodels.PracticeViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    songId: String? = null,
    viewModel: PracticeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showMetronomeSheet by remember { mutableStateOf(false) }
    var showStemMixerSheet by remember { mutableStateOf(false) }
    var showLoopsSheet by remember { mutableStateOf(false) }
    var showSectionsSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSongPickerSheet by remember { mutableStateOf(false) }
    
    // Launcher para seleção de arquivo de áudio do sistema
    val audioFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Copiar arquivo para cache e carregar
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val fileName = it.lastPathSegment ?: "audio.mp3"
                val tempFile = File(context.cacheDir, fileName)
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.loadAudioFile(context, tempFile)
                showSongPickerSheet = false
            } catch (e: Exception) {
                // Erro ao carregar arquivo
            }
        }
    }
    
    // Inicializar ViewModel
    LaunchedEffect(Unit) {
        viewModel.initialize(context, songId)
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Header com info da música
            SongInfoHeader(
                uiState = uiState,
                onSelectFile = { showSongPickerSheet = true }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Waveform e posição
            WaveformSection(
                uiState = uiState,
                onSeek = { viewModel.seekTo(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Controles principais de reprodução
            PlaybackControls(
                uiState = uiState,
                onPlayPause = { viewModel.togglePlayPause() },
                onStop = { viewModel.stop() },
                onSeekBack = { viewModel.seekBy(-5000) },
                onSeekForward = { viewModel.seekBy(5000) },
                onPlayWithCountIn = { viewModel.playWithCountIn() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Barra de ferramentas rápidas
            QuickToolsBar(
                uiState = uiState,
                onMetronomeClick = { showMetronomeSheet = true },
                onMixerClick = { showStemMixerSheet = true },
                onLoopClick = { showLoopsSheet = true },
                onSectionsClick = { showSectionsSheet = true },
                onSpeedClick = { showSpeedSheet = true }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Presets de backing track
            BackingTrackPresets(
                activePreset = uiState.activePreset,
                hasSongLoaded = uiState.currentSong != null,
                hasProcessedStems = uiState.hasProcessedStems,
                onPresetSelect = { viewModel.applyPreset(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Seções da música (se disponível)
            if (uiState.sections.isNotEmpty()) {
                SectionsRow(
                    sections = uiState.sections,
                    currentPosition = uiState.currentPositionMs,
                    onSectionClick = { viewModel.seekToSection(it) },
                    onLoopSection = { viewModel.loopSection(it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Acordes (se disponível)
            if (uiState.chords.isNotEmpty()) {
                CurrentChordDisplay(
                    currentChord = uiState.currentChord,
                    detectedKey = uiState.detectedKey
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Indicador visual do metrônomo
            if (uiState.isMetronomeRunning) {
                MetronomeBeatIndicator(
                    currentBeat = uiState.currentBeat,
                    beatsPerBar = uiState.metronomeSettings.beatsPerBar,
                    currentBar = uiState.currentBar,
                    isInCountIn = uiState.isInCountIn,
                    countInRemaining = uiState.countInRemaining
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Loop ativo
            uiState.activeLoop?.let { loop ->
                ActiveLoopCard(
                    loop = loop,
                    enabled = uiState.loopEnabled,
                    onToggle = { viewModel.toggleLoopEnabled() },
                    onDelete = { viewModel.setActiveLoop(null) }
                )
            }
        }
    }
    
    // Bottom Sheets
    if (showMetronomeSheet) {
        MetronomeBottomSheet(
            settings = uiState.metronomeSettings,
            isRunning = uiState.isMetronomeRunning,
            currentBeat = uiState.currentBeat,
            detectedBpm = uiState.detectedBpm,
            onDismiss = { showMetronomeSheet = false },
            onToggle = { viewModel.toggleMetronome() },
            onBpmChange = { viewModel.setMetronomeBpm(it) },
            onBeatsPerBarChange = { viewModel.setMetronomeBeatsPerBar(it) },
            onVolumeChange = { viewModel.setMetronomeVolume(it) },
            onCountInChange = { viewModel.setCountInBeats(it) },
            onSubdivisionsChange = { viewModel.setMetronomeSubdivisions(it) },
            onTapTempo = { viewModel.tapTempo() },
            onSyncWithSong = { viewModel.syncMetronomeWithSong() }
        )
    }
    
    if (showStemMixerSheet) {
        StemMixerBottomSheet(
            loadedStems = uiState.loadedStems,
            stemVolumes = uiState.stemVolumes,
            stemMuted = uiState.stemMuted,
            soloStem = uiState.soloStem,
            onDismiss = { showStemMixerSheet = false },
            onVolumeChange = { stem, vol -> viewModel.setStemVolume(stem, vol) },
            onMuteToggle = { viewModel.toggleStemMute(it) },
            onSolo = { viewModel.soloStem(it) },
            onReset = { viewModel.resetStemVolumes() }
        )
    }
    
    if (showLoopsSheet) {
        LoopsBottomSheet(
            loops = uiState.loops,
            activeLoop = uiState.activeLoop,
            isSettingLoop = uiState.isSettingLoopStart,
            currentPosition = uiState.currentPositionMs,
            duration = uiState.durationMs,
            onDismiss = { showLoopsSheet = false },
            onStartLoop = { viewModel.startSettingLoop() },
            onEndLoop = { viewModel.setLoopEnd() },
            onCancelLoop = { viewModel.cancelLoopSetting() },
            onSelectLoop = { viewModel.setActiveLoop(it) },
            onDeleteLoop = { viewModel.deleteLoop(it) }
        )
    }
    
    if (showSpeedSheet) {
        SpeedPitchBottomSheet(
            speed = uiState.playbackSpeed,
            pitchSemitones = uiState.pitchSemitones,
            onDismiss = { showSpeedSheet = false },
            onSpeedChange = { viewModel.setPlaybackSpeed(it) },
            onPitchChange = { viewModel.setPitchSemitones(it) },
            onReset = { viewModel.resetSpeedAndPitch() }
        )
    }
    
    if (showSongPickerSheet) {
        SongPickerBottomSheet(
            downloadedFiles = viewModel.getDownloadedFiles(),
            processedSongs = viewModel.getAvailableSongs(),
            onDismiss = { showSongPickerSheet = false },
            onSelectFile = { file ->
                viewModel.loadAudioFile(context, file)
                showSongPickerSheet = false
            },
            onSelectSong = { song ->
                viewModel.loadSong(song)
                showSongPickerSheet = false
            },
            onBrowseDevice = { 
                audioFileLauncher.launch("audio/*")
            }
        )
    }
}

// ==================== Componentes ====================

@Composable
fun SongInfoHeader(
    uiState: PracticeUiState,
    onSelectFile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        if (uiState.currentSong == null) {
            // Sem música - mostrar botão de seleção
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Nenhuma música selecionada",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Selecione um arquivo de áudio para começar a praticar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSelectFile) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Selecionar Música")
                }
            }
        } else {
            // Com música - mostrar informações
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícone/Thumbnail
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🥁", fontSize = 28.sp)
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.currentSong.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    uiState.currentSong.artist?.let { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        uiState.detectedBpm?.let { bpm ->
                            AssistChip(
                                onClick = {},
                                label = { Text("$bpm BPM") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        
                        uiState.detectedKey?.let { key ->
                            AssistChip(
                                onClick = {},
                                label = { Text(key) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                
                // Botão para trocar música
                IconButton(onClick = onSelectFile) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Trocar música")
                }
            }
        }
    }
}

@Composable
fun WaveformSection(
    uiState: PracticeUiState,
    onSeek: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Waveform placeholder (seria renderizado com Canvas real)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Indicador de posição
                val progress = if (uiState.durationMs > 0) {
                    uiState.currentPositionMs.toFloat() / uiState.durationMs
                } else 0f
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                )
                
                // Marcadores de loop
                uiState.activeLoop?.let { loop ->
                    if (uiState.durationMs > 0) {
                        val startFraction = loop.startMs.toFloat() / uiState.durationMs
                        val endFraction = loop.endMs.toFloat() / uiState.durationMs
                        
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth((endFraction - startFraction))
                                .offset(x = (startFraction * 300).dp) // Simplificado
                                .background(Color(0x40FF9800))
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Slider de posição
            Slider(
                value = if (uiState.durationMs > 0) {
                    uiState.currentPositionMs.toFloat() / uiState.durationMs
                } else 0f,
                onValueChange = { 
                    onSeek((it * uiState.durationMs).toLong())
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Tempo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(uiState.currentPositionMs),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatTime(uiState.durationMs),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun PlaybackControls(
    uiState: PracticeUiState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayWithCountIn: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Stop
        IconButton(onClick = onStop) {
            Icon(Icons.Default.Stop, contentDescription = "Parar")
        }
        
        // Seek back 5s
        IconButton(onClick = onSeekBack) {
            Icon(Icons.Default.Replay5, contentDescription = "-5s")
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Play/Pause principal
        FloatingActionButton(
            onClick = onPlayPause,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (uiState.isPlaying) "Pausar" else "Tocar",
                modifier = Modifier.size(32.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Seek forward 5s
        IconButton(onClick = onSeekForward) {
            Icon(Icons.Default.Forward5, contentDescription = "+5s")
        }
        
        // Play with count-in
        IconButton(onClick = onPlayWithCountIn) {
            Icon(Icons.Default.Timer, contentDescription = "Tocar com contagem")
        }
    }
    
    // Indicador de count-in
    AnimatedVisibility(
        visible = uiState.isInCountIn,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = uiState.countInRemaining.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun QuickToolsBar(
    uiState: PracticeUiState,
    onMetronomeClick: () -> Unit,
    onMixerClick: () -> Unit,
    onLoopClick: () -> Unit,
    onSectionsClick: () -> Unit,
    onSpeedClick: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            QuickToolChip(
                icon = Icons.Default.Timer,
                label = "${uiState.metronomeSettings.bpm} BPM",
                isActive = uiState.isMetronomeRunning,
                onClick = onMetronomeClick
            )
        }
        
        item {
            QuickToolChip(
                icon = Icons.Default.Tune,
                label = "Mixer",
                onClick = onMixerClick
            )
        }
        
        item {
            QuickToolChip(
                icon = Icons.Default.Loop,
                label = if (uiState.loopEnabled) "Loop ON" else "Loop",
                isActive = uiState.loopEnabled,
                onClick = onLoopClick
            )
        }
        
        item {
            QuickToolChip(
                icon = Icons.Default.ViewList,
                label = "Seções",
                onClick = onSectionsClick
            )
        }
        
        item {
            QuickToolChip(
                icon = Icons.Default.Speed,
                label = "${(uiState.playbackSpeed * 100).toInt()}%",
                isActive = uiState.playbackSpeed != 1f || uiState.pitchSemitones != 0,
                onClick = onSpeedClick
            )
        }
    }
}

@Composable
fun QuickToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    FilterChip(
        onClick = onClick,
        label = { Text(label) },
        selected = isActive,
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
fun BackingTrackPresets(
    activePreset: BackingTrackPreset?,
    hasSongLoaded: Boolean,
    hasProcessedStems: Boolean,
    onPresetSelect: (BackingTrackPreset) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Backing Tracks",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            when {
                !hasSongLoaded -> {
                    Text(
                        text = "(selecione uma música)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                !hasProcessedStems -> {
                    Text(
                        text = "(processe a música primeiro)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(BackingTrackPreset.PRESETS) { preset ->
                PresetCard(
                    preset = preset,
                    isActive = activePreset?.id == preset.id,
                    enabled = hasSongLoaded && hasProcessedStems,
                    onClick = { if (hasSongLoaded && hasProcessedStems) onPresetSelect(preset) }
                )
            }
        }
    }
}

@Composable
fun PresetCard(
    preset: BackingTrackPreset,
    isActive: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = preset.icon,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun SectionsRow(
    sections: List<SongSection>,
    currentPosition: Long,
    onSectionClick: (SongSection) -> Unit,
    onLoopSection: (SongSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Seções",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sections) { section ->
                val isCurrentSection = currentPosition >= section.startMs && 
                                       currentPosition < section.endMs
                
                SectionChip(
                    section = section,
                    isActive = isCurrentSection,
                    onClick = { onSectionClick(section) },
                    onLongClick = { onLoopSection(section) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionChip(
    section: SongSection,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                Color(section.color.toInt()).copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isActive) {
            BorderStroke(2.dp, Color(section.color.toInt()))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = section.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = "${formatTime(section.startMs)} - ${formatTime(section.endMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CurrentChordDisplay(
    currentChord: ChordMarker?,
    detectedKey: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Acorde Atual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currentChord?.chordName ?: "-",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            detectedKey?.let { key ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Tom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = key,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun MetronomeBeatIndicator(
    currentBeat: Int,
    beatsPerBar: Int,
    currentBar: Int,
    isInCountIn: Boolean,
    countInRemaining: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isInCountIn) {
                Text(
                    text = "Count-in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = countInRemaining.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "Compasso $currentBar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (beat in 1..beatsPerBar) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (beat == currentBeat) {
                                        if (beat == 1) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = beat.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (beat == currentBeat) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveLoopCard(
    loop: LoopMarker,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Loop,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = loop.name.ifEmpty { "Loop" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${formatTime(loop.startMs)} → ${formatTime(loop.endMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row {
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle() }
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Close, contentDescription = "Remover loop")
                }
            }
        }
    }
}

// ==================== Utility ====================

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ==================== Song Picker ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerBottomSheet(
    downloadedFiles: List<File>,
    processedSongs: List<StemmedSong>,
    onDismiss: () -> Unit,
    onSelectFile: (File) -> Unit,
    onSelectSong: (StemmedSong) -> Unit,
    onBrowseDevice: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Selecionar Música",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Botão para buscar no dispositivo
            OutlinedButton(
                onClick = onBrowseDevice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buscar no Dispositivo")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Músicas processadas (com stems)
            if (processedSongs.isNotEmpty()) {
                Text(
                    text = "Músicas Processadas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                processedSongs.take(5).forEach { song ->
                    SongPickerItem(
                        title = song.title,
                        subtitle = song.artist ?: "Desconhecido",
                        icon = Icons.Default.Album,
                        hasStems = true,
                        onClick = { onSelectSong(song) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Downloads do app
            if (downloadedFiles.isNotEmpty()) {
                Text(
                    text = "Downloads do App",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                downloadedFiles.take(10).forEach { file ->
                    val isProcessed = file.parentFile?.name == "NoDrums"
                    SongPickerItem(
                        title = file.nameWithoutExtension,
                        subtitle = if (isProcessed) "Sem bateria" else "Download",
                        icon = if (isProcessed) Icons.Default.MusicOff else Icons.Default.MusicNote,
                        hasStems = false,
                        onClick = { onSelectFile(file) }
                    )
                }
            }
            
            // Mensagem se não houver arquivos
            if (downloadedFiles.isEmpty() && processedSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nenhuma música baixada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Use a tela Download para baixar músicas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SongPickerItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hasStems: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (hasStems) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (hasStems) {
                AssistChip(
                    onClick = {},
                    label = { Text("Stems") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
