package com.acaba.pdfreader.pdf

import android.content.ContentResolver
import android.net.Uri
import android.util.LruCache
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import kotlin.math.hypot
import kotlin.math.max

data class TextGlyph(
    val pageIndex: Int,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val order: Int,
    val line: Int,
)

data class TextPage(
    val pageIndex: Int,
    val width: Float,
    val height: Float,
    val glyphs: List<TextGlyph>,
)

class PdfTextEngine(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : Closeable {
    private val cache = object : LruCache<Int, TextPage>(8) {}
    private var document: PDDocument? = null

    suspend fun page(pageIndex: Int): TextPage = withContext(Dispatchers.IO) {
        cache.get(pageIndex) ?: synchronized(this@PdfTextEngine) {
            cache.get(pageIndex) ?: extractPage(pageIndex).also { cache.put(pageIndex, it) }
        }
    }

    suspend fun select(pageIndex: Int, startX: Float, startY: Float, endX: Float, endY: Float): List<TextGlyph> {
        val page = page(pageIndex)
        if (page.glyphs.isEmpty()) return emptyList()
        val start = nearest(page.glyphs, startX, startY)
        val end = nearest(page.glyphs, endX, endY)
        val from = minOf(start.order, end.order)
        val to = maxOf(start.order, end.order)
        return page.glyphs.filter { it.order in from..to }
    }

    fun textOf(glyphs: List<TextGlyph>): String {
        if (glyphs.isEmpty()) return ""
        val sorted = glyphs.sortedBy { it.order }
        return buildString {
            var previousLine = sorted.first().line
            var previousRight = sorted.first().right
            sorted.forEachIndexed { index, glyph ->
                if (index > 0) {
                    if (glyph.line != previousLine) append('\n')
                    else if (glyph.left - previousRight > max(2f, glyph.bottom - glyph.top) * 0.35f) append(' ')
                }
                append(glyph.text)
                previousLine = glyph.line
                previousRight = glyph.right
            }
        }
    }

    override fun close() {
        synchronized(this) {
            cache.evictAll()
            document?.close()
            document = null
        }
    }

    private fun extractPage(pageIndex: Int): TextPage {
        val doc = ensureDocument()
        require(pageIndex in 0 until doc.numberOfPages) { "Página no disponible" }
        val pdPage = doc.getPage(pageIndex)
        val glyphs = mutableListOf<TextGlyph>()
        val stripper = object : PDFTextStripper() {
            override fun processTextPosition(text: TextPosition) {
                val value = text.unicode ?: return
                if (value.isEmpty()) return
                val left = text.xDirAdj
                val top = text.yDirAdj - text.heightDir
                val right = left + text.widthDirAdj
                val bottom = top + text.heightDir
                val line = (top / max(text.heightDir, 1f)).toInt()
                glyphs += TextGlyph(pageIndex, value, left, top, right, bottom, glyphs.size, line)
                super.processTextPosition(text)
            }
        }
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.getText(doc)
        return TextPage(pageIndex, pdPage.mediaBox.width, pdPage.mediaBox.height, glyphs)
    }

    private fun ensureDocument(): PDDocument {
        document?.let { return it }
        val input = resolver.openInputStream(uri) ?: error("No se puede abrir el PDF")
        return input.use { PDDocument.load(it) }.also { document = it }
    }

    private fun nearest(glyphs: List<TextGlyph>, x: Float, y: Float): TextGlyph = glyphs.minByOrNull {
        hypot((it.left + it.right) / 2f - x, (it.top + it.bottom) / 2f - y)
    } ?: glyphs.first()
}
