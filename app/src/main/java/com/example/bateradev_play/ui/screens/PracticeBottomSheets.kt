package com.example.bateradev_play.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bateradev_play.data.models.*

// ==================== Metrônomo Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeBottomSheet(
    settings: MetronomeSettings,
    isRunning: Boolean,
    currentBeat: Int,
    detectedBpm: Int?,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onBeatsPerBarChange: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCountInChange: (Int) -> Unit,
    onSubdivisionsChange: (Int) -> Unit,
    onTapTempo: () -> Int?,
    onSyncWithSong: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎵 Metrônomo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                FilledTonalButton(
                    onClick = onToggle,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isRunning) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRunning) "Parar" else "Iniciar")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // BPM
            Text(
                text = "BPM: ${settings.bpm}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onBpmChange(settings.bpm - 5) }) {
                    Icon(Icons.Default.Remove, contentDescription = "-5")
                }
                
                Slider(
                    value = settings.bpm.toFloat(),
                    onValueChange = { onBpmChange(it.toInt()) },
                    valueRange = 30f..300f,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { onBpmChange(settings.bpm + 5) }) {
                    Icon(Icons.Default.Add, contentDescription = "+5")
                }
            }
            
            // Botões de BPM rápido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(60, 80, 100, 120, 140, 160).forEach { bpm ->
                    FilterChip(
                        onClick = { onBpmChange(bpm) },
                        label = { Text("$bpm") },
                        selected = settings.bpm == bpm,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tap Tempo e Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onTapTempo() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.TouchApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tap Tempo")
                }
                
                detectedBpm?.let { bpm ->
                    OutlinedButton(
                        onClick = onSyncWithSong,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync ($bpm)")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Compasso
            Text(
                text = "Compasso: ${settings.beatsPerBar}/4",
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 3, 4, 5, 6, 7).forEach { beats ->
                    FilterChip(
                        onClick = { onBeatsPerBarChange(beats) },
                        label = { Text("$beats/4") },
                        selected = settings.beatsPerBar == beats,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Count-in
            Text(
                text = "Count-in: ${settings.countInBeats} cliques",
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 1, 2, 4, 8, 16).forEach { count ->
                    FilterChip(
                        onClick = { onCountInChange(count) },
                        label = { Text(if (count == 0) "Off" else "$count") },
                        selected = settings.countInBeats == count,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Subdivisões
            Text(
                text = "Subdivisões",
                style = MaterialTheme.typography.titleSmall
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    1 to "Nenhuma",
                    2 to "Colcheias",
                    3 to "Tercinas",
                    4 to "Semicolcheias"
                ).forEach { (sub, label) ->
                    FilterChip(
                        onClick = { onSubdivisionsChange(sub) },
                        label = { Text(label) },
                        selected = settings.subdivisions == sub,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Volume
            Text(
                text = "Volume: ${(settings.volume * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall
            )
            
            Slider(
                value = settings.volume,
                onValueChange = { onVolumeChange(it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== Stem Mixer Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StemMixerBottomSheet(
    loadedStems: Set<StemType>,
    stemVolumes: Map<StemType, Float>,
    stemMuted: Map<StemType, Boolean>,
    soloStem: StemType?,
    onDismiss: () -> Unit,
    onVolumeChange: (StemType, Float) -> Unit,
    onMuteToggle: (StemType) -> Unit,
    onSolo: (StemType?) -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎚️ Mixer de Stems",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Lista de stems
            val stemOrder = listOf(
                StemType.DRUMS to "🥁 Bateria",
                StemType.BASS to "🎸 Baixo",
                StemType.VOCALS to "🎤 Vocais",
                StemType.OTHER to "🎹 Outros"
            )
            
            stemOrder.forEach { (stemType, label) ->
                if (loadedStems.contains(stemType)) {
                    StemMixerRow(
                        label = label,
                        volume = stemVolumes[stemType] ?: 1f,
                        isMuted = stemMuted[stemType] ?: false,
                        isSolo = soloStem == stemType,
                        onVolumeChange = { onVolumeChange(stemType, it) },
                        onMuteToggle = { onMuteToggle(stemType) },
                        onSoloToggle = { 
                            onSolo(if (soloStem == stemType) null else stemType)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            if (loadedStems.isEmpty()) {
                Text(
                    text = "Nenhum stem carregado. Processe uma música primeiro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StemMixerRow(
    label: String,
    volume: Float,
    isMuted: Boolean,
    isSolo: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSolo) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Row {
                    // Mute
                    FilterChip(
                        onClick = onMuteToggle,
                        label = { Text("M") },
                        selected = isMuted,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    // Solo
                    FilterChip(
                        onClick = onSoloToggle,
                        label = { Text("S") },
                        selected = isSolo,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isMuted || volume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isMuted) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    enabled = !isMuted,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

// ==================== Loops Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoopsBottomSheet(
    loops: List<LoopMarker>,
    activeLoop: LoopMarker?,
    isSettingLoop: Boolean,
    currentPosition: Long,
    duration: Long,
    onDismiss: () -> Unit,
    onStartLoop: () -> Unit,
    onEndLoop: () -> Unit,
    onCancelLoop: () -> Unit,
    onSelectLoop: (LoopMarker?) -> Unit,
    onDeleteLoop: (LoopMarker) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "🔁 Loops A-B",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Criar novo loop
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Criar Loop",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Posição atual: ${formatTime(currentPosition)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (isSettingLoop) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onEndLoop,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Marcar FIM (B)")
                            }
                            
                            OutlinedButton(
                                onClick = onCancelLoop
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    } else {
                        Button(
                            onClick = onStartLoop,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Marcar INÍCIO (A)")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Lista de loops salvos
            Text(
                text = "Loops Salvos",
                style = MaterialTheme.typography.titleSmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (loops.isEmpty()) {
                Text(
                    text = "Nenhum loop salvo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(loops) { loop ->
                        LoopItem(
                            loop = loop,
                            isActive = activeLoop?.id == loop.id,
                            onSelect = { onSelectLoop(if (activeLoop?.id == loop.id) null else loop) },
                            onDelete = { onDeleteLoop(loop) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoopItem(
    loop: LoopMarker,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Icon(
                        Icons.Default.Loop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Column {
                    Text(
                        text = loop.name.ifEmpty { "Loop" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "${formatTime(loop.startMs)} → ${formatTime(loop.endMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Excluir",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== Speed/Pitch Bottom Sheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedPitchBottomSheet(
    speed: Float,
    pitchSemitones: Int,
    onDismiss: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Int) -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚡ Velocidade e Tom",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onReset) {
                    Text("Reset")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Velocidade
            Text(
                text = "Velocidade: ${(speed * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Slider(
                value = speed,
                onValueChange = { onSpeedChange(it) },
                valueRange = 0.25f..2f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Presets de velocidade
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.5f to "50%", 0.75f to "75%", 1f to "100%", 1.25f to "125%", 1.5f to "150%").forEach { (value, label) ->
                    FilterChip(
                        onClick = { onSpeedChange(value) },
                        label = { Text(label) },
                        selected = speed == value,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Pitch
            val pitchText = when {
                pitchSemitones > 0 -> "+$pitchSemitones"
                else -> "$pitchSemitones"
            }
            
            Text(
                text = "Tom: $pitchText semitons",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onPitchChange(pitchSemitones - 1) }) {
                    Icon(Icons.Default.Remove, contentDescription = "-1")
                }
                
                Slider(
                    value = pitchSemitones.toFloat(),
                    onValueChange = { onPitchChange(it.toInt()) },
                    valueRange = -12f..12f,
                    steps = 23,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { onPitchChange(pitchSemitones + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "+1")
                }
            }
            
            // Presets de pitch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-3 to "-3", 0 to "0", 3 to "+3", 5 to "+5").forEach { (value, label) ->
                    FilterChip(
                        onClick = { onPitchChange(value) },
                        label = { Text(label) },
                        selected = pitchSemitones == value,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Info
            Text(
                text = "💡 Mude a velocidade para praticar em ritmo mais lento. Ajuste o tom se a música estiver em uma tonalidade diferente do original.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
