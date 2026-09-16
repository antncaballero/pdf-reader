package com.acaba.pdfreader.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.io.File

data class ImportedDocument(
    val entity: PdfDocumentEntity,
    val isAvailable: Boolean,
)

class DocumentRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val resolver: ContentResolver = context.contentResolver

    fun observeDocuments(): Flow<List<PdfDocumentEntity>> = database.documentDao().observeAll()

    fun observeStrokes(documentId: String): Flow<List<HighlightStrokeEntity>> =
        database.strokeDao().observeForDocument(documentId)

    suspend fun findDocument(id: String): PdfDocumentEntity? = database.documentDao().findById(id)

    suspend fun pendingStrokes(documentId: String): List<HighlightStrokeEntity> =
        database.strokeDao().pendingForDocument(documentId, CURRENT_APPEARANCE_VERSION)

    suspend fun allStrokes(documentId: String): List<HighlightStrokeEntity> =
        database.strokeDao().allForDocument(documentId)

    suspend fun import(uri: Uri): PdfDocumentEntity {
        val uriString = uri.toString()
        val existing = database.documentDao().findByUri(uriString)
        val metadata = queryMetadata(uri)
        val canWrite = supportsWrite(uri)
        val document = (existing ?: PdfDocumentEntity(
            uri = uriString,
            displayName = metadata.name,
            sizeBytes = metadata.size,
            lastModified = metadata.lastModified,
            canWrite = canWrite,
        )).copy(
            displayName = metadata.name,
            sizeBytes = metadata.size,
            lastModified = metadata.lastModified,
            importedAt = System.currentTimeMillis(),
            canWrite = canWrite,
        )
        database.documentDao().upsert(document)
        return document
    }

    suspend fun addStroke(stroke: HighlightStrokeEntity) {
        database.strokeDao().insert(stroke)
    }

    suspend fun markStroke(annotationId: String, state: SyncState, error: String? = null) {
        database.strokeDao().updateState(annotationId, state, error)
    }

    suspend fun markStrokesForDeletion(annotationIds: Collection<String>) {
        if (annotationIds.isEmpty()) return
        database.strokeDao().markDeletePending(annotationIds.toList())
    }

    suspend fun markStrokeSynced(annotationId: String) {
        database.strokeDao().markSynced(annotationId, CURRENT_APPEARANCE_VERSION)
    }

    suspend fun completeStrokeDeletion(annotationId: String) {
        database.strokeDao().deleteCompleted(annotationId)
    }

    suspend fun updateReadingProgress(documentId: String, pageIndex: Int, pageCount: Int) {
        if (pageCount <= 0) return
        database.documentDao().updateReadingProgress(
            documentId = documentId,
            pageIndex = pageIndex.coerceIn(0, pageCount - 1),
            pageCount = pageCount,
        )
    }

    suspend fun removeDocument(id: String) {
        val document = database.documentDao().findById(id) ?: return
        check(pendingStrokes(id).isEmpty()) { "Hay subrayados pendientes de sincronizar" }
        database.withTransaction { database.documentDao().deleteById(id) }
        runCatching {
            resolver.releasePersistableUriPermission(
                Uri.parse(document.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun openReadDescriptor(document: PdfDocumentEntity) =
        resolver.openFileDescriptor(Uri.parse(document.uri), "r")

    fun uriFor(document: PdfDocumentEntity): Uri = Uri.parse(document.uri)

    fun copyToFile(document: PdfDocumentEntity, target: File) {
        resolver.openInputStream(uriFor(document))?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("No se puede leer el PDF")
    }

    fun replaceWithFile(document: PdfDocumentEntity, source: File, recovery: File) {
        try {
            writeFile(document, source)
        } catch (writeError: Throwable) {
            val recoveryError = runCatching { writeFile(document, recovery) }.exceptionOrNull()
            if (recoveryError != null) writeError.addSuppressed(recoveryError)
            throw writeError
        }
    }

    private fun writeFile(document: PdfDocumentEntity, source: File) {
        val descriptor = resolver.openFileDescriptor(uriFor(document), "rwt")
            ?: error("El proveedor no permite escribir el PDF")
        source.inputStream().use { input ->
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun queryMetadata(uri: Uri): Metadata {
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "documento.pdf"
        val cursor: Cursor? = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null,
        )
        cursor.use { result ->
            if (result != null && result.moveToFirst()) {
                val name = result.getStringOrNull(result.getColumnIndex(OpenableColumns.DISPLAY_NAME)) ?: fallbackName
                val size = result.getLongOrNull(result.getColumnIndex(OpenableColumns.SIZE)) ?: 0L
                val modified = result.getLongOrNull(
                    result.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                ) ?: 0L
                return Metadata(name, size, modified)
            }
        }
        return Metadata(fallbackName, 0L, 0L)
    }

    private fun supportsWrite(uri: Uri): Boolean {
        val cursor = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
        cursor.use { result ->
            if (result != null && result.moveToFirst()) {
                val index = result.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                if (index >= 0) {
                    return result.getInt(index) and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
                }
            }
        }
        return runCatching {
            resolver.openFileDescriptor(uri, "rw")?.use { true } ?: false
        }.getOrDefault(false)
    }

    private data class Metadata(val name: String, val size: Long, val lastModified: Long)

    companion object {
        const val CURRENT_APPEARANCE_VERSION = 1
    }
}

private fun Cursor.getStringOrNull(index: Int): String? = if (index >= 0 && !isNull(index)) getString(index) else null
private fun Cursor.getLongOrNull(index: Int): Long? = if (index >= 0 && !isNull(index)) getLong(index) else null
