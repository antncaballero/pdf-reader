package com.acaba.pdfreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.acaba.pdfreader.R
import com.acaba.pdfreader.data.PdfDocumentEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    documents: List<PdfDocumentEntity>,
    error: String?,
    onErrorDismiss: () -> Unit,
    onImport: (Uri) -> Unit,
    onOpen: (PdfDocumentEntity) -> Unit,
    onRemove: (PdfDocumentEntity) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(error)
            onErrorDismiss()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis PDFs") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch(arrayOf("application/pdf")) }) {
                Icon(Icons.Default.Add, contentDescription = "Importar PDF")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (documents.isEmpty()) {
            EmptyLibrary(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(documents, key = { it.id }) { document ->
                    DocumentCard(document, onOpen, onRemove)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Aún no tienes PDFs", style = MaterialTheme.typography.titleMedium)
            Text("Pulsa + para importar un documento desde el teléfono.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocumentCard(
    document: PdfDocumentEntity,
    onOpen: (PdfDocumentEntity) -> Unit,
    onRemove: (PdfDocumentEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(document) },
        colors = CardDefaults.cardColors(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(document.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(document.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!document.canWrite) {
                    Text("Solo lectura", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Quitar de la biblioteca") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                        onClick = { menuOpen = false; confirmRemove = true },
                    )
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Quitar PDF") },
            text = { Text("Se quitará de la biblioteca, pero el archivo original no se borrará.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = false; onRemove(document) }) { Text("Quitar") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}
