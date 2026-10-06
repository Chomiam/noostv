package io.noostv.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import io.noostv.ui.theme.*

enum class MobileBottomTab(val label: String, val icon: ImageVector) {
    LIVE("Direct", Icons.Default.Tv),
    MOVIES("Films", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary),
    EPG("Guide TV", Icons.Default.EventNote),
    FAVORITES("Favoris", Icons.Default.Favorite)
}

@Composable
fun MobileHomeScreen(
    channels: List<Channel>,
    movies: List<VodMovie>,
    series: List<Series>,
    entitlementManager: EntitlementManager,
    onSelectChannel: (Channel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenEpg: () -> Unit,
    onOpenUpgrade: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MobileBottomTab.LIVE) }
    var selectedCategory by remember { mutableStateOf("Tous") }
    val subscription by entitlementManager.subscription.collectAsState()

    val categories = remember(channels) {
        listOf("Tous") + channels.map { it.categoryName }.distinct()
    }

    val filteredChannels = remember(selectedCategory, channels) {
        if (selectedCategory == "Tous") channels else channels.filter { it.categoryName == selectedCategory }
    }

    Scaffold(
        containerColor = DarkOledBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "NOOS",
                        color = NeonCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "TV",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (subscription.isPremium) {
                        PremiumVipBadge()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Recherche", tint = TextPrimary)
                    }
                    if (!subscription.isPremium) {
                        IconButton(onClick = onOpenUpgrade) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = "VIP", tint = GoldVip)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextPrimary
            ) {
                MobileBottomTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            if (tab == MobileBottomTab.EPG) onOpenEpg()
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = NeonCyan.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                MobileBottomTab.LIVE -> {
                    // Carrousel de catégories tactiles
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            val isSelected = category == selectedCategory
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = SurfaceDark,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }

                    // Liste verticale des chaînes tactiles
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredChannels) { channel ->
                            MobileChannelItem(
                                channel = channel,
                                onClick = { onSelectChannel(channel) }
                            )
                        }
                    }
                }

                MobileBottomTab.MOVIES -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(movies) { movie ->
                            MobileMovieItem(movie = movie, onClick = { onSelectMovie(movie) })
                        }
                    }
                }

                MobileBottomTab.SERIES -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(series) { s ->
                            MobileSeriesItem(series = s)
                        }
                    }
                }

                MobileBottomTab.FAVORITES -> {
                    val favorites = channels.filter { it.isFavorite }
                    if (favorites.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aucune chaîne favorite pour le moment", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(favorites) { ch ->
                                MobileChannelItem(channel = ch, onClick = { onSelectChannel(ch) })
                            }
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
fun MobileChannelItem(channel: Channel, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = channel.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channel.isHdr) HdrBadge()
                }
                Text(
                    text = "${channel.categoryName} • ${channel.resolution} • ${channel.videoCodec.uppercase()}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.PlayCircleOutline,
                contentDescription = "Lecture",
                tint = NeonCyan,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun MobileMovieItem(movie: VodMovie, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp, 85.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDarkVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = TextSecondary)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${movie.releaseYear} • ★ ${movie.rating} • ${movie.durationFormatted}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (movie.isHdr) HdrBadge(text = movie.hdrFormat ?: "HDR")
                    Text(movie.resolution, color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MobileSeriesItem(series: Series) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp, 85.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF231630)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = PurpleHdr)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${series.seasons.size} Saisons • ★ ${series.rating}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
