package com.example.noxapp.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch

class LocationSearchTest {
    @Test
    fun cancellationReturnsEvenWhenLegacyProviderIgnoresInterruption() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val search = launch {
            LocationSearch.awaitLegacyResult {
                started.complete(Unit)
                while (release.count > 0) {
                    try {
                        release.await()
                    } catch (_: InterruptedException) {
                        // Binder requests can outlive thread interruption.
                    }
                }
                emptyList()
            }
        }
        try {
            started.await()
            search.cancel()
            assertTrue("UI cancellation must not wait for the provider",
                withTimeoutOrNull(2_000) { search.join(); true } == true)
        } finally {
            release.countDown()
        }
    }

    @Test
    fun legacyProviderErrorsReachCaller() = runBlocking {
        try {
            LocationSearch.awaitLegacyResult { throw IOException("Service unavailable") }
            fail("Expected provider failure")
        } catch (error: IOException) {
            assertEquals("Service unavailable", error.message)
        }
    }
}
