package io.noostv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.localization.LocalStrings
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Channel
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import io.noostv.ui.theme.*

enum class FavoriteFilterTab {
    ALL,
    CHANNELS,
    MOVIES,
    SERIES
}

/**
 * Onglet Favoris : Affiche l'ensemble des programmes, chaînes, films et séries
 * épinglés par l'utilisateur pour le profil actif.
 */
@Composable
fun TvFavoritesContent(
    channels: List<Channel>,
    movies: List<VodMovie>,
    series: List<Series>,
    sessionManager: SessionManager,
    focusRequester: FocusRequester? = null,
    onNavigateLeftToSidebar: (() -> Unit)? = null,
    onSelectChannel: (Channel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit,
    onFavoriteChanged: () -> Unit
) {
    val strings = LocalStrings.current
    var selectedFilter by remember { mutableStateOf(FavoriteFilterTab.ALL) }

    var activeProfile by remember { mutableStateOf(sessionManager.getActiveProfile()) }
    var favoriteChannelIds by remember { mutableStateOf(activeProfile.favoriteChannelIds) }
    var favoriteMovieIds by remember { mutableStateOf(activeProfile.favoriteMovieIds) }
    var favoriteSeriesIds by remember { mutableStateOf(activeProfile.favoriteSeriesIds) }

    fun refreshFavorites() {
        val prof = sessionManager.getActiveProfile()
        activeProfile = prof
        favoriteChannelIds = prof.favoriteChannelIds
        favoriteMovieIds = prof.favoriteMovieIds
        favoriteSeriesIds = prof.favoriteSeriesIds
        onFavoriteChanged()
    }

    val favChannels = remember(channels, favoriteChannelIds) {
        channels.filter { favoriteChannelIds.contains(it.id) }
    }
    val favMovies = remember(movies, favoriteMovieIds) {
        movies.filter { favoriteMovieIds.contains(it.id) }
    }
    val favSeries = remember(series, favoriteSeriesIds) {
        series.filter { favoriteSeriesIds.contains(it.id) }
    }

    val totalFavorites = favChannels.size + favMovies.size + favSeries.size
    val allTabRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = GoldVip,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.favoritesTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${strings.activeProfile} : ${activeProfile.name}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassPill)
                    .border(1.dp, GlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$totalFavorites épinglés",
                    color = GoldVip,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // Filtres par type de média
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FavTabChip(
                label = "Tout ($totalFavorites)",
                isSelected = selectedFilter == FavoriteFilterTab.ALL,
                focusRequester = focusRequester ?: allTabRequester,
                onNavigateLeft = onNavigateLeftToSidebar,
                onClick = { selectedFilter = FavoriteFilterTab.ALL }
            )
            FavTabChip(
                label = "Chaînes TV (${favChannels.size})",
                isSelected = selectedFilter == FavoriteFilterTab.CHANNELS,
                onClick = { selectedFilter = FavoriteFilterTab.CHANNELS }
            )
            FavTabChip(
                label = "Films (${favMovies.size})",
                isSelected = selectedFilter == FavoriteFilterTab.MOVIES,
                onClick = { selectedFilter = FavoriteFilterTab.MOVIES }
            )
            FavTabChip(
                label = "Séries (${favSeries.size})",
                isSelected = selectedFilter == FavoriteFilterTab.SERIES,
                onClick = { selectedFilter = FavoriteFilterTab.SERIES }
            )
        }

        // Contenu ou Empty State
        if (totalFavorites == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassCardGradient)
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GlassSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.StarOutline,
                            contentDescription = null,
                            tint = GoldVip,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = strings.noFavorites,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = strings.noFavoritesHint,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.widthIn(max = 480.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Section Chaînes
                if ((selectedFilter == FavoriteFilterTab.ALL || selectedFilter == FavoriteFilterTab.CHANNELS) && favChannels.isNotEmpty()) {
                    item {
                        Text(
                            text = "${strings.pinnedChannels} (${favChannels.size})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(favChannels, key = { it.id }) { channel ->
                                FavChannelCard(
                                    channel = channel,
                                    onClick = { onSelectChannel(channel) },
                                    onUnpin = {
                                        sessionManager.toggleFavoriteChannel(channel.id)
                                        refreshFavorites()
                                    }
                                )
                            }
                        }
                    }
                }

                // Section Films
                if ((selectedFilter == FavoriteFilterTab.ALL || selectedFilter == FavoriteFilterTab.MOVIES) && favMovies.isNotEmpty()) {
                    item {
                        Text(
                            text = "${strings.pinnedMovies} (${favMovies.size})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(favMovies, key = { it.id }) { movie ->
                                FavPosterCard(
                                    title = movie.title,
                                    posterUrl = movie.posterUrl,
                                    subtitle = "${movie.releaseYear ?: ""} • ★ ${movie.rating}",
                                    badge = movie.resolution,
                                    onClick = { onSelectMovie(movie) },
                                    onUnpin = {
                                        sessionManager.toggleFavoriteMovie(movie.id)
                                        refreshFavorites()
                                    }
                                )
                            }
                        }
                    }
                }

                // Section Séries
                if ((selectedFilter == FavoriteFilterTab.ALL || selectedFilter == FavoriteFilterTab.SERIES) && favSeries.isNotEmpty()) {
                    item {
                        Text(
                            text = "${strings.pinnedSeries} (${favSeries.size})",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(favSeries, key = { it.id }) { ser ->
                                FavPosterCard(
                                    title = ser.title,
                                    posterUrl = ser.posterUrl,
                                    subtitle = if (ser.seasons.isNotEmpty()) "${ser.seasons.size} Saisons • ★ ${ser.rating}" else "★ ${ser.rating}",
                                    badge = "SERIES",
                                    onClick = { onSelectSeries(ser) },
                                    onUnpin = {
                                        sessionManager.toggleFavoriteSeries(ser.id)
                                        refreshFavorites()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavTabChip(
    label: String,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    isFocused -> Color(0xFF283652)
                    isSelected -> NoosBlue.copy(alpha = 0.35f)
                    else -> GlassSurface
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) NoosCyan else if (isSelected) NoosCyan.copy(alpha = 0.6f) else GlassBorder,
                shape = RoundedCornerShape(50)
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
                } else false
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected || isFocused) Color.White else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun FavChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "fav_chan_scale")

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(110.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) Color(0xFF222F48) else GlassSurfaceElevated)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) NoosCyan else GlassBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onUnpin,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Détacher",
                        tint = GoldVip,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.categoryName,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RedLive.copy(alpha = 0.25f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("DIRECT", color = RedLive, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun FavPosterCard(
    title: String,
    posterUrl: String?,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "fav_post_scale")

    Box(
        modifier = Modifier
            .width(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0xFF283652) else GlassCard)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) NoosCyan else GlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF0F1420))
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bouton Détacher en haut à droite
                IconButton(
                    onClick = onUnpin,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99000000))
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Détacher des favoris",
                        tint = GoldVip,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Badge définition
                if (badge.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(badge, color = NoosCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = title,
                    color = if (isFocused) Color.White else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
