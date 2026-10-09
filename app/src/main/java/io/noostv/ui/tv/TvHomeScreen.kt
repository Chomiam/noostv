@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.noostv.ui.tv

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import io.noostv.R
import io.noostv.core.audio.LocalSoundEffectManager
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.player.PlayerEngine
import io.noostv.core.player.formatDuration
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
 * 6 Catégories latérales :
 * 1. TV (Direct)
 * 2. Films (VOD Films)
 * 3. Séries (VOD Séries)
 * 4. Favoris (Épingles chaînes, films, séries)
 * 5. Filtres (Visibilité des catégories)
 * 6. Paramètres (Gestion système & Mises à jour GitHub)
 */
enum class TvNavTab(val label: String, val icon: ImageVector) {
    TV("TV", Icons.Default.Tv),
    MOVIES("Films", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary),
    FAVORITES("Favoris", Icons.Default.Star),
    FILTERS("Filtres", Icons.Default.Tune),
    SETTINGS("Paramètres", Icons.Default.Settings);

    fun getLabel(strings: AppStrings): String = when (this) {
        TV -> strings.navLiveTv
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
    onLoadChannelsBatch: ((List<Channel>) -> Unit)? = null,
    onSelectMovie: (VodMovie, Long) -> Unit = { _, _ -> },
    onSelectSeries: (Series) -> Unit,
    onSelectEpisode: (Series, io.noostv.data.model.Episode, Long) -> Unit = { ser, _, _ -> onSelectSeries(ser) },
    onSelectVodCategory: (Category) -> Unit,
    onSelectSeriesCategory: (Category) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onFetchVodInfo: (suspend (String) -> VodMovie?)? = null,
    onFetchSeriesInfo: (suspend (String) -> Series?)? = null,
    onLanguageChanged: (AppLanguage) -> Unit = {},
    initialTab: TvNavTab = TvNavTab.TV,
    onTabSelected: (TvNavTab) -> Unit = {},
    initialVodCategory: String = "Toutes",
    onVodCategorySelected: (String) -> Unit = {},
    initialSeriesCategory: String = "Toutes",
    onSeriesCategorySelected: (String) -> Unit = {},
    initialMoviePage: Int = 1,
    onMoviePageChange: (Int) -> Unit = {},
    initialSeriesPage: Int = 1,
    onSeriesPageChange: (Int) -> Unit = {},
    initialFocusedMovieId: String? = null,
    onMovieFocused: (String) -> Unit = {},
    initialFocusedSeriesId: String? = null,
    onSeriesFocused: (String) -> Unit = {},
    onRefreshCatalog: () -> Unit = {},
    hasUpdateAvailable: Boolean = false,
    onUpdateStatusChanged: ((Boolean) -> Unit)? = null
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(initialTab) }
    var selectedLiveCategory by remember { mutableStateOf("Toutes") }
    var selectedVodCategory by remember { mutableStateOf(initialVodCategory) }
    var selectedSeriesCategory by remember { mutableStateOf(initialSeriesCategory) }
    var previewChannel by remember { mutableStateOf(initialPreviewChannel) }

    var moviePage by remember { mutableIntStateOf(initialMoviePage) }
    var seriesPage by remember { mutableIntStateOf(initialSeriesPage) }
    var lastFocusedMovieId by remember { mutableStateOf(initialFocusedMovieId) }
    var lastFocusedSeriesId by remember { mutableStateOf(initialFocusedSeriesId) }

    var showExitDialog by remember { mutableStateOf(false) }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    var activeProfile by remember { mutableStateOf(sessionManager.getActiveProfile()) }
    var isProfileModalOpen by remember { mutableStateOf(false) }

    var selectedMovieDetail by remember { mutableStateOf<VodMovie?>(null) }
    var selectedSeriesDetail by remember { mutableStateOf<Series?>(null) }
    var favoriteMovieIds by remember { mutableStateOf(sessionManager.getFavoriteMovieIds()) }
    var favoriteSeriesIds by remember { mutableStateOf(sessionManager.getFavoriteSeriesIds()) }

    val subscription by entitlementManager.subscription.collectAsState()

    // Focus Requesters pour navigation fluide télécommande D-Pad
    val sidebarFocusRequesters = remember {
        mapOf(
            TvNavTab.TV to FocusRequester(),
            TvNavTab.MOVIES to FocusRequester(),
            TvNavTab.SERIES to FocusRequester(),
            TvNavTab.FAVORITES to FocusRequester(),
            TvNavTab.FILTERS to FocusRequester(),
            TvNavTab.SETTINGS to FocusRequester()
        )
    }
    val contentFocusRequesters = remember {
        mapOf(
            TvNavTab.TV to FocusRequester(),
            TvNavTab.MOVIES to FocusRequester(),
            TvNavTab.SERIES to FocusRequester(),
            TvNavTab.FAVORITES to FocusRequester(),
            TvNavTab.FILTERS to FocusRequester(),
            TvNavTab.SETTINGS to FocusRequester()
        )
    }
    val tvChannelListFocusRequester = remember { FocusRequester() }
    val moviesCategoryChipsFocusRequester = remember { FocusRequester() }
    val seriesCategoryChipsFocusRequester = remember { FocusRequester() }
    val moviesGridState = rememberLazyGridState()
    val seriesGridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        runCatching { sidebarFocusRequesters[initialTab]?.requestFocus() }
    }

    // Interception de la touche Retour télécommande :
    // 1. Fermer les modales (dialogue de confirmation de sortie, profil, détails film/série) ou le mode prévisualisation.
    // 2. Si au menu principal / accueil : double-clic sur Retour affiche le dialogue de confirmation de fermeture complète.
    BackHandler(enabled = true) {
        if (showExitDialog) {
            showExitDialog = false
        } else if (isProfileModalOpen) {
            isProfileModalOpen = false
        } else if (selectedMovieDetail != null) {
            selectedMovieDetail = null
        } else if (selectedSeriesDetail != null) {
            selectedSeriesDetail = null
        } else if (previewChannel != null) {
            playerEngine?.stop()
            previewChannel = null
            onClearCurrentChannel?.invoke()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackPressTime < 2500L) {
                showExitDialog = true
                lastBackPressTime = 0L
            } else {
                lastBackPressTime = now
                Toast.makeText(context, strings.pressBackAgainToExit, Toast.LENGTH_SHORT).show()
                runCatching { sidebarFocusRequesters[selectedTab]?.requestFocus() }
            }
        }
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

    // Filtrage des chaînes en direct selon la catégorie sélectionnée et les filtres de visibilité
    val filteredChannels = remember(channels, selectedLiveCategory, favoriteIds, activeProfile) {
        if (selectedLiveCategory.startsWith("⭐ Favoris")) {
            channels.filter { favoriteIds.contains(it.id) }
        } else if (selectedLiveCategory == "Toutes") {
            channels.filter { sessionManager.isLiveCategoryVisible(it.categoryId) }
        } else {
            channels.filter { it.categoryName.equals(selectedLiveCategory, ignoreCase = true) || it.categoryId == selectedLiveCategory }
        }
    }

    // Chargement automatique en arrière-plan de l'EPG pour les premières chaînes de la catégorie affichée
    LaunchedEffect(filteredChannels, selectedLiveCategory) {
        if (filteredChannels.isNotEmpty()) {
            onLoadChannelsBatch?.invoke(filteredChannels.take(24))
        }
    }

    val isAnyModalOpen = selectedMovieDetail != null || selectedSeriesDetail != null || isProfileModalOpen || showExitDialog

    MacOsDarkGlassBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    canFocus = !isAnyModalOpen
                }
        ) {
        // ==================== BARRE LATÉRALE GAUCHE MODERNE & ARRONDIE ====================
        TvSidebar(
            selectedTab = selectedTab,
            sidebarFocusRequesters = sidebarFocusRequesters,
            contentFocusRequester = when (selectedTab) {
                TvNavTab.MOVIES -> if (movies.isNotEmpty() && !isVodLoading) contentFocusRequesters[TvNavTab.MOVIES] else moviesCategoryChipsFocusRequester
                TvNavTab.SERIES -> if (series.isNotEmpty() && !isSeriesLoading) contentFocusRequesters[TvNavTab.SERIES] else seriesCategoryChipsFocusRequester
                else -> contentFocusRequesters[selectedTab]
            },
            hasUpdateAvailable = hasUpdateAvailable,
            onTabSelected = { tab ->
                selectedTab = tab
                onTabSelected(tab)
            },
            onNavigateRight = {
                var handled = false
                when (selectedTab) {
                    TvNavTab.MOVIES -> {
                        coroutineScope.launch {
                            if (movies.isNotEmpty() && !isVodLoading) {
                                if (moviesGridState.firstVisibleItemIndex != 0) {
                                    moviesGridState.scrollToItem(0)
                                }
                                kotlinx.coroutines.delay(20)
                                val r1 = runCatching {
                                    contentFocusRequesters[TvNavTab.MOVIES]?.requestFocus()
                                }
                                if (!r1.isSuccess) {
                                    runCatching { moviesCategoryChipsFocusRequester.requestFocus() }
                                }
                            } else {
                                kotlinx.coroutines.delay(20)
                                runCatching { moviesCategoryChipsFocusRequester.requestFocus() }
                            }
                        }
                        handled = true
                    }
                    TvNavTab.SERIES -> {
                        coroutineScope.launch {
                            if (series.isNotEmpty() && !isSeriesLoading) {
                                if (seriesGridState.firstVisibleItemIndex != 0) {
                                    seriesGridState.scrollToItem(0)
                                }
                                kotlinx.coroutines.delay(20)
                                val r1 = runCatching {
                                    contentFocusRequesters[TvNavTab.SERIES]?.requestFocus()
                                }
                                if (!r1.isSuccess) {
                                    runCatching { seriesCategoryChipsFocusRequester.requestFocus() }
                                }
                            } else {
                                kotlinx.coroutines.delay(20)
                                runCatching { seriesCategoryChipsFocusRequester.requestFocus() }
                            }
                        }
                        handled = true
                    }
                    TvNavTab.TV -> {
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(20)
                            val r1 = runCatching {
                                tvChannelListFocusRequester.requestFocus()
                            }
                            if (!r1.isSuccess) {
                                runCatching { contentFocusRequesters[TvNavTab.TV]?.requestFocus() }
                            }
                        }
                        handled = true
                    }
                    TvNavTab.FILTERS -> {
                        coroutineScope.launch {
                            val r0 = runCatching { contentFocusRequesters[TvNavTab.FILTERS]?.requestFocus() }
                            if (r0.isFailure) {
                                kotlinx.coroutines.delay(20)
                                val r1 = runCatching { contentFocusRequesters[TvNavTab.FILTERS]?.requestFocus() }
                                if (r1.isFailure) {
                                    kotlinx.coroutines.delay(50)
                                    runCatching { contentFocusRequesters[TvNavTab.FILTERS]?.requestFocus() }
                                }
                            }
                        }
                        handled = true
                    }
                    else -> {
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(20)
                            runCatching { contentFocusRequesters[selectedTab]?.requestFocus() }
                        }
                        handled = true
                    }
                }
                handled
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
                    val liveCatNames = remember(categories, channels, favoriteIds, activeProfile) {
                        val list = mutableListOf("Toutes")
                        if (favoriteIds.isNotEmpty()) {
                            list.add("⭐ Favoris (${favoriteIds.size})")
                        }
                        val visibleCats = if (categories.isNotEmpty()) {
                            categories.filter { sessionManager.isLiveCategoryVisible(it.id) }.map { it.name }
                        } else {
                            channels.map { it.categoryName }.distinct()
                        }
                        list.addAll(visibleCats)
                        list
                    }

                    TvCategoryChipsRow(
                        categories = liveCatNames,
                        selectedCategory = selectedLiveCategory,
                        focusRequester = contentFocusRequesters[TvNavTab.TV],
                        onNavigateLeft = { runCatching { sidebarFocusRequesters[TvNavTab.TV]?.requestFocus() } },
                        onNavigateDown = {
                            runCatching { tvChannelListFocusRequester.requestFocus() }
                        },
                        onSelectCategory = { selectedLiveCategory = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (filteredChannels.isEmpty()) {
                        TvEmptyState(message = "Aucune chaîne disponible dans cette catégorie")
                    } else if (playerEngine != null) {
                        val activePreview = if (filteredChannels.any { it.id == previewChannel?.id }) {
                            previewChannel!!
                        } else {
                            filteredChannels.first()
                        }

                        TvChannelPreviewContent(
                            channels = filteredChannels,
                            selectedChannel = activePreview,
                            epgPrograms = epgPrograms,
                            playerEngine = playerEngine,
                            sessionManager = sessionManager,
                            contentFocusRequester = tvChannelListFocusRequester,
                            onNavigateUpFromFirstItem = {
                                runCatching { contentFocusRequesters[TvNavTab.TV]?.requestFocus() }
                            },
                            onChannelChanged = { newChan ->
                                previewChannel = newChan
                                onLoadChannelEpg?.invoke(newChan)
                            },
                            onOpenFullscreen = { chan ->
                                onSelectChannel(chan)
                            },
                            onClosePreview = null,
                            onNavigateLeftToSidebar = {
                                runCatching { sidebarFocusRequesters[TvNavTab.TV]?.requestFocus() }
                            },
                            onFavoriteToggled = {
                                favoriteIds = sessionManager.getFavoriteChannelIds()
                            }
                        )
                    }
                }

                // ==================== 2. ONGLET FILMS (VOD) — RATIO CINÉMA 2:3 ====================
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
                        focusRequester = moviesCategoryChipsFocusRequester,
                        downFocusRequester = contentFocusRequesters[TvNavTab.MOVIES],
                        leftFocusRequester = sidebarFocusRequesters[TvNavTab.MOVIES],
                        onNavigateLeft = { runCatching { sidebarFocusRequesters[TvNavTab.MOVIES]?.requestFocus() } },
                        onNavigateDown = {
                            if (movies.isNotEmpty()) {
                                runCatching { contentFocusRequesters[TvNavTab.MOVIES]?.requestFocus() }
                            }
                        },
                        onSelectCategory = { catName ->
                            if (selectedVodCategory != catName) {
                                selectedVodCategory = catName
                                onVodCategorySelected(catName)
                                moviePage = 1
                                onMoviePageChange(1)
                                lastFocusedMovieId = null
                                val found = vodCategories.find { it.name == catName }
                                if (found != null) {
                                    onSelectVodCategory(found)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val moviePageSize = 36
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
                            val moviePaginationPrevFocusRequester = remember { FocusRequester() }
                            val moviePaginationNextFocusRequester = remember { FocusRequester() }

                            val movieCardFocusRequesters = remember(moviePage, selectedVodCategory, pagedMovies.size) {
                                List(pagedMovies.size) { FocusRequester() }
                            }

                            // Restauration du focus et du scroll quand le dialogue de détail de film se ferme
                            var previousMovieDetailWasOpen by remember { mutableStateOf(false) }
                            LaunchedEffect(selectedMovieDetail) {
                                if (selectedMovieDetail != null) {
                                    previousMovieDetailWasOpen = true
                                } else if (previousMovieDetailWasOpen) {
                                    previousMovieDetailWasOpen = false
                                    val targetIdx = if (lastFocusedMovieId != null) {
                                        pagedMovies.indexOfFirst { it.id == lastFocusedMovieId }.takeIf { it >= 0 } ?: 0
                                    } else 0
                                    if (targetIdx in pagedMovies.indices) {
                                        moviesGridState.scrollToItem(targetIdx)
                                        kotlinx.coroutines.delay(80)
                                        val req = if (targetIdx == 0) contentFocusRequesters[TvNavTab.MOVIES] else movieCardFocusRequesters.getOrNull(targetIdx)
                                        runCatching { req?.requestFocus() }
                                    }
                                }
                            }

                            LazyVerticalGrid(
                                state = moviesGridState,
                                columns = GridCells.Fixed(6),
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(
                                    count = pagedMovies.size,
                                    key = { index -> pagedMovies[index].id },
                                    contentType = { "movie_card" }
                                ) { index ->
                                    val movie = pagedMovies[index]
                                    val isBottomEdge = (index + 6 >= pagedMovies.size)
                                    val targetPaginationFocusRequester = when {
                                        moviePage == 1 -> moviePaginationNextFocusRequester
                                        moviePage >= totalMoviePages -> moviePaginationPrevFocusRequester
                                        (index % 6) < 3 -> moviePaginationPrevFocusRequester
                                        else -> moviePaginationNextFocusRequester
                                    }

                                    TvMovieGridCard(
                                        movie = movie,
                                        focusRequester = if (index == 0) contentFocusRequesters[TvNavTab.MOVIES] else movieCardFocusRequesters.getOrNull(index),
                                        upFocusRequester = if (index < 6) moviesCategoryChipsFocusRequester else null,
                                        downFocusRequester = if (isBottomEdge) targetPaginationFocusRequester else null,
                                        leftFocusRequester = if (index % 6 == 0) sidebarFocusRequesters[TvNavTab.MOVIES] else null,
                                        onNavigateLeft = if (index % 6 == 0) {
                                            { runCatching { sidebarFocusRequesters[TvNavTab.MOVIES]?.requestFocus() } }
                                        } else null,
                                        onNavigateUp = if (index < 6) {
                                            { runCatching { moviesCategoryChipsFocusRequester.requestFocus() } }
                                        } else {
                                            {
                                                val prevIdx = index - 6
                                                if (prevIdx in pagedMovies.indices) {
                                                    coroutineScope.launch {
                                                        val isPrevVisible = moviesGridState.layoutInfo.visibleItemsInfo.any { it.index == prevIdx }
                                                        if (!isPrevVisible) {
                                                            moviesGridState.scrollToItem(prevIdx)
                                                        }
                                                        val r = if (prevIdx == 0) contentFocusRequesters[TvNavTab.MOVIES] else movieCardFocusRequesters.getOrNull(prevIdx)
                                                        runCatching { r?.requestFocus() }
                                                    }
                                                }
                                            }
                                        },
                                        onNavigateDown = if (isBottomEdge) {
                                            { runCatching { targetPaginationFocusRequester.requestFocus() } }
                                        } else {
                                            {
                                                val nextIdx = index + 6
                                                if (nextIdx in pagedMovies.indices) {
                                                    coroutineScope.launch {
                                                        val isNextVisible = moviesGridState.layoutInfo.visibleItemsInfo.any { it.index == nextIdx }
                                                        if (!isNextVisible) {
                                                            moviesGridState.scrollToItem(nextIdx)
                                                        }
                                                        val r = if (nextIdx == 0) contentFocusRequesters[TvNavTab.MOVIES] else movieCardFocusRequesters.getOrNull(nextIdx)
                                                        runCatching { r?.requestFocus() }
                                                    }
                                                }
                                            }
                                        },
                                        onFocus = {
                                            lastFocusedMovieId = movie.id
                                            onMovieFocused(movie.id)
                                        },
                                        onClick = {
                                            lastFocusedMovieId = movie.id
                                            onMovieFocused(movie.id)
                                            selectedMovieDetail = movie
                                        }
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
                                    lastFocusedMovieId = null
                                    coroutineScope.launch {
                                        runCatching { moviesGridState.scrollToItem(0) }
                                    }
                                },
                                isTv = true,
                                prevFocusRequester = moviePaginationPrevFocusRequester,
                                nextFocusRequester = moviePaginationNextFocusRequester,
                                sidebarFocusRequester = sidebarFocusRequesters[TvNavTab.MOVIES],
                                onNavigateLeftToSidebar = {
                                    runCatching { sidebarFocusRequesters[TvNavTab.MOVIES]?.requestFocus() }
                                },
                                onNavigateUpFromPrev = {
                                    coroutineScope.launch {
                                        val visibleIndices = moviesGridState.layoutInfo.visibleItemsInfo
                                            .map { it.index }
                                            .filter { it in pagedMovies.indices }

                                        val lastIdx = if (lastFocusedMovieId != null) {
                                            pagedMovies.indexOfFirst { it.id == lastFocusedMovieId }
                                        } else -1

                                        val targetIdx = when {
                                            lastIdx in visibleIndices -> lastIdx
                                            visibleIndices.isNotEmpty() -> {
                                                val lastVisible = visibleIndices.last()
                                                val lastVisibleRowStart = maxOf(visibleIndices.first(), (lastVisible / 6) * 6)
                                                lastVisibleRowStart
                                            }
                                            else -> maxOf(0, ((pagedMovies.size - 1) / 6) * 6)
                                        }

                                        val req = if (targetIdx == 0) contentFocusRequesters[TvNavTab.MOVIES] else movieCardFocusRequesters.getOrNull(targetIdx)
                                        var focused = false
                                        try {
                                            req?.requestFocus()
                                            focused = true
                                        } catch (_: Exception) {}

                                        if (!focused && targetIdx in pagedMovies.indices) {
                                            moviesGridState.scrollToItem(targetIdx)
                                            for (d in listOf(30L, 60L, 100L)) {
                                                kotlinx.coroutines.delay(d)
                                                try {
                                                    req?.requestFocus()
                                                    focused = true
                                                    break
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        if (!focused) {
                                            runCatching { contentFocusRequesters[TvNavTab.MOVIES]?.requestFocus() }
                                                .onFailure { runCatching { moviesCategoryChipsFocusRequester.requestFocus() } }
                                        }
                                    }
                                },
                                onNavigateUpFromNext = {
                                    coroutineScope.launch {
                                        val visibleIndices = moviesGridState.layoutInfo.visibleItemsInfo
                                            .map { it.index }
                                            .filter { it in pagedMovies.indices }

                                        val lastIdx = if (lastFocusedMovieId != null) {
                                            pagedMovies.indexOfFirst { it.id == lastFocusedMovieId }
                                        } else -1

                                        val targetIdx = when {
                                            lastIdx in visibleIndices -> lastIdx
                                            visibleIndices.isNotEmpty() -> visibleIndices.last()
                                            else -> pagedMovies.size - 1
                                        }

                                        val req = if (targetIdx == 0) contentFocusRequesters[TvNavTab.MOVIES] else movieCardFocusRequesters.getOrNull(targetIdx)
                                        var focused = false
                                        try {
                                            req?.requestFocus()
                                            focused = true
                                        } catch (_: Exception) {}

                                        if (!focused && targetIdx in pagedMovies.indices) {
                                            moviesGridState.scrollToItem(targetIdx)
                                            for (d in listOf(30L, 60L, 100L)) {
                                                kotlinx.coroutines.delay(d)
                                                try {
                                                    req?.requestFocus()
                                                    focused = true
                                                    break
                                                } catch (_: Exception) {}
                                            }
                                        }
                                        if (!focused) {
                                            runCatching { contentFocusRequesters[TvNavTab.MOVIES]?.requestFocus() }
                                                .onFailure { runCatching { moviesCategoryChipsFocusRequester.requestFocus() } }
                                        }
                                    }
                                }
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
                        focusRequester = seriesCategoryChipsFocusRequester,
                        downFocusRequester = contentFocusRequesters[TvNavTab.SERIES],
                        leftFocusRequester = sidebarFocusRequesters[TvNavTab.SERIES],
                        onNavigateLeft = { runCatching { sidebarFocusRequesters[TvNavTab.SERIES]?.requestFocus() } },
                        onNavigateDown = {
                            if (series.isNotEmpty()) {
                                runCatching { contentFocusRequesters[TvNavTab.SERIES]?.requestFocus() }
                            }
                        },
                        onSelectCategory = { catName ->
                            if (selectedSeriesCategory != catName) {
                                selectedSeriesCategory = catName
                                onSeriesCategorySelected(catName)
                                seriesPage = 1
                                onSeriesPageChange(1)
                                lastFocusedSeriesId = null
                                val found = seriesCategories.find { it.name == catName }
                                if (found != null) {
                                    onSelectSeriesCategory(found)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val seriesPageSize = 36
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
                            val seriesPaginationPrevFocusRequester = remember { FocusRequester() }
                            val seriesPaginationNextFocusRequester = remember { FocusRequester() }

                            val seriesCardFocusRequesters = remember(seriesPage, selectedSeriesCategory, pagedSeries.size) {
                                List(pagedSeries.size) { FocusRequester() }
                            }

                            // Restauration du focus et du scroll quand le dialogue de détail de série se ferme
                            var previousSeriesDetailWasOpen by remember { mutableStateOf(false) }
                            LaunchedEffect(selectedSeriesDetail) {
                                if (selectedSeriesDetail != null) {
                                    previousSeriesDetailWasOpen = true
                                } else if (previousSeriesDetailWasOpen) {
                                    previousSeriesDetailWasOpen = false
                                    val targetIdx = if (lastFocusedSeriesId != null) {
                                        pagedSeries.indexOfFirst { it.id == lastFocusedSeriesId }.takeIf { it >= 0 } ?: 0
                                    } else 0
                                    if (targetIdx in pagedSeries.indices) {
                                        seriesGridState.scrollToItem(targetIdx)
                                        kotlinx.coroutines.delay(80)
                                        val req = if (targetIdx == 0) contentFocusRequesters[TvNavTab.SERIES] else seriesCardFocusRequesters.getOrNull(targetIdx)
                                        runCatching { req?.requestFocus() }
                                    }
                                }
                            }

                            LazyVerticalGrid(
                                state = seriesGridState,
                                columns = GridCells.Fixed(6),
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(
                                    count = pagedSeries.size,
                                    key = { index -> pagedSeries[index].id },
                                    contentType = { "series_card" }
                                ) { index ->
                                    val ser = pagedSeries[index]
                                    val isBottomEdge = (index + 6 >= pagedSeries.size)
                                    val targetPaginationFocusRequester = when {
                                        seriesPage == 1 -> seriesPaginationNextFocusRequester
                                        seriesPage >= totalSeriesPages -> seriesPaginationPrevFocusRequester
                                        (index % 6) < 3 -> seriesPaginationPrevFocusRequester
                                        else -> seriesPaginationNextFocusRequester
                                    }

                                    TvSeriesGridCard(
                                        series = ser,
                                        focusRequester = if (index == 0) contentFocusRequesters[TvNavTab.SERIES] else seriesCardFocusRequesters.getOrNull(index),
                                        upFocusRequester = if (index < 6) seriesCategoryChipsFocusRequester else null,
                                        downFocusRequester = if (isBottomEdge) targetPaginationFocusRequester else null,
                                        leftFocusRequester = if (index % 6 == 0) sidebarFocusRequesters[TvNavTab.SERIES] else null,
                                        onNavigateLeft = if (index % 6 == 0) {
                                            { runCatching { sidebarFocusRequesters[TvNavTab.SERIES]?.requestFocus() } }
                                        } else null,
                                        onNavigateUp = if (index < 6) {
                                            { runCatching { seriesCategoryChipsFocusRequester.requestFocus() } }
                                        } else {
                                            {
                                                val prevIdx = index - 6
                                                if (prevIdx in pagedSeries.indices) {
                                                    coroutineScope.launch {
                                                        val isPrevVisible = seriesGridState.layoutInfo.visibleItemsInfo.any { it.index == prevIdx }
                                                        if (!isPrevVisible) {
                                                            seriesGridState.scrollToItem(prevIdx)
                                                        }
                                                        val r = if (prevIdx == 0) contentFocusRequesters[TvNavTab.SERIES] else seriesCardFocusRequesters.getOrNull(prevIdx)
                                                        runCatching { r?.requestFocus() }
                                                    }
                                                }
                                            }
                                        },
                                        onNavigateDown = if (isBottomEdge) {
                                            { runCatching { targetPaginationFocusRequester.requestFocus() } }
                                        } else {
                                            {
                                                val nextIdx = index + 6
                                                if (nextIdx in pagedSeries.indices) {
                                                    coroutineScope.launch {
                                                        val isNextVisible = seriesGridState.layoutInfo.visibleItemsInfo.any { it.index == nextIdx }
                                                        if (!isNextVisible) {
                                                            seriesGridState.scrollToItem(nextIdx)
                                                        }
                                                        val r = if (nextIdx == 0) contentFocusRequesters[TvNavTab.SERIES] else seriesCardFocusRequesters.getOrNull(nextIdx)
                                                        runCatching { r?.requestFocus() }
                                                    }
                                                }
                                            }
                                        },
                                        onFocus = {
                                            lastFocusedSeriesId = ser.id
                                            onSeriesFocused(ser.id)
                                        },
                                        onClick = {
                                            lastFocusedSeriesId = ser.id
                                            onSeriesFocused(ser.id)
                                            selectedSeriesDetail = ser
                                        }
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
                                    lastFocusedSeriesId = null
                                    coroutineScope.launch {
                                        runCatching { seriesGridState.scrollToItem(0) }
                                    }
                                },
                                isTv = true,
                                prevFocusRequester = seriesPaginationPrevFocusRequester,
                                nextFocusRequester = seriesPaginationNextFocusRequester,
                                sidebarFocusRequester = sidebarFocusRequesters[TvNavTab.SERIES],
                                onNavigateLeftToSidebar = {
                                    runCatching { sidebarFocusRequesters[TvNavTab.SERIES]?.requestFocus() }
                                },
                                onNavigateUpFromPrev = {
                                    coroutineScope.launch {
                                        val visibleIndices = seriesGridState.layoutInfo.visibleItemsInfo
                                            .map { it.index }
                                            .filter { it in pagedSeries.indices }

                                         val lastIdx = if (lastFocusedSeriesId != null) {
                                             pagedSeries.indexOfFirst { it.id == lastFocusedSeriesId }
                                         } else -1

                                         val targetIdx = when {
                                             lastIdx in visibleIndices -> lastIdx
                                             visibleIndices.isNotEmpty() -> {
                                                 val lastVisible = visibleIndices.last()
                                                 val lastVisibleRowStart = maxOf(visibleIndices.first(), (lastVisible / 6) * 6)
                                                 lastVisibleRowStart
                                             }
                                             else -> maxOf(0, ((pagedSeries.size - 1) / 6) * 6)
                                         }

                                         val req = if (targetIdx == 0) contentFocusRequesters[TvNavTab.SERIES] else seriesCardFocusRequesters.getOrNull(targetIdx)
                                         var focused = false
                                         try {
                                             req?.requestFocus()
                                             focused = true
                                         } catch (_: Exception) {}

                                         if (!focused && targetIdx in pagedSeries.indices) {
                                             seriesGridState.scrollToItem(targetIdx)
                                             for (d in listOf(30L, 60L, 100L)) {
                                                 kotlinx.coroutines.delay(d)
                                                 try {
                                                     req?.requestFocus()
                                                     focused = true
                                                     break
                                                 } catch (_: Exception) {}
                                             }
                                         }
                                         if (!focused) {
                                             runCatching { contentFocusRequesters[TvNavTab.SERIES]?.requestFocus() }
                                                 .onFailure { runCatching { seriesCategoryChipsFocusRequester.requestFocus() } }
                                         }
                                     }
                                 },
                                 onNavigateUpFromNext = {
                                     coroutineScope.launch {
                                         val visibleIndices = seriesGridState.layoutInfo.visibleItemsInfo
                                             .map { it.index }
                                             .filter { it in pagedSeries.indices }

                                         val lastIdx = if (lastFocusedSeriesId != null) {
                                             pagedSeries.indexOfFirst { it.id == lastFocusedSeriesId }
                                         } else -1

                                         val targetIdx = when {
                                             lastIdx in visibleIndices -> lastIdx
                                             visibleIndices.isNotEmpty() -> visibleIndices.last()
                                             else -> pagedSeries.size - 1
                                         }

                                         val req = if (targetIdx == 0) contentFocusRequesters[TvNavTab.SERIES] else seriesCardFocusRequesters.getOrNull(targetIdx)
                                         var focused = false
                                         try {
                                             req?.requestFocus()
                                             focused = true
                                         } catch (_: Exception) {}

                                         if (!focused && targetIdx in pagedSeries.indices) {
                                             seriesGridState.scrollToItem(targetIdx)
                                             for (d in listOf(30L, 60L, 100L)) {
                                                 kotlinx.coroutines.delay(d)
                                                 try {
                                                     req?.requestFocus()
                                                     focused = true
                                                     break
                                                 } catch (_: Exception) {}
                                             }
                                         }
                                         if (!focused) {
                                             runCatching { contentFocusRequesters[TvNavTab.SERIES]?.requestFocus() }
                                                 .onFailure { runCatching { seriesCategoryChipsFocusRequester.requestFocus() } }
                                         }
                                     }
                                 }
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
                        categories = categories,
                        vodCategories = vodCategories,
                        seriesCategories = seriesCategories,
                        sessionManager = sessionManager,
                        focusRequester = contentFocusRequesters[TvNavTab.FAVORITES]!!,
                        onNavigateLeftToSidebar = {
                            runCatching { sidebarFocusRequesters[TvNavTab.FAVORITES]?.requestFocus() }
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
                        liveCategories = categories,
                        vodCategories = vodCategories,
                        seriesCategories = seriesCategories,
                        sessionManager = sessionManager,
                        focusRequester = contentFocusRequesters[TvNavTab.FILTERS]!!,
                        onNavigateLeftToSidebar = {
                            runCatching { sidebarFocusRequesters[TvNavTab.FILTERS]?.requestFocus() }
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
                        focusRequester = contentFocusRequesters[TvNavTab.SETTINGS]!!,
                        onNavigateLeft = { runCatching { sidebarFocusRequesters[TvNavTab.SETTINGS]?.requestFocus() } },
                        onOpenLogin = onOpenLogin,
                        onLogout = onLogout,
                        onLanguageChanged = onLanguageChanged,
                        onRefreshCatalog = onRefreshCatalog,
                        onUpdateStatusChanged = onUpdateStatusChanged
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
                sessionManager = sessionManager,
                onPlay = { m, startPos ->
                    selectedMovieDetail = null
                    onSelectMovie(m, startPos)
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
                sessionManager = sessionManager,
                onPlayEpisode = { ep, startPos ->
                    val s = selectedSeriesDetail!!
                    selectedSeriesDetail = null
                    onSelectEpisode(s, ep, startPos)
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

        // ==================== DIALOGUE DE CONFIRMATION DE FERMETURE COMPLÈTE ====================
        if (showExitDialog) {
            TvExitConfirmDialog(
                onDismiss = { showExitDialog = false },
                onConfirmExit = {
                    showExitDialog = false
                    playerEngine?.stop()
                    (context as? Activity)?.finishAffinity()
                }
            )
        }
    }
}

/**
 * Barre latérale avec 7 onglets au style Glassy Apple (Dock capsule flottante, centré verticalement)
 */
@Composable
fun TvSidebar(
    selectedTab: TvNavTab,
    sidebarFocusRequesters: Map<TvNavTab, FocusRequester>,
    contentFocusRequester: FocusRequester? = null,
    hasUpdateAvailable: Boolean = false,
    onTabSelected: (TvNavTab) -> Unit,
    onNavigateRight: () -> Boolean
) {
    val strings = LocalStrings.current
    val tabList = remember { TvNavTab.values().toList() }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(start = 12.dp, end = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        val dockShape = RoundedCornerShape(28.dp)

        Column(
            modifier = Modifier
                .width(68.dp)
                .wrapContentHeight()
                .shadow(
                    elevation = 20.dp,
                    shape = dockShape,
                    clip = false,
                    ambientColor = Color(0x66000000),
                    spotColor = Color(0x99000000)
                )
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xCC1A2338), // Apple frosted glass top (80% opacity)
                        0.45f to Color(0xB3111827), // Frosted translucent mid
                        1.0f to Color(0xCC0B101D)  // Deep frosted bottom
                    ),
                    shape = dockShape
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.40f), // Apple specular rim reflection
                        0.3f to Color.White.copy(alpha = 0.15f),
                        0.7f to Color.White.copy(alpha = 0.05f),
                        1.0f to Color.White.copy(alpha = 0.18f)
                    ),
                    shape = dockShape
                )
                .padding(horizontal = 5.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                val itemShape = RoundedCornerShape(14.dp)

                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(48.dp)
                        .scale(scale)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionRight, Key.Enter, Key.DirectionCenter -> {
                                        val handled = onNavigateRight()
                                        handled
                                    }
                                    Key.DirectionDown -> {
                                        if (nextTab != null) {
                                            runCatching { sidebarFocusRequesters[nextTab]?.requestFocus() }
                                            true
                                        } else false
                                    }
                                    Key.DirectionUp -> {
                                        if (prevTab != null) {
                                            runCatching { sidebarFocusRequesters[prevTab]?.requestFocus() }
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .focusRequester(myRequester)
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) {
                                onTabSelected(tab)
                            }
                        }
                        .clickable {
                            onTabSelected(tab)
                            onNavigateRight()
                        }
                        .shadow(
                            elevation = if (isFocused) 8.dp else 0.dp,
                            shape = itemShape,
                            clip = false,
                            ambientColor = Color(0x663888FF),
                            spotColor = Color(0x993888FF)
                        )
                        .background(
                            brush = when {
                                isFocused -> Brush.verticalGradient(
                                    listOf(
                                        Color(0xFF3888FF),
                                        Color(0xFF1E5BB8)
                                    )
                                )
                                isSelected -> Brush.verticalGradient(
                                    listOf(
                                        Color(0x383888FF),
                                        Color(0x1F3888FF)
                                    )
                                )
                                else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                            shape = itemShape
                        )
                        .border(
                            width = if (isFocused) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                            brush = when {
                                isFocused -> Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.85f),
                                        Color(0xFF93C5FD).copy(alpha = 0.5f)
                                    )
                                )
                                isSelected -> Brush.verticalGradient(
                                    listOf(
                                        NoosCyan.copy(alpha = 0.6f),
                                        NoosBlue.copy(alpha = 0.3f)
                                    )
                                )
                                else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                            },
                            shape = itemShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val tabLabel = tab.getLabel(strings)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tabLabel,
                                tint = if (isFocused) Color.White else if (isSelected) NoosCyan else TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                            if (tab == TvNavTab.SETTINGS && hasUpdateAvailable) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-2).dp)
                                        .background(Color(0xFFEF4444), CircleShape)
                                        .border(1.dp, Color(0xFF0B101D), CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tabLabel,
                            color = if (isFocused) Color.White else if (isSelected) NoosCyan else TextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onSelectCategory: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEachIndexed { index, category ->
            val isSelected = category.equals(selectedCategory, ignoreCase = true)
            var isFocused by remember { mutableStateOf(false) }

            var chipMod = Modifier
                .clip(RoundedCornerShape(50))
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionLeft -> {
                                if (index == 0 && onNavigateLeft != null) {
                                    runCatching { onNavigateLeft() }
                                    true
                                } else false
                            }
                            Key.DirectionDown -> {
                                if (onNavigateDown != null) {
                                    runCatching { onNavigateDown() }
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                }

            if (focusRequester != null && index == 0) {
                chipMod = chipMod.focusRequester(focusRequester)
            }

            Box(
                modifier = chipMod
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable { onSelectCategory(category) }
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
        // 1. Image du programme en cours en arrière-plan plein format avec downsampling 400x225
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(backdropUrl)
                .size(width = 400, height = 225)
                .crossfade(true)
                .allowHardware(false)
                .build(),
            contentDescription = currentProgram.title,
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xDD0B0F17))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                .padding(3.dp),
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
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = LocalSoundEffectManager.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "vod_scale")

    var cardModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f) // Ratio standard d'affiche de film (ex: 200x300, 500x750)
        .scale(scale)
        .clip(RoundedCornerShape(18.dp))
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        if (onNavigateLeft != null) {
                            runCatching { onNavigateLeft() }
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (onNavigateRight != null) {
                            runCatching { onNavigateRight() }
                            true
                        } else false
                    }
                    Key.DirectionUp -> {
                        if (onNavigateUp != null) {
                            runCatching { onNavigateUp() }
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (onNavigateDown != null) {
                            runCatching { onNavigateDown() }
                            true
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        soundManager?.playSelect()
                        onClick()
                        true
                    }
                    else -> false
                }
            } else false
        }

    if (focusRequester != null) {
        cardModifier = cardModifier.focusRequester(focusRequester)
    }

    Box(
        modifier = cardModifier
            .onFocusChanged {
                if (it.isFocused && !isFocused) {
                    soundManager?.playFocus()
                    onFocus?.invoke()
                }
                isFocused = it.isFocused
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                soundManager?.playSelect()
                onClick()
            }
            .background(if (isFocused) Color(0xFF1E2838) else CardBackground)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        // 1. Affiche plein format respectant le ratio 2:3 avec downsampling mémoire 300x450
        if (!movie.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(movie.posterUrl)
                    .size(width = 300, height = 450)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = movie.title,
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
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val soundManager = LocalSoundEffectManager.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, label = "series_scale")

    var cardModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(2f / 3f) // Ratio standard d'affiche de série (ex: 200x300, 500x750)
        .scale(scale)
        .clip(RoundedCornerShape(18.dp))
        .onPreviewKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        if (onNavigateLeft != null) {
                            runCatching { onNavigateLeft() }
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (onNavigateRight != null) {
                            runCatching { onNavigateRight() }
                            true
                        } else false
                    }
                    Key.DirectionUp -> {
                        if (onNavigateUp != null) {
                            runCatching { onNavigateUp() }
                            true
                        } else false
                    }
                    Key.DirectionDown -> {
                        if (onNavigateDown != null) {
                            runCatching { onNavigateDown() }
                            true
                        } else false
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        soundManager?.playSelect()
                        onClick()
                        true
                    }
                    else -> false
                }
            } else false
        }

    if (focusRequester != null) {
        cardModifier = cardModifier.focusRequester(focusRequester)
    }

    Box(
        modifier = cardModifier
            .onFocusChanged {
                if (it.isFocused && !isFocused) {
                    soundManager?.playFocus()
                    onFocus?.invoke()
                }
                isFocused = it.isFocused
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                soundManager?.playSelect()
                onClick()
            }
            .background(if (isFocused) Color(0xFF1E2838) else CardBackground)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        // 1. Affiche plein format respectant le ratio 2:3 avec downsampling mémoire 300x450
        if (!series.posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(series.posterUrl)
                    .size(width = 300, height = 450)
                    .crossfade(true)
                    .allowHardware(false)
                    .build(),
                contentDescription = series.title,
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
 * Indicateur de chargement sobre et moderne
 */
@Composable
fun TvLoadingProgress(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color(0x66182236), Color(0x440C1220))
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = NoosCyan,
                    strokeWidth = 2.8.dp
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
    sessionManager: SessionManager? = null,
    onPlay: (VodMovie, Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onFetchFullInfo: (suspend (String) -> VodMovie?)? = null
) {
    val playFocusRequester = remember { FocusRequester() }
    val restartFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }
    val favFocusRequester = remember { FocusRequester() }
    val bottomCloseFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val view = LocalView.current
    var fullMovie by remember(movie.id) { mutableStateOf(movie) }
    var isFetchingInfo by remember(movie.id) { mutableStateOf(onFetchFullInfo != null && movie.plot.isNullOrBlank()) }
    val resumePoint = remember(movie.id) {
        sessionManager?.getPlaybackResume("movie_${movie.id}")
    }

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
            .focusProperties {
                exit = { FocusRequester.Cancel }
            }
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

                            var isCloseIconFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .focusRequester(closeFocusRequester)
                                    .onFocusChanged { isCloseIconFocused = it.isFocused }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown) {
                                            when (event.key) {
                                                Key.DirectionDown -> {
                                                    view.post { runCatching { playFocusRequester.requestFocus() } }
                                                    true
                                                }
                                                Key.Enter, Key.DirectionCenter -> {
                                                    onDismiss()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .focusable()
                                    .clickable { onDismiss() }
                                    .background(
                                        if (isCloseIconFocused) Color(0xFFE50914)
                                        else SurfaceDarkVariant
                                    )
                                    .border(
                                        width = if (isCloseIconFocused) 2.5.dp else 1.dp,
                                        color = if (isCloseIconFocused) Color.White else CardBorderUnfocused,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fermer",
                                    tint = if (isCloseIconFocused) Color.White else TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
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

                    if (resumePoint != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceDarkVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "▶ Reprise de lecture à ${resumePoint.formattedPosition}",
                                    color = NoosCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${resumePoint.progressPercent}% terminé",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = resumePoint.progressFraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = NoosCyan,
                                trackColor = Color(0xFF233045)
                            )
                        }
                    }

                    // Boutons d'action TV
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var isPlayFocused by remember { mutableStateOf(false) }
                        var isRestartFocused by remember { mutableStateOf(false) }
                        var isFavFocused by remember { mutableStateOf(false) }
                        var isCloseFocused by remember { mutableStateOf(false) }

                        // Bouton Play / Reprendre (Focus initial)
                        Button(
                            onClick = {
                                if (resumePoint != null) {
                                    onPlay(fullMovie, resumePoint.positionMs)
                                } else {
                                    onPlay(fullMovie, 0L)
                                }
                            },
                            modifier = Modifier
                                .focusRequester(playFocusRequester)
                                .onFocusChanged { isPlayFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        view.post { runCatching { closeFocusRequester.requestFocus() } }
                                        true
                                    } else false
                                }
                                .focusable(),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isPlayFocused) NoosCyan else NoosBlue),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isPlayFocused) Color.Black else Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (resumePoint != null) "Reprendre (${resumePoint.formattedPosition})" else "Lancer le film",
                                color = if (isPlayFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // Bouton Recommencer si reprise existante
                        if (resumePoint != null) {
                            OutlinedButton(
                                onClick = {
                                    sessionManager?.clearPlaybackResume("movie_${fullMovie.id}")
                                    onPlay(fullMovie, 0L)
                                },
                                modifier = Modifier
                                    .focusRequester(restartFocusRequester)
                                    .onFocusChanged { isRestartFocused = it.isFocused }
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                            view.post { runCatching { closeFocusRequester.requestFocus() } }
                                            true
                                        } else false
                                    }
                                    .focusable(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isRestartFocused) Color(0xFF1E2838) else Color.Transparent
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isRestartFocused) 2.dp else 1.dp,
                                    color = if (isRestartFocused) FocusGlow else CardBorderUnfocused
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = if (isRestartFocused) FocusGlow else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Recommencer", color = if (isRestartFocused) Color.White else TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Bouton Favoris
                        OutlinedButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .focusRequester(favFocusRequester)
                                .onFocusChanged { isFavFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        view.post { runCatching { closeFocusRequester.requestFocus() } }
                                        true
                                    } else false
                                }
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
                                .focusRequester(bottomCloseFocusRequester)
                                .onFocusChanged { isCloseFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                                        view.post { runCatching { closeFocusRequester.requestFocus() } }
                                        true
                                    } else false
                                }
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
    sessionManager: SessionManager? = null,
    onPlayEpisode: (Episode, Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onFetchFullInfo: (suspend (String) -> Series?)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val closeFocusRequester = remember { FocusRequester() }
    val seasonFocusRequester = remember { FocusRequester() }
    val episodeFocusRequester = remember { FocusRequester() }
    val favBtnFocusRequester = remember { FocusRequester() }
    val closeBtnUnderPosterFocusRequester = remember { FocusRequester() }
    val directPlayFocusRequester = remember { FocusRequester() }
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    var isCloseIconFocused by remember { mutableStateOf(false) }

    var fullSeries by remember(series.id) { mutableStateOf(series) }
    var isLoadingInfo by remember(series.id) { mutableStateOf(onFetchFullInfo != null && (series.seasons.isEmpty() || series.seasons.all { it.episodes.size <= 1 })) }

    LaunchedEffect(series.id) {
        if (onFetchFullInfo != null && (fullSeries.seasons.isEmpty() || fullSeries.seasons.all { it.episodes.size <= 1 })) {
            isLoadingInfo = true
            val fetched = onFetchFullInfo(series.id)
            if (fetched != null && fetched.seasons.isNotEmpty()) {
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

    LaunchedEffect(seasons.size, isLoadingInfo) {
        kotlinx.coroutines.delay(120)
        val res = runCatching {
            closeFocusRequester.requestFocus()
        }
        android.util.Log.d("TV_NAV", "Initial requestFocus result: $res (seasons=${seasons.size}, loading=$isLoadingInfo)")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xEE080A0F))
            .focusProperties {
                exit = { FocusRequester.Cancel }
            }
            .onPreviewKeyEvent { keyEvent ->
                android.util.Log.d("TV_NAV", "Root modal onPreviewKeyEvent: key=${keyEvent.key} type=${keyEvent.type}")
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
                                .focusRequester(favBtnFocusRequester)
                                .onFocusChanged { isFavFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionUp -> {
                                                view.post { runCatching { closeFocusRequester.requestFocus() } }
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                view.post { runCatching { closeBtnUnderPosterFocusRequester.requestFocus() } }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
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
                                .focusRequester(closeBtnUnderPosterFocusRequester)
                                .onFocusChanged { isCloseBtnFocused = it.isFocused }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionUp -> {
                                                view.post { runCatching { closeFocusRequester.requestFocus() } }
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                view.post { runCatching { favBtnFocusRequester.requestFocus() } }
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                view.post {
                                                    runCatching {
                                                        if (seasons.isNotEmpty()) seasonFocusRequester.requestFocus()
                                                        else directPlayFocusRequester.requestFocus()
                                                    }
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
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

                        // Bouton Croix Fermer (en haut à droite)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .focusRequester(closeFocusRequester)
                                .onFocusChanged {
                                    isCloseIconFocused = it.isFocused
                                    android.util.Log.d("TV_NAV", "Series Close icon isFocused=${it.isFocused}")
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(50)
                                                    runCatching {
                                                        if (seasons.isNotEmpty()) seasonFocusRequester.requestFocus()
                                                        else directPlayFocusRequester.requestFocus()
                                                    }
                                                }
                                                true
                                            }
                                            Key.DirectionLeft -> {
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(50)
                                                    runCatching { closeBtnUnderPosterFocusRequester.requestFocus() }
                                                }
                                                true
                                            }
                                            Key.Enter, Key.DirectionCenter -> {
                                                onDismiss()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .clickable { onDismiss() }
                                .background(
                                    if (isCloseIconFocused) Color(0xFFE50914)
                                    else SurfaceDarkVariant
                                )
                                .border(
                                    width = if (isCloseIconFocused) 2.5.dp else 1.dp,
                                    color = if (isCloseIconFocused) Color.White else CardBorderUnfocused,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = if (isCloseIconFocused) Color.White else TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
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
                                         onPlayEpisode(fallbackEp, 0L)
                                    },
                                    modifier = Modifier
                                        .focusRequester(directPlayFocusRequester)
                                        .onFocusChanged { isDirectPlayFocused = it.isFocused }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionUp -> {
                                                        view.post { runCatching { closeFocusRequester.requestFocus() } }
                                                        true
                                                    }
                                                    Key.DirectionLeft -> {
                                                        view.post { runCatching { closeBtnUnderPosterFocusRequester.requestFocus() } }
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            seasons.forEachIndexed { idx, s ->
                                val isSelected = idx == selectedSeasonIndex
                                var isChipFocused by remember { mutableStateOf(false) }

                                Box(
                                    modifier = (if (idx == 0) Modifier.focusRequester(seasonFocusRequester) else Modifier)
                                        .onFocusChanged {
                                            isChipFocused = it.isFocused
                                            android.util.Log.d("TV_NAV", "Season chip idx=$idx isFocused=${it.isFocused}")
                                            if (it.isFocused) {
                                                selectedSeasonIndex = idx
                                            }
                                        }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionUp -> {
                                                        coroutineScope.launch {
                                                            kotlinx.coroutines.delay(50)
                                                            runCatching { closeFocusRequester.requestFocus() }
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionLeft -> {
                                                        if (idx == 0) {
                                                            coroutineScope.launch {
                                                                kotlinx.coroutines.delay(50)
                                                                runCatching { closeBtnUnderPosterFocusRequester.requestFocus() }
                                                            }
                                                            true
                                                        } else false
                                                    }
                                                    Key.DirectionDown -> {
                                                        coroutineScope.launch {
                                                            kotlinx.coroutines.delay(50)
                                                            runCatching { episodeFocusRequester.requestFocus() }
                                                        }
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .focusable()
                                        .clickable { selectedSeasonIndex = idx }
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (isChipFocused) Color.White
                                            else if (isSelected) NoosBlue.copy(alpha = 0.35f)
                                            else SurfaceDarkVariant
                                        )
                                        .border(
                                            width = if (isChipFocused) 2.5.dp else 1.dp,
                                            color = if (isChipFocused) FocusGlow else if (isSelected) NoosBlue else CardBorderUnfocused,
                                            shape = RoundedCornerShape(50)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = s.name.ifBlank { "Saison ${s.seasonNumber}" },
                                        color = if (isChipFocused) Color.Black else if (isSelected) Color.White else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Liste des Épisodes de la saison sélectionnée
                        val episodeList = currentSeason?.episodes ?: emptyList()
                        Text("Épisodes (${episodeList.size})", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            itemsIndexed(episodeList, key = { _, ep -> "${ep.seasonNumber}_${ep.episodeNumber}_${ep.id}" }) { epIndex, ep ->
                                var isEpFocused by remember { mutableStateOf(false) }
                                val epResume = remember(ep.id) {
                                    sessionManager?.getPlaybackResume("ep_${ep.id}")
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (epIndex == 0) Modifier.focusRequester(episodeFocusRequester) else Modifier)
                                        .onFocusChanged { isEpFocused = it.isFocused }
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown) {
                                                when (event.key) {
                                                    Key.DirectionUp -> {
                                                        if (epIndex == 0) {
                                                            coroutineScope.launch {
                                                                kotlinx.coroutines.delay(50)
                                                                runCatching {
                                                                    if (seasons.isNotEmpty()) seasonFocusRequester.requestFocus()
                                                                    else closeFocusRequester.requestFocus()
                                                                }
                                                            }
                                                            true
                                                        } else false
                                                    }
                                                    Key.DirectionLeft -> {
                                                        coroutineScope.launch {
                                                            kotlinx.coroutines.delay(50)
                                                            runCatching { closeBtnUnderPosterFocusRequester.requestFocus() }
                                                        }
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else false
                                        }
                                        .focusable()
                                        .clickable { onPlayEpisode(ep, epResume?.positionMs ?: 0L) }
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isEpFocused) Color(0xFF1E2838) else SurfaceDarkVariant)
                                        .border(
                                            width = if (isEpFocused) 2.5.dp else 1.dp,
                                            color = if (isEpFocused) FocusGlow else CardBorderUnfocused,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                                    if (epResume != null) {
                                                        Text("•", color = TextSecondary, fontSize = 10.sp)
                                                        Text("Reprise ${epResume.formattedPosition} (${epResume.progressPercent}%)", color = NoosCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                if (epResume != null) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = epResume.progressFraction,
                                                        modifier = Modifier
                                                            .width(160.dp)
                                                            .height(3.dp)
                                                            .clip(RoundedCornerShape(2.dp)),
                                                        color = NoosCyan,
                                                        trackColor = Color(0xFF233045)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = if (epResume != null) "▶ Reprendre" else "▶ Lire",
                                            color = if (isEpFocused || epResume != null) NoosCyan else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
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

