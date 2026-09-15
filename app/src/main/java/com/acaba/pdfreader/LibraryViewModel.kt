package com.acaba.pdfreader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.acaba.pdfreader.data.PdfDocumentEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PdfReaderApplication
    private val repository = app.repository

    val documents: StateFlow<List<PdfDocumentEntity>> = repository.observeDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun importDocument(uri: android.net.Uri, onImported: (PdfDocumentEntity) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.import(uri) }
                .onSuccess(onImported)
                .onFailure { _error.value = it.message ?: getApplication<Application>().getString(R.string.import_error) }
        }
    }

    fun removeDocument(document: PdfDocumentEntity, onRemoved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.removeDocument(document.id) }
                .onSuccess { onRemoved() }
                .onFailure { _error.value = it.message ?: getApplication<Application>().getString(R.string.remove_error) }
        }
    }

    fun clearError() { _error.value = null }
}
