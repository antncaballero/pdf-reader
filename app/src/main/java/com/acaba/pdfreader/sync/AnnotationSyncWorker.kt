package com.acaba.pdfreader.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.acaba.pdfreader.PdfReaderApplication
import com.acaba.pdfreader.data.HighlightStrokeEntity
import com.acaba.pdfreader.data.SyncState
import com.acaba.pdfreader.pdf.displayToPdf
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AnnotationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return@withContext Result.failure()
        val app = applicationContext as PdfReaderApplication
        val repository = app.repository
        val document = repository.findDocument(documentId) ?: return@withContext Result.success()
        if (!document.canWrite) return@withContext Result.failure()
        PdfDocumentLocks.forDocument(documentId).withLock {
            val pending = repository.pendingStrokes(documentId)
            if (pending.isEmpty()) return@withLock Result.success()

            val folder = File(applicationContext.cacheDir, "annotation-sync").apply { mkdirs() }
            val source = File(folder, "$documentId-source.pdf")
            val output = File(folder, "$documentId-output.pdf")
            try {
                source.delete()
                output.delete()
                repository.copyToFile(document, source)
                PDDocument.load(source).use { pdf ->
                    pending.forEach { stroke ->
                        if (stroke.syncState == SyncState.DELETE_PENDING || stroke.syncState == SyncState.DELETE_ERROR) {
                            removeAnnotation(pdf, stroke.annotationId)
                        } else {
                            removeAnnotation(pdf, stroke.annotationId)
                            val page = pdf.getPage(stroke.pageIndex)
                            val annotation = toAnnotation(stroke, page)
                            page.annotations.add(annotation)
                            annotation.constructAppearances(pdf)
                            annotation.normalAppearanceStream?.resources?.let { resources ->
                                resources.extGStateNames.forEach { name ->
                                    resources.getExtGState(name)?.apply {
                                        blendMode = BlendMode.NORMAL
                                        setLineCapStyle(1)
                                    }
                                }
                            }
                        }
                    }
                    pdf.save(output)
                }
                PDDocument.load(output).use { valid ->
                    check(valid.numberOfPages > 0) { "PDF inválido tras sincronizar" }
                    val annotationIds = valid.pages
                        .flatMap { page -> page.annotations.mapNotNull { it.annotationName } }
                        .toSet()
                    check(pending.all { stroke ->
                        if (stroke.syncState == SyncState.DELETE_PENDING || stroke.syncState == SyncState.DELETE_ERROR) {
                            stroke.annotationId !in annotationIds
                        } else {
                            stroke.annotationId in annotationIds
                        }
                    }) {
                        "El PDF validado no contiene el estado esperado de los subrayados"
                    }
                }
                repository.replaceWithFile(document, output, source)
                pending.forEach { stroke ->
                    if (stroke.syncState == SyncState.DELETE_PENDING || stroke.syncState == SyncState.DELETE_ERROR) {
                        repository.completeStrokeDeletion(stroke.annotationId)
                    } else {
                        repository.markStrokeSynced(stroke.annotationId)
                    }
                }
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                pending.forEach { stroke ->
                    if (stroke.syncState == SyncState.DELETE_PENDING || stroke.syncState == SyncState.DELETE_ERROR) {
                        repository.markStroke(stroke.annotationId, SyncState.DELETE_ERROR, error.message)
                    } else {
                        repository.markStroke(stroke.annotationId, SyncState.ERROR, error.message)
                    }
                }
                Result.retry()
            } finally {
                source.delete()
                output.delete()
            }
        }
    }

    private fun toAnnotation(stroke: HighlightStrokeEntity, page: com.tom_roush.pdfbox.pdmodel.PDPage): PDAnnotationMarkup {
        val width = page.mediaBox.width
        val height = page.mediaBox.height
        val rotation = ((page.rotation % 360) + 360) % 360
        val pdfPoints = stroke.points.map { point ->
            val mapped = displayToPdf(point.x, point.y, width, height, rotation)
            floatArrayOf(mapped.x, mapped.y)
        }.toTypedArray()
        val left = pdfPoints.minOfOrNull { it[0] } ?: 0f
        val right = pdfPoints.maxOfOrNull { it[0] } ?: 0f
        val bottom = pdfPoints.minOfOrNull { it[1] } ?: 0f
        val top = pdfPoints.maxOfOrNull { it[1] } ?: 0f
        val annotation = PDAnnotationMarkup()
        annotation.annotationName = stroke.annotationId
        annotation.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
        annotation.setInkList(arrayOf(pdfPoints.flatMap { it.asIterable() }.toFloatArray()))
        annotation.rectangle = PDRectangle(left, bottom, maxOf(1f, right - left), maxOf(1f, top - bottom))
        annotation.color = PDColor(rgbComponents(stroke.colorArgb), PDDeviceRGB.INSTANCE)
        annotation.constantOpacity = HIGHLIGHT_OPACITY
        annotation.setBorderStyle(PDBorderStyleDictionary().apply { setWidth(stroke.widthPoints) })
        return annotation
    }

    private fun removeAnnotation(pdf: PDDocument, annotationId: String) {
        pdf.pages.forEach { page ->
            page.annotations.removeAll { it.annotationName == annotationId }
        }
    }

    private fun rgbComponents(argb: Int): FloatArray = floatArrayOf(
        ((argb ushr 16) and 0xFF) / 255f,
        ((argb ushr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f,
    )

    companion object {
        private const val HIGHLIGHT_OPACITY = 0.28f
        private const val KEY_DOCUMENT_ID = "documentId"

        fun enqueue(context: Context, documentId: String) {
            val request = OneTimeWorkRequestBuilder<AnnotationSyncWorker>()
                .setInputData(androidx.work.workDataOf(KEY_DOCUMENT_ID to documentId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "pdf-annotation-$documentId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
