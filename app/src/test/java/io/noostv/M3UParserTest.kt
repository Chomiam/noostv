package io.noostv

import io.noostv.data.parser.M3UParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class M3UParserTest {

    private lateinit var parser: M3UParser

    @Before
    fun setUp() {
        parser = M3UParser()
    }

    @Test
    fun `parse valid M3U playlist should extract channels with metadata`() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="tf1.fr" tvg-name="TF1" tvg-logo="http://logos.com/tf1.png" group-title="General",TF1 HD
            http://streams.server.com/live/user/pass/1.m3u8
            #EXTINF:-1 tvg-id="canal.fr" group-title="Sport",Canal+ Sport 4K HDR
            http://streams.server.com/live/user/pass/2.ts
        """.trimIndent()

        val channels = parser.parse(ByteArrayInputStream(m3uContent.toByteArray()))

        assertEquals(2, channels.size)

        val tf1 = channels[0]
        assertEquals("TF1 HD", tf1.name)
        assertEquals("http://streams.server.com/live/user/pass/1.m3u8", tf1.streamUrl)
        assertEquals("http://logos.com/tf1.png", tf1.logoUrl)
        assertEquals("General", tf1.categoryName)
        assertEquals("1080p", tf1.resolution)
        assertFalse(tf1.isHdr)

        val canal = channels[1]
        assertEquals("Canal+ Sport 4K HDR", canal.name)
        assertEquals("Sport", canal.categoryName)
        assertTrue(canal.isHdr)
        assertEquals("4K UHD", canal.resolution)
        assertEquals("hevc", canal.videoCodec)
    }

    @Test
    fun `parse playlist with AV1 codec tag should identify AV1 video codec`() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="fr2.fr" group-title="General",France 2 4K UHD AV1
            http://streams.server.com/live/user/pass/3.ts
        """.trimIndent()

        val channels = parser.parse(ByteArrayInputStream(m3uContent.toByteArray()))
        assertEquals(1, channels.size)
        assertEquals("av1", channels[0].videoCodec)
        assertEquals("4K UHD", channels[0].resolution)
    }

    @Test
    fun `parse empty or malformed playlist should not crash and return empty list`() {
        val emptyContent = ""
        val channels = parser.parse(ByteArrayInputStream(emptyContent.toByteArray()))
        assertTrue(channels.isEmpty())
    }
}
