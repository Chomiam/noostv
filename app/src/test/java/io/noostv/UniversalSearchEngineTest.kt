package io.noostv

import io.noostv.core.search.UniversalSearchEngine
import io.noostv.data.model.Channel
import io.noostv.data.model.VodMovie
import io.noostv.data.model.Series
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UniversalSearchEngineTest {

    private lateinit var searchEngine: UniversalSearchEngine

    @Before
    fun setUp() {
        searchEngine = UniversalSearchEngine()
    }

    @Test
    fun `search should be accent-insensitive and match accented words`() {
        val channels = listOf(
            Channel(id = "1", name = "Canal+ Cinéma HD", streamUrl = "http://..."),
            Channel(id = "2", name = "TF1 Direct", streamUrl = "http://...")
        )
        val movies = listOf(
            VodMovie(id = "m1", title = "Le Fabuleux Destin d'Amélie Poulain", streamUrl = "http://...", plot = "Comédie dramatique")
        )

        // Recherche avec sans accent "cinema"
        val result1 = searchEngine.search("cinema", channels, movies, emptyList())
        assertEquals(1, result1.channels.size)
        assertEquals("Canal+ Cinéma HD", result1.channels[0].name)

        // Recherche sans accent "amelie"
        val result2 = searchEngine.search("amelie", channels, movies, emptyList())
        assertEquals(1, result2.movies.size)
        assertEquals("Le Fabuleux Destin d'Amélie Poulain", result2.movies[0].title)
    }

    @Test
    fun `search with empty query should return empty result`() {
        val result = searchEngine.search("", emptyList(), emptyList(), emptyList())
        assertTrue(result.isEmpty)
        assertEquals(0, result.totalCount)
    }
}
