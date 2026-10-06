package io.noostv.ui.mobile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noostv.R
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.storage.SessionManager
import io.noostv.core.update.UpdateManager
import io.noostv.core.update.UpdateState
import io.noostv.data.model.Category
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.PremiumVipBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.theme.*
import io.noostv.ui.tv.EpgProvider
import kotlinx.coroutines.launch

enum class MobileBottomTab(val label: String, val icon: ImageVector) {
    TV("Direct", Icons.Default.Tv),
    MOVIES("Films", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary),
    EPG("Guide TV", Icons.Default.CalendarToday),
    SETTINGS("Paramètres", Icons.Default.Settings)
}

@Composable
fun MobileHomeScreen(
    channels: List<Channel>,
    movies: List<VodMovie>,
    series: List<Series>,
    epgPrograms: List<EpgProgram>,
    categories: List<Category>,
    vodCategories: List<Category>,
    seriesCategories: List<Category>,
    isVodLoading: Boolean = false,
    isSeriesLoading: Boolean = false,
    sessionManager: SessionManager,
    entitlementManager: EntitlementManager,
    onSelectChannel: (Channel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit,
    onSelectVodCategory: (Category) -> Unit,
    onSelectSeriesCategory: (Category) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenLogin: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MobileBottomTab.TV) }
    var selectedLiveCategory by remember { mutableStateOf("Toutes") }
    var selectedVodCategory by remember { mutableStateOf("Toutes") }
    var selectedSeriesCategory by remember { mutableStateOf("Toutes") }

    val subscription by entitlementManager.subscription.collectAsState()

    // Pré-chargement des catégories si nécessaire
    LaunchedEffect(selectedTab) {
        if (selectedTab == MobileBottomTab.MOVIES && movies.isEmpty() && vodCategories.isNotEmpty()) {
            onSelectVodCategory(vodCategories.first())
        } else if (selectedTab == MobileBottomTab.SERIES && series.isEmpty() && seriesCategories.isNotEmpty()) {
            onSelectSeriesCategory(seriesCategories.first())
        }
    }

    val liveCategoryNames: List<String> = remember(categories, channels) {
        val list = mutableListOf("Toutes")
        if (categories.isNotEmpty()) {
            list.addAll(categories.map { it.name })
        } else {
            list.addAll(channels.map { it.categoryName }.distinct())
        }
        list
    }

    val filteredChannels = remember(channels, selectedLiveCategory) {
        if (selectedLiveCategory == "Toutes") {
            channels
        } else {
            channels.filter { it.categoryName.equals(selectedLiveCategory, ignoreCase = true) || it.categoryId == selectedLiveCategory }
        }
    }

    Scaffold(
        containerColor = DarkOledBackground,
        topBar = {
            MobileTopBar(
                isPremium = subscription.isPremium,
                onOpenSearch = onOpenSearch,
                onOpenUpgrade = onOpenUpgrade,
                onOpenLogin = onOpenLogin
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                contentColor = TextPrimary,
                tonalElevation = 8.dp
            ) {
                MobileBottomTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NoosCyan,
                            selectedTextColor = NoosCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color(0x2200E5FF)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                // ==================== 1. TV DIRECT ====================
                MobileBottomTab.TV -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Chips de filtrage horizontal
                        MobileCategoryChips(
                            categories = liveCategoryNames,
                            selectedCategory = selectedLiveCategory,
                            onSelect = { selectedLiveCategory = it }
                        )

                        if (filteredChannels.isEmpty()) {
                            MobileEmptyState(message = "Aucune chaîne disponible dans cette catégorie")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredChannels, key = { it.id }) { channel ->
                                    val currentProg = EpgProvider.getCurrentProgram(channel, epgPrograms)
                                    MobileChannelCard(
                                        channel = channel,
                                        currentProgram = currentProg,
                                        onClick = { onSelectChannel(channel) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 2. FILMS (VOD) ====================
                MobileBottomTab.MOVIES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val vodCatNames: List<String> = remember(vodCategories) {
                            val list = mutableListOf("Toutes")
                            list.addAll(vodCategories.map { it.name })
                            list
                        }

                        MobileCategoryChips(
                            categories = vodCatNames,
                            selectedCategory = selectedVodCategory,
                            onSelect = { catName ->
                                selectedVodCategory = catName
                                val target = vodCategories.firstOrNull { it.name == catName }
                                if (target != null) onSelectVodCategory(target)
                            }
                        )

                        if (isVodLoading) {
                            MobileLoadingState("Chargement des films...")
                        } else if (movies.isEmpty()) {
                            MobileEmptyState(message = "Aucun film disponible dans cette catégorie")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(movies, key = { it.id }) { movie ->
                                    MobileVodCard(
                                        title = movie.title,
                                        posterUrl = movie.posterUrl,
                                        subtitle = "${movie.releaseYear} • ★ ${movie.rating}",
                                        badge = if (movie.isHdr) movie.hdrFormat ?: "HDR" else movie.resolution,
                                        onClick = { onSelectMovie(movie) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 3. SÉRIES ====================
                MobileBottomTab.SERIES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val seriesCatNames: List<String> = remember(seriesCategories) {
                            val list = mutableListOf("Toutes")
                            list.addAll(seriesCategories.map { it.name })
                            list
                        }

                        MobileCategoryChips(
                            categories = seriesCatNames,
                            selectedCategory = selectedSeriesCategory,
                            onSelect = { catName ->
                                selectedSeriesCategory = catName
                                val target = seriesCategories.firstOrNull { it.name == catName }
                                if (target != null) onSelectSeriesCategory(target)
                            }
                        )

                        if (isSeriesLoading) {
                            MobileLoadingState("Chargement des séries...")
                        } else if (series.isEmpty()) {
                            MobileEmptyState(message = "Aucune série disponible dans cette catégorie")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(series, key = { it.id }) { ser ->
                                    MobileVodCard(
                                        title = ser.title,
                                        posterUrl = ser.posterUrl,
                                        subtitle = "${ser.seasons.size} Saisons • ★ ${ser.rating}",
                                        badge = "SERIES",
                                        onClick = { onSelectSeries(ser) }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 4. GUIDE TV ====================
                MobileBottomTab.EPG -> {
                    MobileEpgContent(
                        channels = channels,
                        epgPrograms = epgPrograms,
                        onSelectChannel = onSelectChannel
                    )
                }

                // ==================== 5. PARAMÈTRES & OTA ====================
                MobileBottomTab.SETTINGS -> {
                    MobileSettingsView(
                        sessionManager = sessionManager,
                        onOpenLogin = onOpenLogin
                    )
                }
            }
        }
    }
}

/**
 * TopBar mobile moderne avec Logo officiel NOOS
 */
@Composable
private fun MobileTopBar(
    isPremium: Boolean,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenLogin: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo officiel NOOS
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.noos_logo),
                contentDescription = "NoosTV",
                modifier = Modifier.height(28.dp),
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF3888FF))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("TV", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }

            if (isPremium) {
                PremiumVipBadge()
            }
        }

        // Actions droites (Recherche & Compte)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceDarkVariant)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Recherche", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }

            IconButton(
                onClick = onOpenLogin,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceDarkVariant)
            ) {
                Icon(imageVector = Icons.Default.Dns, contentDescription = "Serveur IPTV", tint = NoosCyan, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Rangée horizontale de puces de catégories
 */
@Composable
private fun MobileCategoryChips(
    categories: List<String>,
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { cat ->
            val isSelected = cat == selectedCategory
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) NoosBlue else SurfaceDarkVariant)
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = cat,
                    color = if (isSelected) Color.White else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Carte de chaîne TV en direct sur mobile avec logo, live program, jauge horaire
 */
@Composable
private fun MobileChannelCard(
    channel: Channel,
    currentProgram: EpgProgram,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorderUnfocused, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Logo de la chaîne
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDarkVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .allowHardware(false)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                } else {
                    Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(24.dp))
                }
            }

            // Détails du programme en direct
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LiveIndicatorBadge()
                        if (channel.isHdr) HdrBadge() else ResolutionBadge(resolution = channel.resolution)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Titre du programme en cours
                Text(
                    text = currentProgram.title,
                    color = NoosCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Plage horaire + jauge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = currentProgram.timeSlotFormatted,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )

                    LinearProgressIndicator(
                        progress = { currentProgram.progressFraction() },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50)),
                        color = NoosCyan,
                        trackColor = Color(0x3300E5FF)
                    )
                }
            }

            // Bouton play
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Lecture",
                tint = NoosBlue,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Carte VOD (Films et Séries) au format affiche 2:3 pour mobile
 */
@Composable
private fun MobileVodCard(
    title: String,
    posterUrl: String?,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorderUnfocused, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        // Image de l'affiche
        if (!posterUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceDarkVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
            }
        }

        // Dégradé sombre inférieur pour texte
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xAA080A0F), Color(0xF0080A0F)),
                        startY = 150f
                    )
                )
                .padding(10.dp)
        ) {
            badge?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = it, color = NoosCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Vue mobile du Guide TV interactif
 */
@Composable
private fun MobileEpgContent(
    channels: List<Channel>,
    epgPrograms: List<EpgProgram>,
    onSelectChannel: (Channel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GUIDE TV INTERACTIF",
                color = NoosCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(text = "En direct & À suivre", color = TextSecondary, fontSize = 11.sp)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                val schedule = EpgProvider.getChannelSchedule(channel, epgPrograms)
                val current = EpgProvider.getCurrentProgram(channel, epgPrograms)
                val upcoming = schedule.filter { it.id != current.id && it.startEpochMs >= current.startEpochMs }.take(2)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // En-tête chaîne
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectChannel(channel) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(channel.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                LiveIndicatorBadge()
                            }
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Regarder", tint = NoosCyan, modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Programme en direct
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x223888FF))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(current.timeSlotFormatted, color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("EN CE MOMENT", color = NoosCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                                Text(current.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { current.progressFraction() },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)),
                                    color = NoosCyan,
                                    trackColor = Color(0x3300E5FF)
                                )
                            }
                        }

                        // Programmes suivants
                        if (upcoming.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("À SUIVRE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            upcoming.forEach { prog ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(prog.title, color = TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text(prog.timeSlotFormatted, color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vue mobile des Paramètres avec sélecteur de canal Stable/Testing et mises à jour OTA
 */
@Composable
private fun MobileSettingsView(
    sessionManager: SessionManager,
    onOpenLogin: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    var selectedChannel by remember { mutableStateOf(sessionManager.updateChannel) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val currentAppVersion = io.noostv.BuildConfig.VERSION_NAME

    fun checkUpdates(channel: String) {
        updateState = UpdateState.Checking
        coroutineScope.launch {
            val state = updateManager.checkForUpdates(
                channel = channel,
                token = sessionManager.githubToken,
                currentVersion = currentAppVersion
            )
            updateState = state
        }
    }

    LaunchedEffect(selectedChannel) {
        checkUpdates(selectedChannel)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // En-tête
        Text("Paramètres & Mises à Jour", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Canaux GitHub Releases et diagnostic système", color = TextSecondary, fontSize = 12.sp)

        // 1. Canal de mise à jour (Stable vs Testing)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CANAL DE DIFFUSION DES VERSIONS", color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                // Option Stable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedChannel == "stable") NoosBlue.copy(alpha = 0.2f) else SurfaceDarkVariant)
                        .border(1.dp, if (selectedChannel == "stable") NoosBlue else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable {
                            selectedChannel = "stable"
                            sessionManager.updateChannel = "stable"
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Branche Stable (Recommandé)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Versions testées et validées en production", color = TextSecondary, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = selectedChannel == "stable",
                        onClick = {
                            selectedChannel = "stable"
                            sessionManager.updateChannel = "stable"
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NoosCyan)
                    )
                }

                // Option Testing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedChannel == "testing") NoosBlue.copy(alpha = 0.2f) else SurfaceDarkVariant)
                        .border(1.dp, if (selectedChannel == "testing") NoosBlue else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable {
                            selectedChannel = "testing"
                            sessionManager.updateChannel = "testing"
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Branche Testing (Bêta)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Dernières fonctionnalités en avant-première", color = TextSecondary, fontSize = 11.sp)
                    }
                    RadioButton(
                        selected = selectedChannel == "testing",
                        onClick = {
                            selectedChannel = "testing"
                            sessionManager.updateChannel = "testing"
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = NoosCyan)
                    )
                }

                // Bouton de vérification
                Button(
                    onClick = { checkUpdates(selectedChannel) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NoosBlue)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vérifier les mises à jour ($selectedChannel)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Statut de la vérification
                when (val state = updateState) {
                    is UpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NoosCyan, strokeWidth = 2.dp)
                            Text("Recherche de nouvelles versions...", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    is UpdateState.UpToDate -> {
                        Text("✅ Votre application est à jour ($currentAppVersion)", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    is UpdateState.UpdateAvailable -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🎉 Mise à jour disponible : v${state.release.version}", color = NoosCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        updateState = UpdateState.Downloading(0, 0, state.release.apkSizeBytes)
                                        val dlResult = updateManager.downloadApk(
                                            release = state.release,
                                            token = sessionManager.githubToken,
                                            onProgress = { pct, dl, tot ->
                                                updateState = UpdateState.Downloading(pct, dl, tot)
                                            }
                                        )
                                        dlResult.fold(
                                            onSuccess = { file ->
                                                updateState = UpdateState.ReadyToInstall(file)
                                                updateManager.installApk(file)
                                            },
                                            onFailure = { err ->
                                                updateState = UpdateState.Error(err.localizedMessage ?: "Erreur de téléchargement")
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
                            ) {
                                Text("Installer la mise à jour (${state.release.apkSizeMb.toInt()} Mo)")
                            }
                        }
                    }
                    is UpdateState.Error -> {
                        Text("ℹ️ ${state.message}", color = TextSecondary, fontSize = 12.sp)
                    }
                    else -> {}
                }
            }
        }

        // 2. Compte & Serveur IPTV
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SESSION IPTV XTREAM CODES", color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Serveur", color = TextSecondary, fontSize = 12.sp)
                    Text(sessionManager.serverUrl.ifBlank { "Mode Démo" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Utilisateur", color = TextSecondary, fontSize = 12.sp)
                    Text(sessionManager.username.ifBlank { "Invité" }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onOpenLogin,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NoosCyan)
                ) {
                    Icon(imageVector = Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Changer d'identifiants IPTV", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun MobileEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(imageVector = Icons.Default.Inbox, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
            Text(text = message, color = TextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MobileLoadingState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = NoosCyan, modifier = Modifier.size(36.dp))
            Text(text = message, color = TextSecondary, fontSize = 14.sp)
        }
    }
}
