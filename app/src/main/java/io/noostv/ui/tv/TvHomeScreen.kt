package io.noostv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.data.model.Channel
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.PremiumVipBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.theme.*

enum class TvNavTab(val label: String, val icon: ImageVector) {
    LIVE("Direct TV", Icons.Default.Tv),
    MOVIES("Films VOD", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary),
    EPG("Guide EPG", Icons.Default.EventNote),
    SEARCH("Recherche", Icons.Default.Search),
    SETTINGS("SaaS & VIP", Icons.Default.Star)
}

@Composable
fun TvHomeScreen(
    channels: List<Channel>,
    movies: List<VodMovie>,
    series: List<Series>,
    entitlementManager: EntitlementManager,
    onSelectChannel: (Channel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onOpenEpg: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(TvNavTab.LIVE) }
    val subscription by entitlementManager.subscription.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkOledBackground)
    ) {
        // Barre latérale de navigation D-Pad pour Android TV
        TvSidebar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                if (tab == TvNavTab.EPG) onOpenEpg()
                if (tab == TvNavTab.SEARCH) onOpenSearch()
                if (tab == TvNavTab.SETTINGS) onOpenUpgrade()
            },
            isPremium = subscription.isPremium
        )

        // Contenu principal de la TV
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 20.dp, end = 24.dp)
        ) {
            // Header supérieur avec état SaaS et statut Mi Box / Google TV Streamer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "NOOS",
                        color = NeonCyan,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "TV",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (subscription.isPremium) {
                        PremiumVipBadge()
                    } else {
                        OutlinedButton(
                            onClick = onOpenUpgrade,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldVip),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldVip),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Débloquer 4K HDR VIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResolutionBadge(resolution = "4K HDR READY")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SurfaceDarkVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Mode Salon 10-Foot (D-Pad)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Liste scrollable de carrousels pour la TV
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Section 1 : Chaînes Direct TV en Vedette
                item {
                    TvSectionHeader(title = "Chaînes TV en Direct & 4K UHD", icon = Icons.Default.Tv)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(channels) { channel ->
                            TvChannelCard(
                                channel = channel,
                                onClick = { onSelectChannel(channel) }
                            )
                        }
                    }
                }

                // Section 2 : VOD Films Récents
                item {
                    TvSectionHeader(title = "Films VOD à l'Affiche (AV1 / HEVC)", icon = Icons.Default.Movie)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(movies) { movie ->
                            TvMovieCard(
                                movie = movie,
                                onClick = { onSelectMovie(movie) }
                            )
                        }
                    }
                }

                // Section 3 : Séries en Streaming
                item {
                    TvSectionHeader(title = "Séries & Saisons Complètes", icon = Icons.Default.VideoLibrary)
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(series) { item ->
                            TvSeriesCard(
                                series = item,
                                onClick = { /* Ouvrir détails série */ }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvSidebar(
    selectedTab: TvNavTab,
    onTabSelected: (TvNavTab) -> Unit,
    isPremium: Boolean
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(SurfaceDark)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TvNavTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            var isFocused by remember { mutableStateOf(false) }

            val scale by animateFloatAsState(
                targetValue = if (isFocused) 1.15f else 1.0f,
                label = "nav_scale"
            )

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isSelected -> NeonCyan.copy(alpha = 0.25f)
                            isFocused -> SurfaceDarkVariant
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                        color = if (isFocused) FocusGlow else if (isSelected) NeonCyan else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .focusable()
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (tab == TvNavTab.SETTINGS && !isPremium) GoldVip else if (isSelected || isFocused) NeonCyan else TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
fun TvSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TvChannelCard(channel: Channel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "card_scale")

    Box(
        modifier = Modifier
            .width(220.dp)
            .height(124.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
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
                LiveIndicatorBadge()
                if (channel.isHdr) {
                    HdrBadge(text = "HDR")
                }
            }

            Column {
                Text(
                    text = channel.name,
                    color = if (isFocused) NeonCyan else TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${channel.categoryName} • ${channel.resolution} • ${channel.videoCodec.uppercase()}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun TvMovieCard(movie: VodMovie, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "vod_scale")

    Box(
        modifier = Modifier
            .width(170.dp)
            .height(230.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(SurfaceDarkVariant, CardBackground)
                        )
                    ),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (movie.isHdr) HdrBadge(text = movie.hdrFormat ?: "HDR")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = movie.title,
                    color = if (isFocused) NeonCyan else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${movie.releaseYear ?: 2024} • ★ ${movie.rating} • ${movie.durationFormatted}",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun TvSeriesCard(series: Series, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "series_scale")

    Box(
        modifier = Modifier
            .width(170.dp)
            .height(230.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF261830), CardBackground)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = series.title,
                    color = if (isFocused) NeonCyan else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${series.seasons.size} Saisons • ★ ${series.rating}",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}
