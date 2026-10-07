package io.noostv.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noostv.R
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.player.PlayerEngine
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Category
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.data.model.Episode
import io.noostv.data.model.Season
import io.noostv.data.model.Series
import io.noostv.data.model.UserProfile
import io.noostv.data.model.VodMovie
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.NoosPaginationBar
import io.noostv.core.localization.AppLanguage
import io.noostv.core.localization.AppStrings
import io.noostv.core.localization.LocalStrings
import io.noostv.ui.common.PremiumVipBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.settings.TvSettingsContent
import io.noostv.ui.theme.*
import kotlin.math.ceil

/**
 * 7 Catégories latérales :
 * 1. TV (Direct)
 * 2. Guide TV (EPG)
 * 3. Films (VOD Films)
 * 4. Séries (VOD Séries)
 * 5. Favoris (Épingles chaînes, films, séries)
 * 6. Filtres (Visibilité des catégories)
 * 7. Paramètres (Gestion système & Mises à jour GitHub)
 */
enum class TvNavTab(val label: String, val icon: ImageVector) {
    TV("TV", Icons.Default.Tv),
    EPG("Guide TV", Icons.Default.DateRange),
    MOVIES("Films", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary),
    FAVORITES("Favoris", Icons.Default.Star),
    FILTERS("Filtres", Icons.Default.Tune),
    SETTINGS("Paramètres", Icons.Default.Settings);

    fun getLabel(strings: AppStrings): String = when (this) {
        TV -> strings.navLiveTv
        EPG -> strings.navEpg
        MOVIES -> strings.navMovies
        SERIES -> strings.navSeries
        FAVORITES -> strings.navFavorites
        FILTERS -> strings.navFilters
        SETTINGS -> strings.navSettings
    }
}

@Composable
fun TvHomeScreen(
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
    initialPreviewChannel: Channel? = null,
    onClearCurrentChannel: (() -> Unit)? = null,
    onSelectChannel: (Channel) -> Unit,
    onLoadChannelEpg: ((Channel) -> Unit)? = null,
    onSelectMovie: (VodMovie) -> Unit,
    onSelectSeries: (Series) -> Unit,
    onSelectEpisode: (Series, io.noostv.data.model.Episode) -> Unit = { ser, _ -> onSelectSeries(ser) },
    onSelectVodCategory: (Category) -> Unit,
    onSelectSeriesCategory: (Category) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onFetchVodInfo: (suspend (String) -> VodMovie?)? = null,
    onFetchSeriesInfo: (suspend (String) -> Series?)? = null,
    onLanguageChanged: (AppLanguage) -> Unit = {}
) {
    val strings = LocalStrings.current
    var selectedTab by remember { mutableStateOf(TvNavTab.TV) }
    var selectedLiveCategory by remember { mutableStateOf("Toutes") }
    var selectedVodCategory by remember { mutableStateOf("Toutes") }
    var selectedSeriesCategory by remember { mutableStateOf("Toutes") }
    var previewChannel by remember { mutableStateOf(initialPreviewChannel) }

    var activeProfile by remember { mutableStateOf(sessionManager.getActiveProfile()) }
    var isProfileModalOpen by remember { mutableStateOf(false) }

    var selectedMovieDetail by remember { mutableStateOf<VodMovie?>(null) }
    var selectedSeriesDetail by remember { mutableStateOf<Series?>(null) }
    var favoriteMovieIds by remember { mutableStateOf(sessionManager.getFavoriteMovieIds()) }
    var favoriteSeriesIds by remember { mutableStateOf(sessionManager.getFavoriteSeriesIds()) }

    // Interception de la touche Retour télécommande pour fermer les modales de détail, modale profil ou le mode prévisualisation
    BackHandler(enabled = isProfileModalOpen || selectedMovieDetail != null || selectedSeriesDetail != null || previewChannel != null) {
        if (isProfileModalOpen) {
            isProfileModalOpen = false
        } else if (selectedMovieDetail != null) {
            selectedMovieDetail = null
        } else if (selectedSeriesDetail != null) {
            selectedSeriesDetail = null
        } else if (previewChannel != null) {
            playerEngine?.stop()
            previewChannel = null
            onClearCurrentChannel?.invoke()
        }
    }

    val subscription by entitlementManager.subscription.collectAsState()

    // Focus Requesters pour navigation fluide télécommande D-Pad
    val sidebarFocusRequesters = remember {
        mapOf(
            TvNavTab.TV to FocusRequester(),
            TvNavTab.EPG to FocusRequester(),
            TvNavTab.MOVIES to FocusRequester(),
            TvNavTab.SERIES to FocusRequester(),
            TvNavTab.FAVORITES to FocusRequester(),
            TvNavTab.FILTERS to FocusRequester(),
            TvNavTab.SETTINGS to FocusRequester()
        )
    }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        sidebarFocusRequesters[TvNavTab.TV]?.requestFocus()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != TvNavTab.TV && previewChannel != null) {
            playerEngine?.stop()
            previewChannel = null
            onClearCurrentChannel?.invoke()
        }
        if (selectedTab == TvNavTab.MOVIES && movies.isEmpty() && vodCategories.isNotEmpty()) {
            onSelectVodCategory(vodCategories.first())
        } else if (selectedTab == TvNavTab.SERIES && series.isEmpty() && seriesCategories.isNotEmpty()) {
            onSelectSeriesCategory(seriesCategories.first())
        }
    }

    // Persistance et suivi des chaînes favorites
    var favoriteIds by remember { mutableStateOf(sessionManager.getFavoriteChannelIds()) }

    // Filtrage des chaînes en direct selon la catégorie sélectionnée
    val filteredChannels = remember(channels, selectedLiveCategory, favoriteIds) {
        if (selectedLiveCategory.startsWith("⭐ Favoris")) {
            channels.filter { favoriteIds.contains(it.id) }
        } else if (selectedLiveCategory == "Toutes") {
            channels
        } else {
            channels.filter { it.categoryName.equals(selectedLiveCategory, ignoreCase = true) || it.categoryId == selectedLiveCategory }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassMeshBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
        // ==================== BARRE LATÉRALE GAUCHE MODERNE & ARRONDIE ====================
        TvSidebar(
            selectedTab = selectedTab,
            sidebarFocusRequesters = sidebarFocusRequesters,
            onTabSelected = { tab ->
                selectedTab = tab
            },
            onNavigateRight = {
                runCatching { contentFocusRequester.requestFocus() }
            }
        )

        // ==================== CONTENU PRINCIPAL SELON L'ONGLET ACTIF ====================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 18.dp, end = 24.dp)
        ) {
            // Header supérieur (Logo officiel NOOS, statut VIP, Recherche, Profil & Identifiants)
            TvHeader(
                isPremium = subscription.isPremium,
                activeProfile = activeProfile,
                onOpenProfileModal = { isProfileModalOpen = true },
                onOpenSearch = onOpenSearch,
                onOpenUpgrade = onOpenUpgrade,
                onLogout = onLogout
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                // ==================== 1. ONGLET TV (DIRECT) ====================
                TvNavTab.TV -> {
                    if (previewChannel != null && playerEngine != null) {
                        TvChannelPreviewContent(
                            channels = filteredChannels,
                            selectedChannel = previewChannel!!,
                            epgPrograms = epgPrograms,
                            playerEngine = playerEngine,
                            sessionManager = sessionManager,
                            contentFocusRequester = contentFocusRequester,
                            onChannelChanged = { newChan ->
                                previewChannel = newChan
                                onLoadChannelEpg?.invoke(newChan)
                            },
                            onOpenFullscreen = { chan ->
                                onSelectChannel(chan)
                            },
                            onClosePreview = {
                                playerEngine.stop()
                                previewChannel = null
                                onClearCurrentChannel?.invoke()
                            },
                            onNavigateLeftToSidebar = {
                                runCatching { sidebarFocusRequesters[TvNavTab.TV]?.requestFocus() }
                            },
                            onFavoriteToggled = {
                                favoriteIds = sessionManager.getFavoriteChannelIds()
                            }
                        )
                    } else {
                        val liveCatNames = remember(categories, channels, favoriteIds) {
                            val list = mutableListOf("Toutes")
                            if (favoriteIds.isNotEmpty()) {
                                list.add("⭐ Favoris (${favoriteIds.size})")
                            }
                            list.addAll(if (categories.isNotEmpty()) categories.map { it.name } else channels.map { it.categoryName }.distinct())
                            list
                        }

                        TvCategoryChipsRow(
                            categories = liveCatNames,
                            selectedCategory = selectedLiveCategory,
                            focusRequester = contentFocusRequester,
                            onNavigateLeft = { sidebarFocusRequesters[TvNavTab.TV]?.requestFocus() },
                            onSelectCategory = { selectedLiveCategory = it }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (filteredChannels.isEmpty()) {
                            TvEmptyState(message = "Aucune chaîne disponible dans cette catégorie")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 32.dp)
                            ) {
                                items(filteredChannels.size) { index ->
                                    val channel = filteredChannels[index]
                                    val currentProg = EpgProvider.getCurrentProgram(channel, epgPrograms)

                                    TvChannelGridCard(
                                        channel = channel,
                                        currentProgram = currentProg,
                                        isFavorite = favoriteIds.contains(channel.id),
                                        onNavigateLeft = if (index % 4 == 0) {
                                            { sidebarFocusRequesters[TvNavTab.TV]?.requestFocus() }
                                        } else null,
                                        onClick = {
                                            previewChannel = channel
                                            onLoadChannelEpg?.invoke(channel)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== 2. ONGLET GUIDE TV (EPG) ====================
                TvNavTab.EPG -> {
                    TvEpgContent(
                        channels = channels,
                        epgPrograms = epgPrograms,
                        focusRequester = contentFocusRequester,
                        onNavigateLeft = { sidebarFocusRequesters[TvNavTab.EPG]?.requestFocus() },
                        onSelectChannel = onSelectChannel
                    )
                }

                // ==================== 3. ONGLET FILMS (VOD) — RATIO CINÉMA 2:3 ====================
                TvNavTab.MOVIES -> {
                    val visibleVodCategories = remember(vodCategories, activeProfile) {
                        vodCategories.filter { sessionManager.isVodCategoryVisible(it.id) }
                    }
                    val vodCatNames = remember(visibleVodCategories) {
                        listOf("Toutes") + visibleVodCategories.map { it.name }
                    }

                    TvCategoryChipsRow(
                        categories = vodCatNames,
                        selectedCategory = selectedVodCategory,
                        focusRequester = contentFocusRequester,
                        onNavigateLeft = { sidebarFocusRequesters[TvNavTab.MOVIES]?.requestFocus() },
                        onSelectCategory = { catName ->
                            selectedVodCategory = catName
                            val found = vodCategories.find { it.name == catName }
                            if (found != null) {
                                onSelectVodCategory(found)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val moviePageSize = 36
                    var moviePage by remember(selectedVodCategory) { mutableIntStateOf(1) }
                    val totalMoviePages = maxOf(1, ceil(movies.size.toDouble() / moviePageSize).toInt())
                    val pagedMovies = remember(movies, moviePage) {
                        movies.drop((moviePage - 1) * moviePageSize).take(moviePageSize)
                    }

                    if (isVodLoading) {
                        TvLoadingProgress(message = "Chargement des films de $selectedVodCategory...")
                    } else if (movies.isEmpty()) {
                        TvEmptyState(message = "Aucun film disponible dans cette catégorie")
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(6),
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(pagedMovies.size) { index ->
                                    val movie = pagedMovies[index]
                                    TvMovieGridCard(
                                        movie = movie,
                                        onNavigateLeft = if (index % 6 == 0) {
                                            { sidebarFocusRequesters[TvNavTab.MOVIES]?.requestFocus() }
                                        } else null,
                                        onClick = { selectedMovieDetail = movie }
                                    )
                                }
                            }

                            NoosPaginationBar(
                                currentPage = moviePage,
                                totalPages = totalMoviePages,
                                totalItems = movies.size,
                                itemLabel = "films",
                                onPageChange = { moviePage = it },
                                isTv = true
                            )
                        }
                    }
                }

                // ==================== 4. ONGLET SÉRIES (VOD) — RATIO CINÉMA 2:3 ====================
                TvNavTab.SERIES -> {
                    val visibleSeriesCategories = remember(seriesCategories, activeProfile) {
                        seriesCategories.filter { sessionManager.isSeriesCategoryVisible(it.id) }
                    }
                    val seriesCatNames = remember(visibleSeriesCategories) {
                        listOf("Toutes") + visibleSeriesCategories.map { it.name }
                    }

                    TvCategoryChipsRow(
                        categories = seriesCatNames,
                        selectedCategory = selectedSeriesCategory,
                        focusRequester = contentFocusRequester,
                        onNavigateLeft = { sidebarFocusRequesters[TvNavTab.SERIES]?.requestFocus() },
                        onSelectCategory = { catName ->
                            selectedSeriesCategory = catName
                            val found = seriesCategories.find { it.name == catName }
                            if (found != null) {
                                onSelectSeriesCategory(found)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val seriesPageSize = 36
                    var seriesPage by remember(selectedSeriesCategory) { mutableIntStateOf(1) }
                    val totalSeriesPages = maxOf(1, ceil(series.size.toDouble() / seriesPageSize).toInt())
                    val pagedSeries = remember(series, seriesPage) {
                        series.drop((seriesPage - 1) * seriesPageSize).take(seriesPageSize)
                    }

                    if (isSeriesLoading) {
                        TvLoadingProgress(message = "Chargement des séries de $selectedSeriesCategory...")
                    } else if (series.isEmpty()) {
                        TvEmptyState(message = "Aucune série disponible dans cette catégorie")
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(6),
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(pagedSeries.size) { index ->
                                    val ser = pagedSeries[index]
                                    TvSeriesGridCard(
                                        series = ser,
                                        onNavigateLeft = if (index % 6 == 0) {
                                            { sidebarFocusRequesters[TvNavTab.SERIES]?.requestFocus() }
                                        } else null,
                                        onClick = { selectedSeriesDetail = ser }
                                    )
                                }
                            }

                            NoosPaginationBar(
                                currentPage = seriesPage,
                                totalPages = totalSeriesPages,
                                totalItems = series.size,
                                itemLabel = "séries",
                                onPageChange = { seriesPage = it },
                                isTv = true
                            )
                        }
                    }
                }

                // ==================== 5. ONGLET FAVORIS (CHAÎNES, FILMS & SÉRIES ÉPINGLÉS) ====================
                TvNavTab.FAVORITES -> {
                    TvFavoritesContent(
                        channels = channels,
                        movies = movies,
                        series = series,
                        sessionManager = sessionManager,
                        focusRequester = contentFocusRequester,
                        onNavigateLeftToSidebar = {
                            sidebarFocusRequesters[TvNavTab.FAVORITES]?.requestFocus()
                        },
                        onSelectChannel = onSelectChannel,
                        onSelectMovie = { selectedMovieDetail = it },
                        onSelectSeries = { selectedSeriesDetail = it },
                        onFavoriteChanged = {
                            activeProfile = sessionManager.getActiveProfile()
                            favoriteIds = sessionManager.getFavoriteChannelIds()
                            favoriteMovieIds = sessionManager.getFavoriteMovieIds()
                            favoriteSeriesIds = sessionManager.getFavoriteSeriesIds()
                        }
                    )
                }

                // ==================== 6. ONGLET FILTRES DE CATÉGORIES ====================
                TvNavTab.FILTERS -> {
                    TvCategoryFiltersContent(
                        vodCategories = vodCategories,
                        seriesCategories = seriesCategories,
                        sessionManager = sessionManager,
                        focusRequester = contentFocusRequester,
                        onNavigateLeftToSidebar = {
                            sidebarFocusRequesters[TvNavTab.FILTERS]?.requestFocus()
                        },
                        onFiltersUpdated = {
                            activeProfile = sessionManager.getActiveProfile()
                        }
                    )
                }

                // ==================== 7. ONGLET SETTINGS (PARAMÈTRES & MISES À JOUR) ====================
                TvNavTab.SETTINGS -> {
                    TvSettingsContent(
                        sessionManager = sessionManager,
                        focusRequester = contentFocusRequester,
                        onNavigateLeft = { sidebarFocusRequesters[TvNavTab.SETTINGS]?.requestFocus() },
                        onOpenLogin = onOpenLogin,
                        onLogout = onLogout,
                        onLanguageChanged = onLanguageChanged
                    )
                }
            }
        }
    }

    // ==================== MODALES CINÉMATIQUES DE DÉTAIL (FILM & SÉRIE) ====================
        if (selectedMovieDetail != null) {
            TvMovieDetailModal(
                movie = selectedMovieDetail!!,
                isFavorite = favoriteMovieIds.contains(selectedMovieDetail!!.id),
                onPlay = { m ->
                    selectedMovieDetail = null
                    onSelectMovie(m)
                },
                onToggleFavorite = {
                    val id = selectedMovieDetail!!.id
                    sessionManager.toggleFavoriteMovie(id)
                    favoriteMovieIds = sessionManager.getFavoriteMovieIds()
                    activeProfile = sessionManager.getActiveProfile()
                },
                onDismiss = { selectedMovieDetail = null },
                onFetchFullInfo = { id -> onFetchVodInfo?.invoke(id) }
            )
        }

        if (selectedSeriesDetail != null) {
            TvSeriesDetailModal(
                series = selectedSeriesDetail!!,
                isFavorite = favoriteSeriesIds.contains(selectedSeriesDetail!!.id),
                onPlayEpisode = { ep ->
                    val s = selectedSeriesDetail!!
                    selectedSeriesDetail = null
                    onSelectEpisode(s, ep)
                },
                onToggleFavorite = {
                    val id = selectedSeriesDetail!!.id
                    sessionManager.toggleFavoriteSeries(id)
                    favoriteSeriesIds = sessionManager.getFavoriteSeriesIds()
                    activeProfile = sessionManager.getActiveProfile()
                },
                onDismiss = { selectedSeriesDetail = null },
                onFetchFullInfo = { id -> onFetchSeriesInfo?.invoke(id) }
            )
        }

        // ==================== MODALE DE GESTION MULTI-PROFILS ====================
        if (isProfileModalOpen) {
            TvProfileModal(
                sessionManager = sessionManager,
                onDismiss = { isProfileModalOpen = false },
                onProfileChanged = { newProfile ->
                    activeProfile = newProfile
                    favoriteIds = newProfile.favoriteChannelIds
                    favoriteMovieIds = newProfile.favoriteMovieIds
                    favoriteSeriesIds = newProfile.favoriteSeriesIds
                    isProfileModalOpen = false
                }
            )
        }
    }
}

/**
 * Barre latérale avec 7 onglets au style Glassy Frosted Blur
 */
@Composable
fun TvSidebar(
    selectedTab: TvNavTab,
    sidebarFocusRequesters: Map<TvNavTab, FocusRequester>,
    onTabSelected: (TvNavTab) -> Unit,
    onNavigateRight: () -> Unit
) {
    val strings = LocalStrings.current
    val tabList = remember { TvNavTab.values().toList() }

    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(GlassSurface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, GlassBorder))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabList.forEachIndexed { index, tab ->
            val isSelected = tab == selectedTab
            var isFocused by remember { mutableStateOf(false) }

            val scale by animateFloatAsState(
                targetValue = if (isFocused) 1.08f else 1.0f,
                label = "sidebar_scale"
            )

            val myRequester = sidebarFocusRequesters[tab] ?: remember { FocusRequester() }
            val nextTab = tabList.getOrNull(index + 1)
            val prevTab = tabList.getOrNull(index - 1)

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(18.dp))
                    .focusRequester(myRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                        if (it.isFocused) {
                            onTabSelected(tab)
                        }
                    }
                    .focusable()
                    .clickable {
                        onTabSelected(tab)
                        onNavigateRight()
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                                    onNavigateRight()
                                    true
                                }
                                Key.DirectionDown -> {
                                    if (nextTab != null) {
                                        sidebarFocusRequesters[nextTab]?.requestFocus()
                                        true
                                    } else false
                                }
                                Key.DirectionUp -> {
                                    if (prevTab != null) {
                                        sidebarFocusRequesters[prevTab]?.requestFocus()
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }
                    .background(
                        when {
                            isFocused -> Color(0xFF24334D)
                            isSelected -> NoosBlue.copy(alpha = 0.32f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (isFocused) 2.5.dp else if (isSelected) 1.dp else 0.dp,
                        color = if (isFocused) FocusGlow else if (isSelected) NoosCyan.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val tabLabel = tab.getLabel(strings)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tabLabel,
                        tint = if (isFocused) Color.White else if (isSelected) NoosCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tabLabel,
                        color = if (isFocused) Color.White else if (isSelected) NoosCyan else TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * En-tête supérieur moderne au style Glassy : Logo officiel NOOS, profil utilisateur, boutons arrondis
 */
@Composable
fun TvHeader(
    isPremium: Boolean,
    activeProfile: UserProfile,
    onOpenProfileModal: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onLogout: () -> Unit
) {
    val strings = LocalStrings.current
    var showLogoutConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo Officiel NOOS + Badge Gradient TV (décalé à droite pour ne pas coller au volet latéral)
        Row(
            modifier = Modifier.padding(start = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.noos_logo),
                contentDescription = "NOOS",
                modifier = Modifier.height(34.dp),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NoosGradient)
                    .padding(horizontal = 9.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = "TV",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
            if (isPremium) {
                PremiumVipBadge()
            } else {
                OutlinedButton(
                    onClick = onOpenUpgrade,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldVip),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldVip.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(strings.upgradePremium, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Actions rapides sobres avec forme pilule moderne glassy (50%)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bouton Recherche Pilule Glassy
            var isSearchFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onOpenSearch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSearchFocused) Color(0xFF283652) else GlassSurfaceElevated
                ),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSearchFocused) 2.dp else 1.dp,
                    color = if (isSearchFocused) NoosCyan else GlassBorder
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.onFocusChanged { isSearchFocused = it.isFocused }
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(strings.searchTitle, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Bouton Se déconnecter Pilule Glassy (remplace Identifiants IPTV)
            var isLogoutFocused by remember { mutableStateOf(false) }
            Button(
                onClick = { showLogoutConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLogoutFocused) RedLive.copy(alpha = 0.25f) else GlassSurfaceElevated
                ),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isLogoutFocused) 2.dp else 1.dp,
                    color = if (isLogoutFocused) RedLive else GlassBorder
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.onFocusChanged { isLogoutFocused = it.isFocused }
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = if (isLogoutFocused) RedLive else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.logout,
                    color = if (isLogoutFocused) Color.White else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Bouton Profil multi-utilisateurs
            TvProfileButton(
                profile = activeProfile,
                onClick = onOpenProfileModal
            )
        }
    }

    if (showLogoutConfirm) {
        TvLogoutConfirmModal(
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }
}

/**
 * Barre de défilement horizontal des catégories en pilules modernes (RoundedCornerShape(50))
 */
@Composable
fun TvCategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onSelectCategory: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(categories) { category ->
            val isSelected = category.equals(selectedCategory, ignoreCase = true)
            var isFocused by remember { mutableStateOf(false) }

            var chipMod = Modifier
                .clip(RoundedCornerShape(50))

            if (focusRequester != null && category == categories.firstOrNull()) {
                chipMod = chipMod.focusRequester(focusRequester)
            }

            Box(
                modifier = chipMod
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelectCategory(category) }
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && category == categories.firstOrNull()) {
                            onNavigateLeft?.invoke()
                            true
                        } else false
                    }
                    .background(
                        when {
                            isFocused -> Color.White
                            isSelected -> NoosBlue.copy(alpha = 0.35f)
                            else -> SurfaceDarkVariant
                        }
                    )
                    .border(
                        width = if (isFocused) 2.5.dp else if (isSelected) 1.dp else 1.dp,
                        color = if (isFocused) FocusGlow else if (isSelected) NoosBlue else CardBorderUnfocused,
                        shape = RoundedCornerShape(50)
                    )
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(
                    text = category,
                    color = if (isFocused) Color.Black else if (isSelected) Color.White else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Carte de chaîne TV en direct — Image du programme en cours, texte défilant (Marquee) et jauge de direct
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TvChannelGridCard(
    channel: Channel,
    currentProgram: EpgProgram,
    isFavorite: Boolean = false,
    onNavigateLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "channel_scale")

    val backdropUrl = remember(channel.id, currentProgram.id) {
        EpgProvider.getProgramBackdrop(channel, currentProgram)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(144.dp)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
                } else false
            }
            .background(Color(0xFF131A26))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        // 1. Image du programme en cours en arrière-plan plein format
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(backdropUrl)
                .crossfade(true)
                .allowHardware(false)
                .build(),
            contentDescription = currentProgram.title,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = NoosBlue,
                        trackColor = SurfaceDarkVariant
                    )
                }
            },
            error = {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF131A26)))
            },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Dégradé sombre cinématique pour garantir un contraste et une lisibilité parfaits
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xCC080A0F),
                            Color(0x88080A0F),
                            Color(0xF6080A0F)
                        )
                    )
                )
        )

        // 3. Contenu de la miniature : En-tête chaîne & Pied de carte programme défilant
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Ligne supérieure : Logo + Nom chaîne & Badges Direct / 4K
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (!channel.logoUrl.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x99000000))
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

                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isFavorite) {
                        Text("⭐", fontSize = 11.sp)
                    }
                    LiveIndicatorBadge()
                    if (channel.isHdr) HdrBadge() else ResolutionBadge(resolution = channel.resolution)
                }
            }

            // Ligne inférieure : Programme en cours (Texte défilant marquee) + Plage horaire + Barre de progression
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Horaires et catégorie
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentProgram.timeSlotFormatted,
                        color = NoosCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    currentProgram.category?.let {
                        Text(
                            text = it,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                // Petit texte qui défile avec le nom du programme en cours
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = null,
                        tint = if (isFocused) NoosCyan else Color.White,
                        modifier = Modifier.size(14.dp)
                    )

                    Text(
                        text = currentProgram.title,
                        color = if (isFocused) NoosCyan else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }

                // Barre de progression du direct
                val progFraction = currentProgram.progressFraction()
                LinearProgressIndicator(
                    progress = { progFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50)),
                    color = NoosBlue,
                    trackColor = Color(0x333888FF)
                )
            }
        }
    }
}

/**
 * Carte de film VOD — Ratio Cinéma Standard 2:3 (non rogné) & Dégradé d'information sobre
 */
@Composable
fun TvMovieGridCard(
    movie: VodMovie,
    onNavigateLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "vod_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f) // Ratio standard d'affiche de film (ex: 200x300, 500x750)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
                } else false
            }
            .background(if (isFocused) Color(0xFF1E2838) else CardBackground)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        // 1. Affiche plein format respectant le ratio 2:3
        if (!movie.posterUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.posterUrl)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = movie.title,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = NoosBlue,
                                trackColor = SurfaceDarkVariant
                            )
                            Text("Chargement...", color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                    }
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
            }
        }

        // 2. Badge HDR en haut à droite
        if (movie.isHdr) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                HdrBadge(text = movie.hdrFormat ?: "HDR")
            }
        }

        // 3. Dégradé sombre cinématique en bas avec Titre, Année et Note
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x80080A0F),
                            Color(0xF5080A0F)
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = movie.title,
                    color = if (isFocused) NoosCyan else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = movie.releaseYear?.toString() ?: "",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    if (movie.rating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("★", color = GoldVip, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", movie.rating),
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Carte de série VOD — Ratio Cinéma Standard 2:3 (non rogné) & Dégradé d'information sobre
 */
@Composable
fun TvSeriesGridCard(
    series: Series,
    onNavigateLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "series_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f) // Ratio standard d'affiche de série (ex: 200x300, 500x750)
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
                } else false
            }
            .background(if (isFocused) Color(0xFF1E2838) else CardBackground)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        // 1. Affiche plein format respectant le ratio 2:3
        if (!series.posterUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(series.posterUrl)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = series.title,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = NoosBlue,
                                trackColor = SurfaceDarkVariant
                            )
                            Text("Chargement...", color = TextSecondary, fontSize = 9.sp)
                        }
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                    }
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
            }
        }

        // 2. Dégradé sombre cinématique en bas avec Titre, Saisons et Note
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color(0x80080A0F),
                            Color(0xF5080A0F)
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = series.title,
                    color = if (isFocused) NoosCyan else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val seasonsText = if (series.seasons.isNotEmpty()) "${series.seasons.size} S." else "Série"
                    Text(
                        text = seasonsText,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    if (series.rating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("★", color = GoldVip, fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", series.rating),
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contenu interactif Guide TV (EPG) sobre et élégant
 */
@Composable
fun TvEpgContent(
    channels: List<Channel>,
    epgPrograms: List<EpgProgram>,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    onSelectChannel: (Channel) -> Unit
) {
    val currentTime = remember { System.currentTimeMillis() }
    var focusedProgram by remember { mutableStateOf<EpgProgram?>(null) }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Guide TV Interactif (Direct & Replay)",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Heure actuelle : ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(currentTime)}",
                color = NoosCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (channels.isEmpty()) {
            TvEmptyState(message = "Aucune chaîne pour afficher le guide")
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(channels) { channel ->
                    var isRowFocused by remember { mutableStateOf(false) }

                    val programs = remember(channel.id, epgPrograms) {
                        val real = epgPrograms.filter { it.channelId == channel.id }
                        if (real.isNotEmpty()) real else generateFallbackPrograms(channel, currentTime)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // En-tête de la chaîne à gauche
                        Box(
                            modifier = Modifier
                                .width(180.dp)
                                .height(78.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .onFocusChanged {
                                    isRowFocused = it.isFocused
                                    if (it.isFocused) {
                                        focusedChannel = channel
                                        focusedProgram = programs.find { p -> p.isLiveNow(currentTime) } ?: programs.firstOrNull()
                                    }
                                }
                                .focusable()
                                .clickable { onSelectChannel(channel) }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.Back)) {
                                        onNavigateLeft()
                                        true
                                    } else false
                                }
                                .background(if (isRowFocused) Color(0xFF1E2838) else SurfaceDark)
                                .border(
                                    width = if (isRowFocused) 3.dp else 1.dp,
                                    color = if (isRowFocused) FocusGlow else CardBorderUnfocused,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = channel.name,
                                    color = if (isRowFocused) NoosCyan else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${channel.resolution} • ${channel.categoryName}",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Ligne des programmes avec barre de progression
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(programs) { prog ->
                                val isLive = prog.isLiveNow(currentTime)
                                val progress = prog.progressFraction(currentTime)
                                var isProgFocused by remember { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier
                                        .width(260.dp)
                                        .height(78.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .onFocusChanged {
                                            isProgFocused = it.isFocused
                                            if (it.isFocused) {
                                                focusedProgram = prog
                                                focusedChannel = channel
                                            }
                                        }
                                        .focusable()
                                        .clickable { onSelectChannel(channel) }
                                        .background(if (isLive) Color(0xFF162032) else SurfaceDark)
                                        .border(
                                            width = if (isProgFocused) 3.dp else if (isLive) 1.dp else 0.dp,
                                            color = if (isProgFocused) FocusGlow else if (isLive) NoosBlue.copy(alpha = 0.4f) else Color.Transparent,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .padding(10.dp)
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
                                                text = prog.timeSlotFormatted,
                                                color = if (isLive) NoosCyan else TextSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (isLive) {
                                                LiveIndicatorBadge()
                                            } else if (prog.hasCatchup) {
                                                Text("REVOIR", color = GoldVip, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Text(
                                            text = prog.title,
                                            color = if (isProgFocused) NoosCyan else TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (isLive) {
                                            LinearProgressIndicator(
                                                progress = { progress },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = NoosBlue,
                                                trackColor = SurfaceDarkVariant
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(3.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bandeau d'information détaillé du programme ciblé
            AnimatedVisibility(visible = focusedProgram != null) {
                focusedProgram?.let { prog ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF131A26))
                            .border(1.dp, CardBorderUnfocused, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    focusedChannel?.let { ch ->
                                        Text(ch.name, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("•", color = TextSecondary, fontSize = 11.sp)
                                    }
                                    Text(prog.title, color = NoosCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("${prog.timeSlotFormatted} • ${prog.category ?: "Programme"}", color = TextSecondary, fontSize = 11.sp)
                            }
                            if (!prog.description.isNullOrBlank()) {
                                Text(
                                    text = prog.description,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Créneaux par défaut si l'EPG n'est pas encore synchronisé
 */
private fun generateFallbackPrograms(channel: Channel, currentTime: Long): List<EpgProgram> {
    val halfHour = 30 * 60 * 1000L
    val start1 = currentTime - (currentTime % halfHour)
    val end1 = start1 + halfHour
    val start2 = end1
    val end2 = start2 + halfHour

    return listOf(
        EpgProgram(
            id = "cur_${channel.id}",
            channelId = channel.id,
            title = "En direct sur ${channel.name}",
            description = "Programme en cours de diffusion",
            startEpochMs = start1,
            stopEpochMs = end1
        ),
        EpgProgram(
            id = "next_${channel.id}",
            channelId = channel.id,
            title = "Suite des programmes",
            description = "Émission à suivre sur ${channel.name}",
            startEpochMs = start2,
            stopEpochMs = end2
        )
    )
}

/**
 * Indicateur de chargement sobre et moderne
 */
@Composable
fun TvLoadingProgress(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(38.dp),
                color = NoosBlue,
                strokeWidth = 3.dp
            )
            Text(
                text = message,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * État vide sobre
 */
@Composable
fun TvEmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

/**
 * Modale cinématique de détails d'un film VOD pour Android TV
 */
@Composable
fun TvMovieDetailModal(
    movie: VodMovie,
    isFavorite: Boolean,
    onPlay: (VodMovie) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onFetchFullInfo: (suspend (String) -> VodMovie?)? = null
) {
    val playFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    var fullMovie by remember(movie.id) { mutableStateOf(movie) }
    var isFetchingInfo by remember(movie.id) { mutableStateOf(onFetchFullInfo != null && movie.plot.isNullOrBlank()) }

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(movie.id) {
        if (movie.plot.isNullOrBlank() && onFetchFullInfo != null) {
            isFetchingInfo = true
            onFetchFullInfo(movie.id)?.let { enriched ->
                fullMovie = enriched
            }
            isFetchingInfo = false
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { playFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE080A0F))
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.84f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(listOf(NoosBlue.copy(alpha = 0.6f), NoosCyan.copy(alpha = 0.6f))),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Colonne de gauche : Affiche 2:3 + Badges techniques
                Column(
                    modifier = Modifier.width(230.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.5.dp, NoosCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .background(Color(0xFF162032))
                    ) {
                        val poster = fullMovie.posterUrl ?: movie.posterUrl
                        if (!poster.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(poster)
                                    .crossfade(true)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = fullMovie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(54.dp))
                            }
                        }

                        if (fullMovie.isHdr) {
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                HdrBadge(text = fullMovie.hdrFormat ?: "HDR")
                            }
                        }
                    }

                    // Badges techniques (4K, HEVC, AC3)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ResolutionBadge(resolution = fullMovie.resolution)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceDarkVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(fullMovie.videoCodec.uppercase(), color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SurfaceDarkVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(fullMovie.audioCodec.uppercase(), color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Colonne de droite : Détails, Synopsis & Actions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Titre & Bouton Fermer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = fullMovie.title,
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceDarkVariant)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Ligne métadonnées : Année, Durée, Note
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            fullMovie.releaseYear?.let {
                                Text("$it", color = NoosCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text("•", color = TextSecondary)
                            Text(fullMovie.durationFormatted, color = TextPrimary, fontSize = 13.sp)
                            if (fullMovie.rating > 0f) {
                                Text("•", color = TextSecondary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("★ ", color = GoldVip, fontSize = 13.sp)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f", fullMovie.rating) + " / 10",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Tags Genres
                        if (fullMovie.genres.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                fullMovie.genres.forEach { genre ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(SurfaceDarkVariant)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(genre, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // Synopsis / Plot
                        Text(
                            text = "Synopsis",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val moviePlot = fullMovie.plot
                        val synopsisDisplay = when {
                            !moviePlot.isNullOrBlank() -> moviePlot
                            isFetchingInfo -> "Chargement du synopsis..."
                            else -> "Aucun synopsis renseigné par le fournisseur."
                        }
                        Text(
                            text = synopsisDisplay,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Casting & Réalisation
                        if (fullMovie.director != null || fullMovie.cast.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                fullMovie.director?.let {
                                    Column {
                                        Text("Réalisation", color = TextSecondary, fontSize = 10.sp)
                                        Text(it, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (fullMovie.cast.isNotEmpty()) {
                                    Column {
                                        Text("Distribution", color = TextSecondary, fontSize = 10.sp)
                                        Text(fullMovie.cast.joinToString(", "), color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // Boutons d'action TV
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isPlayFocused by remember { mutableStateOf(false) }
                        var isFavFocused by remember { mutableStateOf(false) }
                        var isCloseFocused by remember { mutableStateOf(false) }

                        // Bouton Play (Focus initial)
                        Button(
                            onClick = { onPlay(fullMovie) },
                            modifier = Modifier
                                .focusRequester(playFocusRequester)
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .focusable(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isPlayFocused) NoosCyan else NoosBlue),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isPlayFocused) Color.Black else Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lancer le film", color = if (isPlayFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Bouton Favoris
                        OutlinedButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .onFocusChanged { isFavFocused = it.isFocused }
                                .focusable(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFavFocused) Color(0xFF1E2838) else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isFavFocused) 2.dp else 1.dp,
                                color = if (isFavFocused) FocusGlow else CardBorderUnfocused
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(if (isFavorite) "★ Dans vos favoris" else "☆ Ajouter aux favoris", color = if (isFavorite) GoldVip else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Bouton Fermer
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .focusable(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isCloseFocused) Color(0xFF1E2838) else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isCloseFocused) 2.dp else 1.dp,
                                color = if (isCloseFocused) FocusGlow else CardBorderUnfocused
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("Fermer", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modale cinématique de détails d'une série TV pour Android TV
 */
@Composable
fun TvSeriesDetailModal(
    series: Series,
    isFavorite: Boolean,
    onPlayEpisode: (Episode) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onFetchFullInfo: (suspend (String) -> Series?)? = null
) {
    val context = LocalContext.current
    val firstFocusRequester = remember { FocusRequester() }
    var selectedSeasonIndex by remember { mutableStateOf(0) }

    var fullSeries by remember(series.id) { mutableStateOf(series) }
    var isLoadingInfo by remember(series.id) { mutableStateOf(series.seasons.isEmpty() && onFetchFullInfo != null) }

    LaunchedEffect(series.id) {
        if (series.seasons.isEmpty() && onFetchFullInfo != null) {
            isLoadingInfo = true
            val fetched = onFetchFullInfo(series.id)
            if (fetched != null) {
                fullSeries = fetched
            }
            isLoadingInfo = false
        }
    }

    BackHandler {
        onDismiss()
    }

    val seasons = fullSeries.seasons
    val currentSeason = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE080A0F))
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Back) {
                    onDismiss()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(listOf(NoosBlue.copy(alpha = 0.6f), NoosCyan.copy(alpha = 0.6f))),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Colonne de gauche : Affiche 2:3 + Bouton Favoris
                Column(
                    modifier = Modifier.width(220.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.5.dp, NoosCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .background(Color(0xFF162032))
                    ) {
                        if (!fullSeries.posterUrl.isNullOrBlank()) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(fullSeries.posterUrl)
                                    .crossfade(true)
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = fullSeries.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(54.dp))
                            }
                        }
                    }

                    // Boutons Favoris & Fermer sous l'affiche
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var isFavFocused by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isFavFocused = it.isFocused }
                                .focusable(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isFavFocused) Color(0xFF1E2838) else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isFavFocused) 2.dp else 1.dp,
                                color = if (isFavFocused) FocusGlow else CardBorderUnfocused
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(if (isFavorite) "★ Favori" else "☆ Favori", color = if (isFavorite) GoldVip else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        var isCloseBtnFocused by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isCloseBtnFocused = it.isFocused }
                                .focusable(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isCloseBtnFocused) Color(0xFF1E2838) else Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isCloseBtnFocused) 2.dp else 1.dp,
                                color = if (isCloseBtnFocused) FocusGlow else CardBorderUnfocused
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text("Fermer", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                // Colonne de droite : Titre, Saisons, Liste des Épisodes
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Titre & Fermer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fullSeries.title,
                                color = TextPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                fullSeries.releaseYear?.let { Text("$it", color = NoosCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                                if (seasons.isNotEmpty()) {
                                    Text("•", color = TextSecondary)
                                    Text("${seasons.size} Saison${if (seasons.size > 1) "s" else ""}", color = TextPrimary, fontSize = 12.sp)
                                }
                                if (fullSeries.rating > 0f) {
                                    Text("•", color = TextSecondary)
                                    Text("★ ${String.format(java.util.Locale.US, "%.1f", fullSeries.rating)}", color = GoldVip, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceDarkVariant)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Synopsis
                    val seriesPlot = fullSeries.plot
                    if (!seriesPlot.isNullOrBlank()) {
                        Text(
                            text = seriesPlot,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isLoadingInfo) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = NoosCyan,
                                    strokeWidth = 3.dp
                                )
                                Text("Chargement des saisons et épisodes...", color = TextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else if (seasons.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text("Épisodes détaillés non indexés par le serveur", color = TextSecondary, fontSize = 13.sp)
                                var isDirectPlayFocused by remember { mutableStateOf(false) }
                                Button(
                                    onClick = {
                                        val fallbackEp = Episode(
                                            id = fullSeries.id,
                                            seriesId = fullSeries.id,
                                            seasonNumber = 1,
                                            episodeNumber = 1,
                                            title = "Épisode 1",
                                            streamUrl = ""
                                        )
                                        onPlayEpisode(fallbackEp)
                                    },
                                    modifier = Modifier
                                        .onFocusChanged { isDirectPlayFocused = it.isFocused }
                                        .focusable(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isDirectPlayFocused) Color.White else NoosBlue
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isDirectPlayFocused) Color.Black else Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Lancer la série (Épisode 1)", color = if (isDirectPlayFocused) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // Sélecteur de Saisons
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons.size) { idx ->
                                val s = seasons[idx]
                                val isSelected = idx == selectedSeasonIndex
                                var isChipFocused by remember { mutableStateOf(false) }

                                var chipMod = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .onFocusChanged { isChipFocused = it.isFocused }
                                    .focusable()
                                    .clickable { selectedSeasonIndex = idx }
                                    .background(if (isChipFocused) Color.White else if (isSelected) NoosBlue.copy(alpha = 0.35f) else SurfaceDarkVariant)
                                    .border(
                                        width = if (isChipFocused) 2.5.dp else 1.dp,
                                        color = if (isChipFocused) FocusGlow else if (isSelected) NoosBlue else CardBorderUnfocused,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 6.dp)

                                if (idx == 0) {
                                    chipMod = chipMod.focusRequester(firstFocusRequester)
                                }

                                Box(modifier = chipMod) {
                                    Text(
                                        text = s.name.ifBlank { "Saison ${s.seasonNumber}" },
                                        color = if (isChipFocused) Color.Black else if (isSelected) Color.White else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Liste des Épisodes de la saison sélectionnée
                        val episodeList = currentSeason?.episodes ?: emptyList()
                        Text("Épisodes (${episodeList.size})", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(episodeList) { ep ->
                                var isEpFocused by remember { mutableStateOf(false) }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .onFocusChanged { isEpFocused = it.isFocused }
                                        .focusable()
                                        .clickable { onPlayEpisode(ep) }
                                        .background(if (isEpFocused) Color(0xFF1E2838) else SurfaceDarkVariant)
                                        .border(
                                            width = if (isEpFocused) 2.5.dp else 1.dp,
                                            color = if (isEpFocused) FocusGlow else CardBorderUnfocused,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isEpFocused) NoosCyan else NoosBlue),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = if (isEpFocused) Color.Black else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = "Épisode ${ep.episodeNumber} : ${ep.title}",
                                                    color = if (isEpFocused) NoosCyan else TextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    ep.durationMinutes?.let { dur ->
                                                        Text("$dur min", color = TextSecondary, fontSize = 10.sp)
                                                    }
                                                    if (ep.containerExtension.isNotBlank()) {
                                                        Text(ep.containerExtension.uppercase(), color = NoosCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        Text("▶ Lire", color = if (isEpFocused) NoosCyan else TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
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
 * Modale de confirmation de déconnexion au style Glassy Frosted Blur
 */
@Composable
fun TvLogoutConfirmModal(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val confirmBtnRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { confirmBtnRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(GlassCardGradient)
                .border(1.5.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(RedLive.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = RedLive,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = strings.logoutConfirmTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = strings.logoutConfirmMsg,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var cancelFocused by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { cancelFocused = it.isFocused }
                            .border(if (cancelFocused) 1.5.dp else 0.dp, NoosCyan, RoundedCornerShape(50))
                    ) {
                        Text(strings.cancel, color = if (cancelFocused) Color.White else TextSecondary)
                    }

                    var confirmFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(confirmBtnRequester)
                            .onFocusChanged { confirmFocused = it.isFocused },
                        colors = ButtonDefaults.buttonColors(containerColor = RedLive),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(strings.logout, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

