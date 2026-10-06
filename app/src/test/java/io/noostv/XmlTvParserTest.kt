package io.noostv

import io.noostv.data.parser.XmlTvParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class XmlTvParserTest {

    private lateinit var parser: XmlTvParser

    @Before
    fun setUp() {
        parser = XmlTvParser()
    }

    @Test
    fun `parse valid XMLTV stream should extract EPG programs and dates`() {
        val xmltv = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="tf1.fr">
                <display-name>TF1</display-name>
              </channel>
              <programme start="20261006200000 +0200" stop="20261006210000 +0200" channel="tf1.fr">
                <title lang="fr">Journal de 20H</title>
                <desc lang="fr">Le grand journal télévisé</desc>
                <category lang="fr">Information</category>
              </programme>
            </tv>
        """.trimIndent()

        val programs = parser.parse(ByteArrayInputStream(xmltv.toByteArray()))

        assertEquals(1, programs.size)
        val p = programs[0]
        assertEquals("tf1.fr", p.channelId)
        assertEquals("Journal de 20H", p.title)
        assertEquals("Le grand journal télévisé", p.description)
        assertEquals("Information", p.category)
        assertTrue(p.startEpochMs > 0)
        assertTrue(p.stopEpochMs > p.startEpochMs)
    }

    @Test
    fun `epgProgram progressFraction should calculate correct percentage`() {
        val start = 1_000_000L
        val stop = 2_000_000L
        val currentMid = 1_500_000L

        val prog = io.noostv.data.model.EpgProgram(
            id = "test",
            channelId = "ch1",
            title = "Film",
            startEpochMs = start,
            stopEpochMs = stop
        )

        assertTrue(prog.isLiveNow(currentMid))
        assertEquals(0.5f, prog.progressFraction(currentMid), 0.01f)
        assertEquals(0.0f, prog.progressFraction(start - 1000), 0.01f)
        assertEquals(1.0f, prog.progressFraction(stop + 1000), 0.01f)
    }
}
