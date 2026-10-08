package io.noostv

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.noostv.data.cache.EncryptedCatalogStore
import io.noostv.data.model.Category
import io.noostv.data.model.Channel
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EncryptedCatalogStoreTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var catalogStore: EncryptedCatalogStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("noostv_test_cache").toFile()
        mockContext = mockk()
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.filesDir } returns tempDir

        catalogStore = EncryptedCatalogStore(mockContext)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `saveLiveCatalog and loadLiveCatalog should persist channels and categories correctly`() {
        val server = "http://test.iptv.com:8080"
        val user = "testuser"
        val channels = listOf(
            Channel(id = "1", name = "TF1 HD", streamUrl = "http://test.iptv.com:8080/live/1.ts", categoryName = "Généraliste"),
            Channel(id = "2", name = "France 2 HD", streamUrl = "http://test.iptv.com:8080/live/2.ts", categoryName = "Généraliste")
        )
        val categories = listOf(
            Category(id = "gen", name = "Généraliste")
        )

        catalogStore.saveLiveCatalog(server, user, channels, categories)
        val loaded = catalogStore.loadLiveCatalog(server, user)

        assertNotNull(loaded)
        assertEquals(2, loaded!!.channels.size)
        assertEquals("TF1 HD", loaded.channels[0].name)
        assertEquals(1, loaded.categories.size)
        assertEquals("Généraliste", loaded.categories[0].name)
    }

    @Test
    fun `saveVodMovies and loadVodMovies should persist movies per category`() {
        val server = "http://test.iptv.com:8080"
        val user = "testuser"
        val movies = listOf(
            VodMovie(id = "m1", title = "Inception", streamUrl = "http://test.iptv.com:8080/movie/m1.mp4", releaseYear = 2010),
            VodMovie(id = "m2", title = "Interstellar", streamUrl = "http://test.iptv.com:8080/movie/m2.mp4", releaseYear = 2014)
        )

        catalogStore.saveVodMovies(server, user, "sf_cat", movies)
        val loaded = catalogStore.loadVodMovies(server, user, "sf_cat")

        assertNotNull(loaded)
        assertEquals(2, loaded!!.size)
        assertEquals("Inception", loaded[0].title)
        assertEquals(2014, loaded[1].releaseYear)
    }

    @Test
    fun `saveSeries and loadSeries should persist series per category`() {
        val server = "http://test.iptv.com:8080"
        val user = "testuser"
        val series = listOf(
            Series(id = "s1", title = "Breaking Bad", categoryId = "drama", releaseYear = 2008)
        )

        catalogStore.saveSeries(server, user, "drama", series)
        val loaded = catalogStore.loadSeries(server, user, "drama")

        assertNotNull(loaded)
        assertEquals(1, loaded!!.size)
        assertEquals("Breaking Bad", loaded[0].title)
    }

    @Test
    fun `clearAccountCache should delete account specific files`() {
        val server = "http://test.iptv.com:8080"
        val user = "testuser"
        catalogStore.saveLiveCatalog(server, user, listOf(Channel(id = "1", name = "Test", streamUrl = "")), emptyList())

        assertNotNull(catalogStore.loadLiveCatalog(server, user))
        catalogStore.clearAccountCache(server, user)
        assertNull(catalogStore.loadLiveCatalog(server, user))
    }
}
