package io.noostv.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import io.noostv.data.model.Episode
import io.noostv.data.model.Season
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import io.noostv.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Fiche de métadonnées détaillée pour Film VOD sur Mobile.
 */
@Composable
fun MobileMovieDetailModal(
    movie: VodMovie,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlay: (VodMovie) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onFetchFullInfo: suspend (String) -> VodMovie?
) {
    var fullMovie by remember(movie.id) { mutableStateOf(movie) }
    var isLoadingInfo by remember(movie.id) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(movie.id) {
        isLoadingInfo = true
        val enriched = onFetchFullInfo(movie.id)
        if (enriched != null) {
            fullMovie = enriched
        }
        isLoadingInfo = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE60A0D14))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurface)
                    .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {} // Empêche la fermeture au clic dans la carte
            ) {
                // Header avec Image Backdrop ou Affiche
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    val bgImg = fullMovie.backdropUrl ?: fullMovie.posterUrl
                    if (bgImg != null) {
                        AsyncImage(
                            model = bgImg,
                            contentDescription = fullMovie.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkCard)
                        )
                    }

                    // Dégradé vers le bas
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xAA0E131F), DarkSurface)
                                )
                            )
                    )

                    // Bouton Fermer
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color(0x66000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.White
                        )
                    }

                    // Titre et badges au bas de la bannière
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = fullMovie.title,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            fullMovie.releaseYear?.let {
                                Text(text = "$it", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "•", color = TextSecondary, fontSize = 12.sp)
                            }
                            Text(text = fullMovie.durationFormatted, color = TextSecondary, fontSize = 12.sp)
                            if (fullMovie.rating > 0f) {
                                Text(text = "•", color = TextSecondary, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Note",
                                        tint = AccentAmber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = String.format("%.1f", fullMovie.rating),
                                        color = AccentAmber,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Contenu scrollable : badges, synopsis, réalisateur, casting
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        // Badges techniques & Genres
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BadgeChip(fullMovie.resolution)
                            if (fullMovie.isHdr) {
                                BadgeChip(fullMovie.hdrFormat ?: "HDR", NoosCyan)
                            }
                            BadgeChip(fullMovie.videoCodec.uppercase())
                            BadgeChip(fullMovie.audioCodec.uppercase())
                        }
                    }

                    if (fullMovie.genres.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(fullMovie.genres) { g ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = g, color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "SYNOPSIS",
                            color = NoosCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = fullMovie.plot?.takeIf { it.isNotBlank() }
                                ?: if (isLoadingInfo) "Chargement du résumé..." else "Aucun synopsis disponible pour ce film.",
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    fullMovie.director?.let { dir ->
                        if (dir.isNotBlank()) {
                            item {
                                Row {
                                    Text(text = "Réalisateur : ", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(text = dir, color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (fullMovie.cast.isNotEmpty()) {
                        item {
                            Column {
                                Text(text = "Distribution : ", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = fullMovie.cast.joinToString(", "),
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Boutons d'action en bas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onPlay(fullMovie)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoosCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Lire le film", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    IconButton(
                        onClick = { onToggleFavorite(movie.id) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favori",
                            tint = if (isFavorite) AccentAmber else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fiche de métadonnées détaillée pour Série TV sur Mobile avec Sélecteur de Saisons et Liste d'Épisodes.
 */
@Composable
fun MobileSeriesDetailModal(
    series: Series,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onPlayEpisode: (Series, Episode) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onFetchFullInfo: suspend (String) -> Series?
) {
    var fullSeries by remember(series.id) { mutableStateOf(series) }
    var isLoadingInfo by remember(series.id) { mutableStateOf(series.seasons.isEmpty()) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(series.id) {
        isLoadingInfo = true
        val enriched = onFetchFullInfo(series.id)
        if (enriched != null) {
            fullSeries = enriched
        }
        isLoadingInfo = false
    }

    val activeSeason: Season? = fullSeries.seasons.getOrNull(selectedSeasonIndex) ?: fullSeries.seasons.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE60A0D14))
                .clickable { onDismiss() }
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkSurface)
                    .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(24.dp))
                    .clickable(enabled = false) {}
            ) {
                // Header Bannière
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val bgImg = fullSeries.backdropUrl ?: fullSeries.posterUrl
                    if (bgImg != null) {
                        AsyncImage(
                            model = bgImg,
                            contentDescription = fullSeries.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(DarkCard))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xAA0E131F), DarkSurface)
                                )
                            )
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color(0x66000000), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = fullSeries.title,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${fullSeries.seasons.size} Saison${if (fullSeries.seasons.size > 1) "s" else ""}",
                                color = NoosCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            fullSeries.releaseYear?.let {
                                Text(text = "•", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "$it", color = TextSecondary, fontSize = 12.sp)
                            }
                            if (fullSeries.rating > 0f) {
                                Text(text = "•", color = TextSecondary, fontSize = 12.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Filled.Star, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(text = String.format("%.1f", fullSeries.rating), color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Onglets de Saisons
                if (fullSeries.seasons.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(fullSeries.seasons.indices.toList()) { index ->
                            val s = fullSeries.seasons[index]
                            val isSelected = index == selectedSeasonIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) NoosCyan else DarkCard)
                                    .clickable { selectedSeasonIndex = index }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = s.name.ifBlank { "Saison ${s.seasonNumber}" },
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Liste des épisodes de la saison sélectionnée
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLoadingInfo) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = NoosCyan, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else if (activeSeason != null && activeSeason.episodes.isNotEmpty()) {
                        items(activeSeason.episodes, key = { it.id }) { ep ->
                            MobileEpisodeCard(
                                episode = ep,
                                onPlay = {
                                    onDismiss()
                                    onPlayEpisode(fullSeries, ep)
                                }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "Aucun épisode disponible pour cette saison",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }

                // Boutons d'action au bas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstEp = activeSeason?.episodes?.firstOrNull() ?: fullSeries.seasons.firstOrNull()?.episodes?.firstOrNull()
                    val targetEp = firstEp ?: Episode(
                        id = fullSeries.id,
                        seriesId = fullSeries.id,
                        seasonNumber = 1,
                        episodeNumber = 1,
                        title = "Épisode 1",
                        streamUrl = ""
                    )
                    Button(
                        onClick = {
                            onDismiss()
                            onPlayEpisode(fullSeries, targetEp)
                        },
                        enabled = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoosCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (firstEp != null) "Regarder S${firstEp.seasonNumber}:E${firstEp.episodeNumber}" else "Lancer la série",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    IconButton(
                        onClick = { onToggleFavorite(series.id) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkCard)
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favori",
                            tint = if (isFavorite) AccentAmber else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileEpisodeCard(
    episode: Episode,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkCard)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail épisode ou badge numéro
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161C28)),
            contentAlignment = Alignment.Center
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = "E${episode.episodeNumber}",
                    color = NoosCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Infos épisode
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNumber}. ${episode.title}",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                episode.durationMinutes?.let { dur ->
                    Text(text = "${dur}m", color = TextSecondary, fontSize = 11.sp)
                }
                if (episode.rating > 0f) {
                    Text(text = " • ★ ${String.format("%.1f", episode.rating)}", color = AccentAmber, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Bouton lecture
        IconButton(
            onClick = onPlay,
            modifier = Modifier
                .size(36.dp)
                .background(Color(0x2200E5FF), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Lire l'épisode",
                tint = NoosCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color = Color(0xFF6B7280)) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = if (color == Color(0xFF6B7280)) TextPrimary else color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
