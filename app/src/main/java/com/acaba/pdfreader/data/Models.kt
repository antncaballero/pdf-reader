package com.acaba.pdfreader.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

enum class SyncState { PENDING, SYNCED, ERROR }

data class StrokePoint(val x: Float, val y: Float)

@Entity(
    tableName = "pdf_documents",
    indices = [Index(value = ["uri"], unique = true)],
)
data class PdfDocumentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val importedAt: Long = System.currentTimeMillis(),
    val canWrite: Boolean,
    @ColumnInfo(defaultValue = "0") val lastPageIndex: Int = 0,
    @ColumnInfo(defaultValue = "0") val pageCount: Int = 0,
)

@Entity(
    tableName = "highlight_strokes",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId"), Index(value = ["documentId", "annotationId"], unique = true)],
)
data class HighlightStrokeEntity(
    @PrimaryKey val annotationId: String = UUID.randomUUID().toString(),
    val documentId: String,
    val pageIndex: Int,
    val colorArgb: Int,
    val widthPoints: Float,
    val points: List<StrokePoint>,
    val syncState: SyncState = SyncState.PENDING,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

class RoomConverters {
    @TypeConverter
    fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun pointsToBlob(points: List<StrokePoint>): ByteArray {
        val bytes = ByteArray(points.size * 8)
        points.forEachIndexed { index, point ->
            writeFloat(bytes, index * 8, point.x)
            writeFloat(bytes, index * 8 + 4, point.y)
        }
        return bytes
    }

    @TypeConverter
    fun blobToPoints(bytes: ByteArray): List<StrokePoint> {
        if (bytes.isEmpty()) return emptyList()
        return buildList(bytes.size / 8) {
            var offset = 0
            while (offset + 7 < bytes.size) {
                add(StrokePoint(readFloat(bytes, offset), readFloat(bytes, offset + 4)))
                offset += 8
            }
        }
    }

    private fun writeFloat(target: ByteArray, offset: Int, value: Float) {
        val bits = value.toBits()
        target[offset] = bits.toByte()
        target[offset + 1] = (bits shr 8).toByte()
        target[offset + 2] = (bits shr 16).toByte()
        target[offset + 3] = (bits shr 24).toByte()
    }

    private fun readFloat(source: ByteArray, offset: Int): Float {
        val bits = (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            (source[offset + 3].toInt() shl 24)
        return Float.fromBits(bits)
    }
}
