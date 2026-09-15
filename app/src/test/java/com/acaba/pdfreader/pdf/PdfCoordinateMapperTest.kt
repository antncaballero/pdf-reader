package com.acaba.pdfreader.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoordinateMapperTest {
    @Test
    fun mapsUnrotatedTopLeftToBottomLeft() {
        assertEquals(PdfCoordinate(10f, 700f), displayToPdf(10f, 100f, 600f, 800f, 0))
    }

    @Test
    fun mapsAllQuarterTurns() {
        assertEquals(PdfCoordinate(100f, 10f), displayToPdf(10f, 100f, 600f, 800f, 90))
        assertEquals(PdfCoordinate(590f, 100f), displayToPdf(10f, 100f, 600f, 800f, 180))
        assertEquals(PdfCoordinate(500f, 790f), displayToPdf(10f, 100f, 600f, 800f, 270))
    }
}
