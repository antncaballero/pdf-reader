package com.acaba.pdfreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val converters = RoomConverters()

    @Test
    fun strokePointsRoundTripAsCompactBlob() {
        val points = listOf(StrokePoint(1.25f, -2.5f), StrokePoint(99f, 0.125f))
        val restored = converters.blobToPoints(converters.pointsToBlob(points))
        assertEquals(points, restored)
    }

    @Test
    fun emptyStrokePointsRoundTrip() {
        assertTrue(converters.blobToPoints(converters.pointsToBlob(emptyList())).isEmpty())
    }

    @Test
    fun syncStateRoundTrip() {
        SyncState.entries.forEach { assertEquals(it, converters.stringToSyncState(converters.syncStateToString(it))) }
    }
}
