package com.acaba.pdfreader.pdf

data class PdfCoordinate(val x: Float, val y: Float)

/** Maps AndroidX's top-left display coordinates into PDFBox's bottom-left coordinates. */
fun displayToPdf(x: Float, y: Float, pageWidth: Float, pageHeight: Float, rotation: Int): PdfCoordinate {
    return when (((rotation % 360) + 360) % 360) {
        90 -> PdfCoordinate(y, x)
        180 -> PdfCoordinate(pageWidth - x, y)
        270 -> PdfCoordinate(pageWidth - y, pageHeight - x)
        else -> PdfCoordinate(x, pageHeight - y)
    }
}
