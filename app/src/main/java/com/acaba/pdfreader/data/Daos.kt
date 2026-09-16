package com.acaba.pdfreader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY importedAt DESC")
    fun observeAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: PdfDocumentEntity)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """UPDATE pdf_documents
           SET lastPageIndex = :pageIndex, pageCount = :pageCount
           WHERE id = :documentId""",
    )
    suspend fun updateReadingProgress(documentId: String, pageIndex: Int, pageCount: Int)
}

@Dao
interface StrokeDao {
    @Query("SELECT * FROM highlight_strokes WHERE documentId = :documentId AND syncState NOT IN ('DELETE_PENDING', 'DELETE_ERROR') ORDER BY createdAt ASC")
    fun observeForDocument(documentId: String): Flow<List<HighlightStrokeEntity>>

    @Query("SELECT * FROM highlight_strokes WHERE documentId = :documentId AND (syncState != 'SYNCED' OR appearanceVersion < :appearanceVersion) ORDER BY createdAt ASC")
    suspend fun pendingForDocument(documentId: String, appearanceVersion: Int): List<HighlightStrokeEntity>

    @Query("SELECT * FROM highlight_strokes WHERE documentId = :documentId ORDER BY createdAt ASC")
    suspend fun allForDocument(documentId: String): List<HighlightStrokeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stroke: HighlightStrokeEntity)

    @Query("UPDATE highlight_strokes SET syncState = :state, lastError = :error WHERE annotationId = :annotationId")
    suspend fun updateState(annotationId: String, state: SyncState, error: String?)

    @Query("UPDATE highlight_strokes SET syncState = 'DELETE_PENDING', lastError = NULL WHERE annotationId IN (:annotationIds)")
    suspend fun markDeletePending(annotationIds: List<String>)

    @Query("UPDATE highlight_strokes SET syncState = :state, lastError = :error WHERE annotationId = :annotationId")
    suspend fun updateDeleteState(annotationId: String, state: SyncState, error: String?)

    @Query("UPDATE highlight_strokes SET syncState = 'SYNCED', lastError = NULL, appearanceVersion = :appearanceVersion WHERE annotationId = :annotationId AND syncState IN ('PENDING', 'ERROR', 'SYNCED')")
    suspend fun markSynced(annotationId: String, appearanceVersion: Int)

    @Query("DELETE FROM highlight_strokes WHERE annotationId = :annotationId AND syncState IN ('DELETE_PENDING', 'DELETE_ERROR')")
    suspend fun deleteCompleted(annotationId: String)

    @Query("DELETE FROM highlight_strokes WHERE annotationId = :annotationId")
    suspend fun delete(annotationId: String)
}
