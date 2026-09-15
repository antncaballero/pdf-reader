package com.acaba.pdfreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acaba.pdfreader.LibraryViewModel

@Composable
fun PdfReaderApp() {
    val libraryViewModel: LibraryViewModel = viewModel()
    val context = LocalContext.current
    val documents by libraryViewModel.documents.collectAsState()
    val error by libraryViewModel.error.collectAsState()
    var selectedDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var requestedPageIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val selected = documents.firstOrNull { it.id == selectedDocumentId }

    if (selected == null) {
        LibraryScreen(
            documents = documents,
            error = error,
            onErrorDismiss = libraryViewModel::clearError,
            onImport = { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                libraryViewModel.importDocument(uri) { selectedDocumentId = it.id }
            },
            onOpen = {
                requestedPageIndex = null
                selectedDocumentId = it.id
            },
            onGoToPage = { document, pageIndex ->
                requestedPageIndex = pageIndex
                selectedDocumentId = document.id
            },
            onRemove = libraryViewModel::removeDocument,
        )
    } else {
        BackHandler { selectedDocumentId = null }
        ReaderScreen(
            document = selected,
            initialPageIndex = requestedPageIndex,
            onBack = { selectedDocumentId = null },
        )
    }
}
