package io.noostv.ui.mobile

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.noostv.core.player.PlayerEngine
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import io.noostv.core.localization.AppLanguage
import io.noostv.core.localization.AppStrings
import io.noostv.core.localization.LocalAppLanguage
import io.noostv.core.localization.LocalStrings
import io.noostv.data.model.UserProfile
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.NoosPaginationBar
import io.noostv.ui.common.PremiumVipBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.theme.*
import io.noostv.ui.tv.EpgProvider
import io.noostv.ui.tv.TvCategoryFiltersContent
import io.noostv.ui.tv.TvFavoritesContent
import io.noostv.ui.tv.TvProfileModal
import io.noostv.ui.tv.TvProfileButton
import io.noostv.ui.tv.TvLiveScheduleCard
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MobileBottomTab(val label: String, val icon: ImageVector) {
    TV("Direct", Icons.Default.Tv),
    MOVIES("Films", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary),
    FAVORITES("Favoris", Icons.Default.Star),
    FILTERS("Filtres", Icons.Default.Tune),
    EPG("Guide TV", Icons.Default.CalendarToday),
    SETTINGS("Paramètres", Icons.Default.Settings);

    fun getLabel(strings: AppStrings): String = when (this) {
        TV -> strings.navLiveTv
        MOVIES -> strings.navMovies
        SERIES -> strings.navSeries
        FAVORITES -> strings.navFavorites
        FILTERS -> strings.navFilters
        EPG -> strings.navEpg
        SETTINGS -> strings.navSettings
    }
}

@OptIn(UnstableApi::class)
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
    playerEngine: PlayerEngine? = null,
    onSelectChannel: (Channel) -> Unit,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit,
    onSelectVodCategory: (Category) -> Unit,
    onSelectSeriesCategory: (Category) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onSelectEpisode: ((Series, io.noostv.data.model.Episode) -> Unit)? = null,
    onFetchVodInfo: (suspend (String) -> VodMovie?)? = null,
    onFetchSeriesInfo: (suspend (String) -> Series?)? = null,
    onLanguageChanged: (AppLanguage) -> Unit = {},
    onLoadChannelEpg: ((Channel) -> Unit)? = null,
    onLoadChannelsBatch: ((List<Channel>) -> Unit)? = null,
    initialTab: MobileBottomTab = MobileBottomTab.TV,
    onTabSelected: (MobileBottomTab) -> Unit = {},
    initialVodCategory: String = "Toutes",
    onVodCategorySelected: (String) -> Unit = {},
    initialSeriesCategory: String = "Toutes",
    onSeriesCategorySelected: (String) -> Unit = {},
    initialMoviePage: Int = 1,
    onMoviePageChange: (Int) -> Unit = {},
    initialSeriesPage: Int = 1,
    onSeriesPageChange: (Int) -> Unit = {}
) {
    val strings = LocalStrings.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedLiveCategory by remember { mutableStateOf("Toutes") }
    var selectedVodCategory by remember { mutableStateOf(initialVodCategory) }
    var selectedSeriesCategory by remember { mutableStateOf(initialSeriesCategory) }

    var moviePage by remember { mutableIntStateOf(initialMoviePage) }
    var seriesPage by remember { mutableIntStateOf(initialSeriesPage) }

    val moviesGridState = rememberLazyGridState()
    val seriesGridState = rememberLazyGridState()

    var activeDetailMovie by remember { mutableStateOf<VodMovie?>(null) }
    var activeDetailSeries by remember { mutableStateOf<Series?>(null) }

    var activeProfile by remember { mutableStateOf(sessionManager.getActiveProfile()) }
    var isProfileModalOpen by remember { mutableStateOf(false) }

    var favoriteChannelIds by remember(activeProfile) { mutableStateOf(sessionManager.getFavoriteChannelIds()) }
    var favoriteMovieIds by remember(activeProfile) { mutableStateOf(sessionManager.getFavoriteMovieIds()) }
    var favoriteSeriesIds by remember(activeProfile) { mutableStateOf(sessionManager.getFavoriteSeriesIds()) }
    var hiddenVodIds by remember(activeProfile) { mutableStateOf(sessionManager.getHiddenVodCategoryIds()) }
    var hiddenSeriesIds by remember(activeProfile) { mutableStateOf(sessionManager.getHiddenSeriesCategoryIds()) }

    fun refreshProfileData(newProfile: UserProfile) {
        activeProfile = newProfile
        favoriteChannelIds = sessionManager.getFavoriteChannelIds()
        favoriteMovieIds = sessionManager.getFavoriteMovieIds()
        favoriteSeriesIds = sessionManager.getFavoriteSeriesIds()
        hiddenVodIds = sessionManager.getHiddenVodCategoryIds()
        hiddenSeriesIds = sessionManager.getHiddenSeriesCategoryIds()
    }

    val subscription by entitlementManager.subscription.collectAsState()

    // Pré-chargement des catégories si nécessaire
    LaunchedEffect(selectedTab) {
        if (selectedTab == MobileBottomTab.MOVIES && movies.isEmpty() && vodCategories.isNotEmpty()) {
            onSelectVodCategory(vodCategories.first())
        } else if (selectedTab == MobileBottomTab.SERIES && series.isEmpty() && seriesCategories.isNotEmpty()) {
            onSelectSeriesCategory(seriesCategories.first())
        }
    }

    val liveCategoryNames: List<String> = remember(categories, channels, favoriteChannelIds) {
        val list = mutableListOf("Toutes")
        if (favoriteChannelIds.isNotEmpty()) {
            list.add("⭐ Favoris (${favoriteChannelIds.size})")
        }
        if (categories.isNotEmpty()) {
            list.addAll(categories.map { it.name })
        } else {
            list.addAll(channels.map { it.categoryName }.distinct())
        }
        list
    }

    val filteredChannels = remember(channels, selectedLiveCategory, favoriteChannelIds) {
        if (selectedLiveCategory.startsWith("⭐ Favoris")) {
            channels.filter { favoriteChannelIds.contains(it.id) }
        } else if (selectedLiveCategory == "Toutes") {
            channels
        } else {
            channels.filter { it.categoryName.equals(selectedLiveCategory, ignoreCase = true) || it.categoryId == selectedLiveCategory }
        }
    }

    // Chargement automatique de l'EPG pour les premières chaînes de la catégorie affichée
    LaunchedEffect(filteredChannels, selectedLiveCategory) {
        if (filteredChannels.isNotEmpty()) {
            onLoadChannelsBatch?.invoke(filteredChannels.take(20))
        }
    }

    Scaffold(
        containerColor = DarkOledBackground,
        topBar = {
            if (!isLandscape) {
                MobileTopBar(
                    isPremium = subscription.isPremium,
                    activeProfile = activeProfile,
                    onOpenProfiles = { isProfileModalOpen = true },
                    onOpenSearch = onOpenSearch,
                    onOpenUpgrade = onOpenUpgrade,
                    onOpenLogin = onOpenLogin
                )
            }
        },
        bottomBar = {
            if (!isLandscape) {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                    MobileBottomTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        val tabLabel = tab.getLabel(strings)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedTab = tab
                                onTabSelected(tab)
                            },
                            alwaysShowLabel = false,
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tabLabel,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tabLabel,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
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
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLandscape) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary,
                    header = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        ) {
                            IconButton(
                                onClick = { isProfileModalOpen = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Profil",
                                    tint = NoosCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onOpenSearch,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Recherche",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MobileBottomTab.values().forEach { tab ->
                            val isSelected = selectedTab == tab
                            val tabLabel = tab.getLabel(strings)
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { selectedTab = tab },
                                alwaysShowLabel = false,
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tabLabel,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tabLabel,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
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
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (selectedTab) {
                    // ==================== 1. TV DIRECT ====================
                    MobileBottomTab.TV -> {
                        if (isLandscape && playerEngine != null) {
                            MobileLandscapeTvContent(
                                channels = filteredChannels,
                                categories = liveCategoryNames,
                                selectedCategory = selectedLiveCategory,
                                onSelectCategory = { selectedLiveCategory = it },
                                epgPrograms = epgPrograms,
                                playerEngine = playerEngine,
                                sessionManager = sessionManager,
                                favoriteChannelIds = favoriteChannelIds,
                                onToggleFavoriteChannel = { chId ->
                                    sessionManager.toggleFavoriteChannel(chId)
                                    val prof = sessionManager.getActiveProfile()
                                    refreshProfileData(prof)
                                },
                                onSelectChannel = onSelectChannel,
                                onLoadChannelEpg = onLoadChannelEpg
                            )
                        } else {
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
                                            val isFav = favoriteChannelIds.contains(channel.id)
                                            MobileChannelCard(
                                                channel = channel,
                                                currentProgram = currentProg,
                                                isFavorite = isFav,
                                                onToggleFavorite = {
                                                    sessionManager.toggleFavoriteChannel(channel.id)
                                                    val prof = sessionManager.getActiveProfile()
                                                    refreshProfileData(prof)
                                                },
                                                onClick = { onSelectChannel(channel) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                // ==================== 2. FILMS (VOD) ====================
                MobileBottomTab.MOVIES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val visibleVodCategories = remember(vodCategories, hiddenVodIds) {
                            vodCategories.filter { !hiddenVodIds.contains(it.id) }
                        }
                        val vodCatNames: List<String> = remember(visibleVodCategories) {
                            val list = mutableListOf("Toutes")
                            list.addAll(visibleVodCategories.map { it.name })
                            list
                        }

                        MobileCategoryChips(
                            categories = vodCatNames,
                            selectedCategory = selectedVodCategory,
                            onSelect = { catName ->
                                if (selectedVodCategory != catName) {
                                    selectedVodCategory = catName
                                    onVodCategorySelected(catName)
                                    moviePage = 1
                                    onMoviePageChange(1)
                                    val target = visibleVodCategories.firstOrNull { it.name == catName }
                                    if (target != null) onSelectVodCategory(target)
                                }
                            }
                        )

                        val moviePageSize = 24
                        val totalMoviePages = maxOf(1, ceil(movies.size.toDouble() / moviePageSize).toInt())
                        val pagedMovies = remember(movies, moviePage) {
                            movies.drop((moviePage - 1) * moviePageSize).take(moviePageSize)
                        }

                        if (isVodLoading) {
                            MobileLoadingState("Chargement des films...")
                        } else if (movies.isEmpty()) {
                            MobileEmptyState(message = "Aucun film disponible dans cette catégorie")
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LazyVerticalGrid(
                                    state = moviesGridState,
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(pagedMovies, key = { it.id }) { movie ->
                                        MobileVodCard(
                                            title = movie.title,
                                            posterUrl = movie.posterUrl,
                                            subtitle = "${movie.releaseYear ?: ""} • ★ ${movie.rating}",
                                            badge = if (movie.isHdr) movie.hdrFormat ?: "HDR" else movie.resolution,
                                            onClick = { activeDetailMovie = movie }
                                        )
                                    }
                                }

                                NoosPaginationBar(
                                    currentPage = moviePage,
                                    totalPages = totalMoviePages,
                                    totalItems = movies.size,
                                    itemLabel = "films",
                                    onPageChange = {
                                        moviePage = it
                                        onMoviePageChange(it)
                                        coroutineScope.launch {
                                            runCatching { moviesGridState.scrollToItem(0) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ==================== 3. SÉRIES ====================
                MobileBottomTab.SERIES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val visibleSeriesCategories = remember(seriesCategories, hiddenSeriesIds) {
                            seriesCategories.filter { !hiddenSeriesIds.contains(it.id) }
                        }
                        val seriesCatNames: List<String> = remember(visibleSeriesCategories) {
                            val list = mutableListOf("Toutes")
                            list.addAll(visibleSeriesCategories.map { it.name })
                            list
                        }

                        MobileCategoryChips(
                            categories = seriesCatNames,
                            selectedCategory = selectedSeriesCategory,
                            onSelect = { catName ->
                                if (selectedSeriesCategory != catName) {
                                    selectedSeriesCategory = catName
                                    onSeriesCategorySelected(catName)
                                    seriesPage = 1
                                    onSeriesPageChange(1)
                                    val target = visibleSeriesCategories.firstOrNull { it.name == catName }
                                    if (target != null) onSelectSeriesCategory(target)
                                }
                            }
                        )

                        val seriesPageSize = 24
                        val totalSeriesPages = maxOf(1, ceil(series.size.toDouble() / seriesPageSize).toInt())
                        val pagedSeries = remember(series, seriesPage) {
                            series.drop((seriesPage - 1) * seriesPageSize).take(seriesPageSize)
                        }

                        if (isSeriesLoading) {
                            MobileLoadingState("Chargement des séries...")
                        } else if (series.isEmpty()) {
                            MobileEmptyState(message = "Aucune série disponible dans cette catégorie")
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                LazyVerticalGrid(
                                    state = seriesGridState,
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(pagedSeries, key = { it.id }) { ser ->
                                        val seasonInfo = if (ser.seasons.isNotEmpty()) "${ser.seasons.size} Saison${if (ser.seasons.size > 1) "s" else ""} • " else ""
                                        MobileVodCard(
                                            title = ser.title,
                                            posterUrl = ser.posterUrl,
                                            subtitle = "${seasonInfo}★ ${ser.rating}",
                                            badge = "SERIES",
                                            onClick = { activeDetailSeries = ser }
                                        )
                                    }
                                }

                                NoosPaginationBar(
                                    currentPage = seriesPage,
                                    totalPages = totalSeriesPages,
                                    totalItems = series.size,
                                    itemLabel = "séries",
                                    onPageChange = {
                                        seriesPage = it
                                        onSeriesPageChange(it)
                                        coroutineScope.launch {
                                            runCatching { seriesGridState.scrollToItem(0) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ==================== 4. FAVORIS ====================
                MobileBottomTab.FAVORITES -> {
                    TvFavoritesContent(
                        channels = channels,
                        movies = movies,
                        series = series,
                        sessionManager = sessionManager,
                        onSelectChannel = onSelectChannel,
                        onSelectMovie = { activeDetailMovie = it },
                        onSelectSeries = { activeDetailSeries = it },
                        onFavoriteChanged = {
                            val prof = sessionManager.getActiveProfile()
                            refreshProfileData(prof)
                        }
                    )
                }

                // ==================== 5. FILTRES ====================
                MobileBottomTab.FILTERS -> {
                    TvCategoryFiltersContent(
                        vodCategories = vodCategories,
                        seriesCategories = seriesCategories,
                        sessionManager = sessionManager,
                        onFiltersUpdated = {
                            val prof = sessionManager.getActiveProfile()
                            refreshProfileData(prof)
                        }
                    )
                }

                // ==================== 6. GUIDE TV ====================
                MobileBottomTab.EPG -> {
                    MobileEpgContent(
                        channels = channels,
                        epgPrograms = epgPrograms,
                        onSelectChannel = onSelectChannel
                    )
                }

                // ==================== 7. PARAMÈTRES & OTA ====================
                MobileBottomTab.SETTINGS -> {
                    MobileSettingsView(
                        sessionManager = sessionManager,
                        onOpenLogin = onOpenLogin,
                        onLogout = onLogout,
                        onLanguageChanged = onLanguageChanged
                    )
                }
            }
        }
    }
}

    activeDetailMovie?.let { movie ->
        MobileMovieDetailModal(
            movie = movie,
            isFavorite = favoriteMovieIds.contains(movie.id),
            onDismiss = { activeDetailMovie = null },
            onPlay = { m ->
                activeDetailMovie = null
                onSelectMovie(m)
            },
            onToggleFavorite = { id ->
                sessionManager.toggleFavoriteMovie(id)
                val prof = sessionManager.getActiveProfile()
                refreshProfileData(prof)
            },
            onFetchFullInfo = { id -> onFetchVodInfo?.invoke(id) }
        )
    }

    activeDetailSeries?.let { ser ->
        MobileSeriesDetailModal(
            series = ser,
            isFavorite = favoriteSeriesIds.contains(ser.id),
            onDismiss = { activeDetailSeries = null },
            onPlayEpisode = { s, ep ->
                activeDetailSeries = null
                if (onSelectEpisode != null) {
                    onSelectEpisode(s, ep)
                } else {
                    onSelectSeries(s)
                }
            },
            onToggleFavorite = { id ->
                sessionManager.toggleFavoriteSeries(id)
                val prof = sessionManager.getActiveProfile()
                refreshProfileData(prof)
            },
            onFetchFullInfo = { id -> onFetchSeriesInfo?.invoke(id) }
        )
    }

    if (isProfileModalOpen) {
        TvProfileModal(
            sessionManager = sessionManager,
            onDismiss = { isProfileModalOpen = false },
            onProfileChanged = { newProfile ->
                refreshProfileData(newProfile)
                isProfileModalOpen = false
            }
        )
    }
}

/**
 * TopBar mobile moderne avec Logo officiel NOOS
 */
@Composable
private fun MobileTopBar(
    isPremium: Boolean,
    activeProfile: UserProfile,
    onOpenProfiles: () -> Unit,
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

        // Actions droites (Recherche & Profil)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SurfaceDarkVariant)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Recherche", tint = TextPrimary, modifier = Modifier.size(18.dp))
            }

            TvProfileButton(
                profile = activeProfile,
                onClick = onOpenProfiles
            )
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
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val backdropUrl = remember(channel.id, currentProgram.id) {
        EpgProvider.getProgramBackdrop(channel, currentProgram)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorderUnfocused, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Miniature 16:9 du programme en cours avec macaron logo de chaîne
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF131A26)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backdropUrl)
                        .crossfade(true)
                        .allowHardware(false)
                        .build(),
                    contentDescription = currentProgram.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Voile sombre subtil pour la lisibilité
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x22000000), Color(0xAA080A0F))
                            )
                        )
                )

                // Macaron du logo de la chaîne en coin inférieur gauche
                if (!channel.logoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(3.dp)
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xDD000000))
                            .border(0.5.dp, Color(0x44FFFFFF), RoundedCornerShape(6.dp))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(channel.logoUrl)
                                .crossfade(true)
                                .allowHardware(false)
                                .build(),
                            contentDescription = channel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

            // Boutons Favori et Play
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onToggleFavorite != null) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) GoldVip else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Lecture",
                    tint = NoosBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
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
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val strings = LocalStrings.current
    val currentLanguage = LocalAppLanguage.current

    var selectedChannel by remember { mutableStateOf(sessionManager.updateChannel) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val currentAppVersion = io.noostv.BuildConfig.VERSION_NAME

    fun checkUpdates(channel: String) {
        updateState = UpdateState.Checking
        coroutineScope.launch {
            val state = updateManager.checkForUpdates(
                channel = channel,
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
        Text(strings.settingsTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(strings.settingsSubtitle, color = TextSecondary, fontSize = 12.sp)

        // 1. Canal de mise à jour (Stable vs Testing)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.githubChannelTitle, color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.stableBranchTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(strings.stableBranchDesc, color = TextSecondary, fontSize = 11.sp)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.testingBranchTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(strings.testingBranchDesc, color = TextSecondary, fontSize = 11.sp)
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
                    Text("${strings.checkUpdates} ($selectedChannel)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Statut de la vérification
                when (val state = updateState) {
                    is UpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NoosCyan, strokeWidth = 2.dp)
                            Text(strings.checkingUpdates, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                    is UpdateState.UpToDate -> {
                        Text("✅ ${strings.noUpdateAvailable}", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    is UpdateState.UpdateAvailable -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🎉 ${strings.updateAvailable} ${state.release.title.ifBlank { state.release.tag }}", color = NoosCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        updateState = UpdateState.Downloading(0, 0, state.release.apkSizeBytes)
                                        val dlResult = updateManager.downloadApk(
                                            release = state.release,
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
                                Text("${strings.installUpdate} (${"%.1f".format(state.release.apkSizeMb)} Mo)")
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

        // 2. Langue de l'interface avec drapeaux
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(strings.languageSectionTitle, color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(strings.languageSectionSubtitle, color = TextSecondary, fontSize = 11.sp)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        val isSelected = currentLanguage == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NoosBlue.copy(alpha = 0.2f) else SurfaceDarkVariant)
                                .border(1.dp, if (isSelected) NoosCyan else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { onLanguageChanged(lang) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x22FFFFFF))
                                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(lang.flagEmoji, fontSize = 18.sp)
                                }
                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        color = if (isSelected) Color.White else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = lang.displayName,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onLanguageChanged(lang) },
                                colors = RadioButtonDefaults.colors(selectedColor = NoosCyan)
                            )
                        }
                    }
                }
            }
        }

        // 3. Compte & Serveur IPTV
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.subscriptionSectionTitle, color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.server, color = TextSecondary, fontSize = 12.sp)
                    Text(sessionManager.getMaskedServerUrl(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.username, color = TextSecondary, fontSize = 12.sp)
                    Text(sessionManager.getMaskedUsername(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.logout, fontSize = 12.sp)
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

/**
 * Vue split optimisée pour smartphone en mode horizontal (paysage) :
 * - Colonne gauche : Catégories et liste compacte des chaînes TV
 * - Colonne droite : Lecteur vidéo 16:9 de prévisualisation (720p / 2.5 Mbps) + Guide TV complet sous le lecteur
 */
@OptIn(UnstableApi::class)
@Composable
private fun MobileLandscapeTvContent(
    channels: List<Channel>,
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    epgPrograms: List<EpgProgram>,
    playerEngine: PlayerEngine,
    sessionManager: SessionManager? = null,
    favoriteChannelIds: Set<String> = emptySet(),
    onToggleFavoriteChannel: ((String) -> Unit)? = null,
    onSelectChannel: (Channel) -> Unit,
    onLoadChannelEpg: ((Channel) -> Unit)? = null
) {
    var previewChannel by remember(channels) {
        mutableStateOf(channels.firstOrNull())
    }

    LaunchedEffect(channels) {
        if (previewChannel == null || !channels.any { it.id == previewChannel?.id }) {
            previewChannel = channels.firstOrNull()
        }
    }

    // Debounce zapping de 250ms pour fluidité absolue et zéro freeze
    LaunchedEffect(previewChannel?.id) {
        val ch = previewChannel ?: return@LaunchedEffect
        delay(250)
        onLoadChannelEpg?.invoke(ch)
        playerEngine.playStream(
            url = ch.streamUrl,
            title = ch.name,
            isHdrStream = ch.isHdr,
            is4K = ch.resolution.contains("4K"),
            isPreview = true
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            playerEngine.stop()
        }
    }

    val activeChannel = previewChannel

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ========== COLONNE GAUCHE (LISTE DES CHAÎNES & CATÉGORIES) ==========
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
        ) {
            // Catégories compactes
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) NoosBlue else SurfaceDarkVariant)
                            .clickable { onSelectCategory(cat) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (channels.isEmpty()) {
                MobileEmptyState(message = "Aucune chaîne disponible")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    items(channels, key = { it.id }) { channel ->
                        val isSelected = channel.id == activeChannel?.id
                        val isFav = favoriteChannelIds.contains(channel.id)
                        val currentProg = EpgProvider.getCurrentProgram(channel, epgPrograms)
                        MobileLandscapeChannelListItem(
                            channel = channel,
                            currentProgram = currentProg,
                            isSelected = isSelected,
                            isFavorite = isFav,
                            onClick = {
                                if (isSelected) {
                                    onSelectChannel(channel)
                                } else {
                                    previewChannel = channel
                                }
                            }
                        )
                    }
                }
            }
        }

        // ========== COLONNE DROITE (PRÉVISUALISATION 16:9 + GUIDE TV EN DESSOUS) ==========
        if (activeChannel != null) {
            val channelSchedule = remember(activeChannel.id, epgPrograms) {
                EpgProvider.getChannelSchedule(activeChannel, epgPrograms)
            }
            val currentLiveProgram = remember(activeChannel.id, epgPrograms) {
                EpgProvider.getCurrentProgram(activeChannel, epgPrograms)
            }

            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Lecteur vidéo 16:9 de prévisualisation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .clickable { onSelectChannel(activeChannel) }
                        .border(1.dp, CardBorderUnfocused, RoundedCornerShape(14.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = playerEngine.exoPlayer
                            }
                        },
                        update = { pv ->
                            if (pv.player != playerEngine.exoPlayer) {
                                pv.player = playerEngine.exoPlayer
                            }
                        }
                    )

                    // Superposition : Nom de la chaîne + Badges + Bouton Plein Écran
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x99080A0F),
                                        Color.Transparent,
                                        Color(0xDD080A0F)
                                    )
                                )
                            )
                            .padding(8.dp)
                    ) {
                        // En-tête : Badge Live + Nom + Favori + Résolution
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LiveIndicatorBadge()
                                Text(
                                    text = activeChannel.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isFav = favoriteChannelIds.contains(activeChannel.id)
                                if (onToggleFavoriteChannel != null) {
                                    IconButton(
                                        onClick = { onToggleFavoriteChannel(activeChannel.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Favori",
                                            tint = if (isFav) GoldVip else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (activeChannel.isHdr) HdrBadge() else ResolutionBadge(resolution = activeChannel.resolution)
                            }
                        }

                        // Bas : Bouton Plein écran cliquable
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xAA000000))
                                .border(1.dp, NoosCyan, RoundedCornerShape(50))
                                .clickable { onSelectChannel(activeChannel) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "Plein écran",
                                tint = NoosCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Plein écran",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. Guide TV de la chaîne en dessous de la prévisualisation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDark)
                        .border(1.dp, CardBorderUnfocused, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = NoosCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "GUIDE TV • ${activeChannel.name.uppercase()}",
                                color = NoosCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            TvLiveScheduleCard(
                                program = currentLiveProgram,
                                isLiveNow = true
                            )
                        }

                        val upcoming = channelSchedule.filter { it.id != currentLiveProgram.id && it.startEpochMs >= currentLiveProgram.startEpochMs }
                        itemsIndexed(upcoming) { _, prog ->
                            TvLiveScheduleCard(
                                program = prog,
                                isLiveNow = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Élément de liste de chaîne compact pour smartphone en mode paysage
 */
@Composable
private fun MobileLandscapeChannelListItem(
    channel: Channel,
    currentProgram: EpgProgram,
    isSelected: Boolean,
    isFavorite: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) NoosBlue.copy(alpha = 0.3f) else SurfaceDarkVariant)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) NoosCyan else CardBorderUnfocused,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Logo de la chaîne
        if (!channel.logoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(channel.logoUrl)
                        .crossfade(true)
                        .allowHardware(false)
                        .build(),
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = NoosCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = channel.name,
                        color = if (isSelected) Color.White else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favori",
                            tint = GoldVip,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
                if (isSelected) {
                    Text(
                        text = "EN VUE",
                        color = NoosCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = currentProgram.title,
                color = if (isSelected) NoosCyan else TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
