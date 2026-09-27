package com.crashalert.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideLocationTest {
    @Test fun recentFixProducesMapLink() {
        val fix = RideLocation(30.3165, 78.0322, 100f, 1_000L)
        assertEquals("https://maps.google.com/?q=30.316500,78.032200", fix.mapLinkIfFresh(30_000L))
    }

    @Test fun staleFutureOrInvalidFixIsNotShared() {
        assertNull(RideLocation(30.0, 78.0, 20f, 1_000L).mapLinkIfFresh(31_001L))
        assertNull(RideLocation(30.0, 78.0, 20f, 5_000L).mapLinkIfFresh(4_000L))
        assertNull(RideLocation(91.0, 78.0, 20f, 1_000L).mapLinkIfFresh(2_000L))
    }
}
