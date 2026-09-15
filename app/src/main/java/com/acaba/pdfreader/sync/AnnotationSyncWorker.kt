package com.acaba.pdfreader.sync

import android.content.Context
import android.graphics.Color
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
                    val existing = pdf.pages.flatMap { page -> page.annotations.mapNotNull { it.annotationName } }.toSet()
                    pending.forEach { stroke ->
                        if (stroke.annotationId in existing) {
                            return@forEach
                        }
                        val page = pdf.getPage(stroke.pageIndex)
                        val annotation = toAnnotation(stroke, page)
                        page.annotations.add(annotation)
                        annotation.constructAppearances(pdf)
                        annotation.normalAppearanceStream?.resources?.let { resources ->
                            resources.extGStateNames.forEach { name ->
                                resources.getExtGState(name)?.apply {
                                    blendMode = BlendMode.MULTIPLY
                                    setLineCapStyle(1)
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
                    check(pending.all { it.annotationId in annotationIds }) {
                        "El PDF validado no contiene todos los subrayados"
                    }
                }
                repository.replaceWithFile(document, output, source)
                pending.forEach { repository.markStroke(it.annotationId, SyncState.SYNCED) }
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                pending.forEach { repository.markStroke(it.annotationId, SyncState.ERROR, error.message) }
                Result.failure()
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
        val color = Color.valueOf(stroke.colorArgb)
        annotation.color = PDColor(floatArrayOf(color.red() / 255f, color.green() / 255f, color.blue() / 255f), PDDeviceRGB.INSTANCE)
        annotation.constantOpacity = HIGHLIGHT_OPACITY
        annotation.setBorderStyle(PDBorderStyleDictionary().apply { setWidth(stroke.widthPoints) })
        return annotation
    }

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
