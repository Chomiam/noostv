package io.noostv

import io.noostv.core.player.formatDuration
import io.noostv.data.model.PlaybackResumePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumeTest {

    @Test
    fun testFormatDuration() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("00:45", formatDuration(45_000L))
        assertEquals("03:15", formatDuration(195_000L))
        assertEquals("01:23:45", formatDuration((1 * 3600 + 23 * 60 + 45) * 1000L))
        assertEquals("10:05:01", formatDuration((10 * 3600 + 5 * 60 + 1) * 1000L))
    }

    @Test
    fun testPlaybackResumePointCalculations() {
        val point = PlaybackResumePoint(
            contentId = "movie_123",
            title = "Test Movie",
            positionMs = 300_000L,
            durationMs = 600_000L
        )

        assertEquals(0.5f, point.progressFraction, 0.001f)
        assertEquals(50, point.progressPercent)
        assertEquals("05:00", point.formattedPosition)
        assertEquals("10:00", point.formattedDuration)
    }

    @Test
    fun testZeroDurationSafeguard() {
        val point = PlaybackResumePoint(
            contentId = "movie_456",
            title = "Zero Duration Movie",
            positionMs = 10_000L,
            durationMs = 0L
        )

        assertEquals(0f, point.progressFraction, 0.001f)
        assertEquals(0, point.progressPercent)
        assertEquals("00:10", point.formattedPosition)
    }
}
