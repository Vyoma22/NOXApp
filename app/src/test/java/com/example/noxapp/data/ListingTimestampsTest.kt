package com.example.noxapp.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

class ListingTimestampsTest {

    @Test fun convertsFirestoreServerTimestamp() {
        assertEquals(
            1_700_000_000_123L,
            ListingTimestamps.toMillis(Timestamp(1_700_000_000L, 123_000_000))
        )
    }

    @Test fun truncatesSubMillisecondTimestampNanos() {
        assertEquals(5_000L, ListingTimestamps.toMillis(Timestamp(5L, 999_999)))
    }

    @Test fun convertsLegacyNumbers() {
        assertEquals(42L, ListingTimestamps.toMillis(42))
        assertEquals(1_700_000_000_123L, ListingTimestamps.toMillis(1_700_000_000_123L))
        assertEquals(1_700_000_000_123L, ListingTimestamps.toMillis(1_700_000_000_123.0))
    }

    @Test fun convertsDate() {
        assertEquals(1_700_000_000_123L, ListingTimestamps.toMillis(Date(1_700_000_000_123L)))
    }

    @Test fun rejectsNullOrGarbage() {
        assertNull(ListingTimestamps.toMillis(null))
        assertNull(ListingTimestamps.toMillis("1700000000123"))
        assertNull(ListingTimestamps.toMillis(true))
        assertNull(ListingTimestamps.toMillis(listOf(1)))
    }
}
