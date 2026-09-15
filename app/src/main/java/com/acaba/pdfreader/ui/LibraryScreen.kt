package com.acaba.pdfreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FindInPage
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.acaba.pdfreader.R
import com.acaba.pdfreader.data.PdfDocumentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    documents: List<PdfDocumentEntity>,
    error: String?,
    onErrorDismiss: () -> Unit,
    onImport: (Uri) -> Unit,
    onOpen: (PdfDocumentEntity) -> Unit,
    onGoToPage: (PdfDocumentEntity, Int) -> Unit,
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
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { launcher.launch(arrayOf("application/pdf")) },
                shape = CircleShape,
            ) {
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
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(documents, key = { it.id }) { document ->
                    DocumentCard(document, onOpen, onGoToPage, onRemove)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text("Tu biblioteca está vacía", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Pulsa + para añadir tu primer PDF.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    document: PdfDocumentEntity,
    onOpen: (PdfDocumentEntity) -> Unit,
    onGoToPage: (PdfDocumentEntity, Int) -> Unit,
    onRemove: (PdfDocumentEntity) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var goToPageOpen by remember { mutableStateOf(false) }
    val hasPageCount = document.pageCount > 0
    val currentPage = if (hasPageCount) {
        (document.lastPageIndex + 1).coerceIn(1, document.pageCount)
    } else {
        0
    }
    val progress = if (hasPageCount) currentPage.toFloat() / document.pageCount else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpen(document) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 14.dp, end = 4.dp)) {
                    Text(
                        document.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            append(formatBytes(document.sizeBytes))
                            if (!document.canWrite) append("  ·  Solo lectura")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (document.canWrite) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                IconButton(onClick = { goToPageOpen = true }) {
                    Icon(Icons.Default.FindInPage, contentDescription = "Ir a página")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Quitar de la biblioteca") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = { menuOpen = false; confirmRemove = true },
                        )
                    }
                }
            }

            Spacer(Modifier.height(15.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Progreso",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (hasPageCount) "$currentPage/${document.pageCount}" else "0/?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }

    if (goToPageOpen) {
        GoToPageDialog(
            pageCount = document.pageCount.takeIf { it > 0 },
            currentPage = currentPage.coerceAtLeast(1),
            onDismiss = { goToPageOpen = false },
            onConfirm = { pageIndex ->
                goToPageOpen = false
                onGoToPage(document, pageIndex)
            },
        )
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Quitar PDF") },
            text = { Text("Se quitará de la biblioteca, pero el archivo original no se borrará.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = false; onRemove(document) }) {
                    Text("Quitar")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
    else -> "%.1f MB".format(bytes / (1024f * 1024f))
}
