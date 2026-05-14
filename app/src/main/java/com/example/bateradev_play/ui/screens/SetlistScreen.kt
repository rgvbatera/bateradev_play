package com.example.bateradev_play.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bateradev_play.data.models.Setlist
import com.example.bateradev_play.data.models.StemmedSong
import com.example.bateradev_play.ui.viewmodels.SetlistItemWithSong
import com.example.bateradev_play.ui.viewmodels.SetlistUiState
import com.example.bateradev_play.ui.viewmodels.SetlistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistScreen(
    onNavigateToSong: (String) -> Unit = {},
    viewModel: SetlistViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // Exibe setlist selecionada ou lista de setlists
    if (uiState.selectedSetlist != null) {
        SetlistDetailScreen(
            uiState = uiState,
            onBack = { viewModel.selectSetlist(null) },
            onPlaySong = { onNavigateToSong(it) },
            onAddSong = { viewModel.showAddSongDialog(true) },
            onRemoveSong = { viewModel.removeSongFromSetlist(it) },
            onReorder = { from, to -> viewModel.reorderSetlist(from, to) },
            onShare = { viewModel.shareSetlist() },
            onEdit = { viewModel.setEditing(true) },
            onSaveEdit = { name, desc -> viewModel.updateSetlist(name, desc) },
            onDelete = { 
                viewModel.deleteSetlist(uiState.selectedSetlist!!)
                viewModel.selectSetlist(null)
            }
        )
    } else {
        SetlistListScreen(
            uiState = uiState,
            onSelectSetlist = { viewModel.selectSetlist(it) },
            onCreateSetlist = { showCreateDialog = true },
            onImportSetlist = { showImportDialog = true },
            onDuplicateSetlist = { viewModel.duplicateSetlist(it) },
            onDeleteSetlist = { viewModel.deleteSetlist(it) }
        )
    }
    
    // Dialog de criar setlist
    if (showCreateDialog) {
        CreateSetlistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc ->
                viewModel.createSetlist(name, desc)
                showCreateDialog = false
            }
        )
    }
    
    // Dialog de importar setlist
    if (showImportDialog) {
        ImportSetlistDialog(
            onDismiss = { showImportDialog = false },
            onImport = { code ->
                viewModel.importSetlistByCode(code)
                showImportDialog = false
            }
        )
    }
    
    // Dialog de compartilhamento
    if (uiState.showShareDialog) {
        ShareSetlistDialog(
            shareCode = uiState.shareCode ?: "",
            onDismiss = { viewModel.dismissShareDialog() }
        )
    }
    
    // Dialog de adicionar música
    if (uiState.showAddSongDialog) {
        AddSongDialog(
            availableSongs = uiState.availableSongs,
            currentSongIds = uiState.selectedSetlist?.songIds ?: emptyList(),
            onDismiss = { viewModel.showAddSongDialog(false) },
            onAddSong = { 
                viewModel.addSongToSetlist(it)
                viewModel.showAddSongDialog(false)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistListScreen(
    uiState: SetlistUiState,
    onSelectSetlist: (Setlist) -> Unit,
    onCreateSetlist: () -> Unit,
    onImportSetlist: () -> Unit,
    onDuplicateSetlist: (Setlist) -> Unit,
    onDeleteSetlist: (Setlist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "📋 Setlists",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Organize suas músicas para shows e estudos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Ações
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCreateSetlist,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nova Setlist")
            }
            
            OutlinedButton(
                onClick = onImportSetlist
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Importar")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lista de setlists
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.setlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhuma setlist ainda",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Crie sua primeira setlist para organizar suas músicas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.setlists) { setlist ->
                    SetlistCard(
                        setlist = setlist,
                        onClick = { onSelectSetlist(setlist) },
                        onDuplicate = { onDuplicateSetlist(setlist) },
                        onDelete = { onDeleteSetlist(setlist) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistCard(
    setlist: Setlist,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = setlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (setlist.isShared) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Compartilhada",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (setlist.description.isNotEmpty()) {
                    Text(
                        text = setlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Text(
                    text = "${setlist.songIds.size} músicas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Duplicar") },
                        onClick = {
                            onDuplicate()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir") },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetlistDetailScreen(
    uiState: SetlistUiState,
    onBack: () -> Unit,
    onPlaySong: (String) -> Unit,
    onAddSong: () -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onSaveEdit: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    val setlist = uiState.selectedSetlist ?: return
    
    var editName by remember(setlist) { mutableStateOf(setlist.name) }
    var editDescription by remember(setlist) { mutableStateOf(setlist.description) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header com botão voltar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            
            if (uiState.isEditing) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Nome") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = setlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (setlist.description.isNotEmpty()) {
                        Text(
                            text = setlist.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (uiState.isEditing) {
                IconButton(onClick = { onSaveEdit(editName, editDescription) }) {
                    Icon(Icons.Default.Check, contentDescription = "Salvar")
                }
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                }
            }
        }
        
        if (uiState.isEditing) {
            OutlinedTextField(
                value = editDescription,
                onValueChange = { editDescription = it },
                label = { Text("Descrição") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botão de adicionar música
        OutlinedButton(
            onClick = onAddSong,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Adicionar Música")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lista de músicas
        if (uiState.setlistItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nenhuma música na setlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.setlistItems) { index, item ->
                    SetlistSongCard(
                        index = index + 1,
                        item = item,
                        onPlay = { onPlaySong(item.item.songId) },
                        onRemove = { onRemoveSong(item.item.songId) },
                        onMoveUp = if (index > 0) {{ onReorder(index, index - 1) }} else null,
                        onMoveDown = if (index < uiState.setlistItems.size - 1) {{ onReorder(index, index + 1) }} else null
                    )
                }
            }
        }
        
        // Botão de deletar (quando editando)
        if (uiState.isEditing) {
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Excluir Setlist")
            }
        }
    }
}

@Composable
fun SetlistSongCard(
    index: Int,
    item: SetlistItemWithSong,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Número
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info da música
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.song?.title ?: "Música não encontrada",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                item.song?.let { song ->
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Controles de reordenação
            Column {
                onMoveUp?.let {
                    IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Mover para cima")
                    }
                }
                onMoveDown?.let {
                    IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Mover para baixo")
                    }
                }
            }
            
            // Botão de play
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Tocar")
            }
            
            // Botão de remover
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remover",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ==================== Dialogs ====================

@Composable
fun CreateSetlistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Setlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description) },
                enabled = name.isNotBlank()
            ) {
                Text("Criar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ImportSetlistDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar Setlist") },
        text = {
            Column {
                Text(
                    text = "Digite o código de compartilhamento da setlist:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Código") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Ex: ABCD1234") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(code) },
                enabled = code.length >= 8
            ) {
                Text("Importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun ShareSetlistDialog(
    shareCode: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartilhar Setlist") },
        text = {
            Column {
                Text(
                    text = "Compartilhe este código com sua banda ou alunos:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = shareCode,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(shareCode))
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Quem tiver este código pode importar a setlist em seu app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongDialog(
    availableSongs: List<StemmedSong>,
    currentSongIds: List<String>,
    onDismiss: () -> Unit,
    onAddSong: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar Música") },
        text = {
            if (availableSongs.isEmpty()) {
                Text("Nenhuma música disponível. Processe algumas músicas primeiro.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableSongs.filter { it.id !in currentSongIds }) { song ->
                        Card(
                            onClick = { onAddSong(song.id) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Adicionar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
