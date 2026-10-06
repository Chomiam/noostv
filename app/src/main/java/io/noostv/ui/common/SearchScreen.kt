package io.noostv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.search.UniversalSearchEngine
import io.noostv.core.search.UniversalSearchResult
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import io.noostv.ui.theme.*

@Composable
fun SearchScreen(
    channels: List<Channel>,
    movies: List<VodMovie>,
    series: List<Series>,
    epgPrograms: List<EpgProgram>,
    onSelectChannel: (Channel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit = {},
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val searchEngine = remember { UniversalSearchEngine() }

    val searchResult by remember(query, channels, movies, series, epgPrograms) {
        derivedStateOf {
            if (query.isBlank()) {
                UniversalSearchResult(query = "")
            } else {
                searchEngine.search(
                    query = query,
                    allChannels = channels,
                    allMovies = movies,
                    allSeries = series,
                    allEpgPrograms = epgPrograms
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkOledBackground)
            .padding(24.dp)
    ) {
        // Barre de recherche
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Retour", tint = TextPrimary)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Rechercher une chaîne, un film, une série ou un programme...", color = TextSecondary) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonCyan) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Effacer", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SurfaceDarkVariant,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceDark,
                    unfocusedContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Résultats de recherche
        if (query.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Saisissez un mot-clé (ex: 'TF1', 'Dune', 'Sport', '4K')...",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        } else if (searchResult.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun résultat trouvé pour « $query »",
                    color = TextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Chaînes trouvées
                if (searchResult.channels.isNotEmpty()) {
                    item {
                        Text("Chaînes Direct TV (${searchResult.channels.size})", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    items(searchResult.channels) { channel ->
                        SearchResultItem(
                            title = channel.name,
                            subtitle = "${channel.categoryName} • ${channel.resolution}",
                            badge = if (channel.isHdr) "HDR" else null,
                            onClick = { onSelectChannel(channel) }
                        )
                    }
                }

                // Films trouvés
                if (searchResult.movies.isNotEmpty()) {
                    item {
                        Text("Films VOD (${searchResult.movies.size})", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    items(searchResult.movies) { movie ->
                        SearchResultItem(
                            title = movie.title,
                            subtitle = "${movie.releaseYear} • ★ ${movie.rating} • ${movie.durationFormatted}",
                            badge = if (movie.isHdr) movie.hdrFormat ?: "HDR" else null,
                            onClick = { onSelectMovie(movie) }
                        )
                    }
                }

                // Séries trouvées
                if (searchResult.series.isNotEmpty()) {
                    item {
                        Text("Séries (${searchResult.series.size})", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    items(searchResult.series) { item ->
                        SearchResultItem(
                            title = item.title,
                            subtitle = "${item.seasons.size} Saisons • ★ ${item.rating}",
                            onClick = { onSelectSeries(item) }
                        )
                    }
                }

                // Programmes EPG trouvés
                if (searchResult.epgPrograms.isNotEmpty()) {
                    item {
                        Text("Programmes Guide TV (${searchResult.epgPrograms.size})", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    items(searchResult.epgPrograms) { prog ->
                        val matchingChannel = channels.find { it.id == prog.channelId || it.epgChannelId == prog.channelId }
                        SearchResultItem(
                            title = prog.title,
                            subtitle = "${prog.timeSlotFormatted} • ${prog.category ?: ""} ${matchingChannel?.let { "(${it.name})" } ?: ""}",
                            onClick = {
                                if (matchingChannel != null) {
                                    onSelectChannel(matchingChannel)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .background(if (isFocused) Color(0xFF1E2838) else SurfaceDark)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isFocused) NeonCyan else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
            }
            if (badge != null) {
                HdrBadge(text = badge)
            }
        }
    }
}
