package io.noostv.core.search

import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import java.text.Normalizer

/**
 * Résultat agrégé d'une recherche universelle NoosTV
 */
data class UniversalSearchResult(
    val query: String,
    val channels: List<Channel> = emptyList(),
    val movies: List<VodMovie> = emptyList(),
    val series: List<Series> = emptyList(),
    val epgPrograms: List<EpgProgram> = emptyList()
) {
    val totalCount: Int
        get() = channels.size + movies.size + series.size + epgPrograms.size

    val isEmpty: Boolean
        get() = totalCount == 0
}

/**
 * Moteur de recherche universel NoosTV.
 * Effectue des recherches ultra-rapides, insensibles à la casse et aux accents sur l'ensemble du catalogue.
 */
class UniversalSearchEngine {

    /**
     * Supprime les accents et met en minuscules pour comparaison insensible
     */
    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "").lowercase().trim()
    }

    /**
     * Recherche globale à travers toutes les sources de contenu
     */
    fun search(
        query: String,
        allChannels: List<Channel>,
        allMovies: List<VodMovie>,
        allSeries: List<Series>,
        allEpgPrograms: List<EpgProgram> = emptyList(),
        maxResultsPerSection: Int = 20
    ): UniversalSearchResult {
        if (query.isBlank()) {
            return UniversalSearchResult(query = query)
        }

        val normalizedQuery = normalize(query)
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }

        // Recherche Chaînes
        val matchedChannels = allChannels.filter { channel ->
            val normName = normalize(channel.name)
            val normCat = normalize(channel.categoryName)
            queryTokens.all { token -> normName.contains(token) || normCat.contains(token) }
        }.take(maxResultsPerSection)

        // Recherche Films
        val matchedMovies = allMovies.filter { movie ->
            val normTitle = normalize(movie.title)
            val normPlot = movie.plot?.let { normalize(it) } ?: ""
            val normDirector = movie.director?.let { normalize(it) } ?: ""
            queryTokens.all { token ->
                normTitle.contains(token) || normPlot.contains(token) || normDirector.contains(token)
            }
        }.take(maxResultsPerSection)

        // Recherche Séries
        val matchedSeries = allSeries.filter { s ->
            val normTitle = normalize(s.title)
            val normPlot = s.plot?.let { normalize(it) } ?: ""
            queryTokens.all { token -> normTitle.contains(token) || normPlot.contains(token) }
        }.take(maxResultsPerSection)

        // Recherche Programmes Guide TV
        val matchedEpg = allEpgPrograms.filter { prog ->
            val normTitle = normalize(prog.title)
            val normDesc = prog.description?.let { normalize(it) } ?: ""
            queryTokens.all { token -> normTitle.contains(token) || normDesc.contains(token) }
        }.take(maxResultsPerSection)

        return UniversalSearchResult(
            query = query,
            channels = matchedChannels,
            movies = matchedMovies,
            series = matchedSeries,
            epgPrograms = matchedEpg
        )
    }
}
