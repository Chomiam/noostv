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
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noostv.R
import io.noostv.core.entitlement.EntitlementManager
import io.noostv.core.player.PlayerEngine
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Category
import io.noostv.data.model.Channel
import io.noostv.data.model.EpgProgram
import io.noostv.data.model.Series
import io.noostv.data.model.VodMovie
import io.noostv.ui.common.HdrBadge
import io.noostv.ui.common.LiveIndicatorBadge
import io.noostv.ui.common.PremiumVipBadge
import io.noostv.ui.common.ResolutionBadge
import io.noostv.ui.settings.TvSettingsContent
import io.noostv.ui.theme.*

/**
 * 5 Catégories latérales :
 * 1. TV (Direct)
 * 2. Guide TV (EPG)
 * 3. Films (VOD Films)
 * 4. Séries (VOD Séries)
 * 5. Settings (Paramètres & Mises à jour GitHub)
 */
enum class TvNavTab(val label: String, val icon: ImageVector) {
    TV("TV", Icons.Default.Tv),
    EPG("Guide TV", Icons.Default.DateRange),
    MOVIES("Films", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary),
    SETTINGS("Settings", Icons.Default.Settings)
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
    onSelectVodCategory: (Category) -> Unit,
    onSelectSeriesCategory: (Category) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenLogin: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(TvNavTab.TV) }
    var selectedLiveCategory by remember { mutableStateOf("Toutes") }
    var selectedVodCategory by remember { mutableStateOf("Toutes") }
    var selectedSeriesCategory by remember { mutableStateOf("Toutes") }
    var previewChannel by remember { mutableStateOf(initialPreviewChannel) }

    // Interception de la touche Retour télécommande pour quitter le mode prévisualisation 1/4
    BackHandler(enabled = previewChannel != null) {
        playerEngine?.stop()
        previewChannel = null
        onClearCurrentChannel?.invoke()
    }

    val subscription by entitlementManager.subscription.collectAsState()

    // Focus Requesters pour navigation fluide télécommande D-Pad
    val sidebarFocusRequesters = remember {
        mapOf(
            TvNavTab.TV to FocusRequester(),
            TvNavTab.EPG to FocusRequester(),
            TvNavTab.MOVIES to FocusRequester(),
            TvNavTab.SERIES to FocusRequester(),
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkOledBackground)
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
            // Header supérieur (Logo officiel NOOS, statut VIP, Recherche, Identifiants)
            TvHeader(
                isPremium = subscription.isPremium,
                onOpenSearch = onOpenSearch,
                onOpenUpgrade = onOpenUpgrade,
                onOpenLogin = onOpenLogin
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
                    val vodCatNames = remember(vodCategories) {
                        listOf("Toutes") + vodCategories.map { it.name }
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

                    if (isVodLoading) {
                        TvLoadingProgress(message = "Chargement des films de $selectedVodCategory...")
                    } else if (movies.isEmpty()) {
                        TvEmptyState(message = "Aucun film disponible dans cette catégorie")
                    } else {
                        // 6 colonnes avec ratio standard d'affiche 2:3 (non rognée)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(movies.size) { index ->
                                val movie = movies[index]
                                TvMovieGridCard(
                                    movie = movie,
                                    onNavigateLeft = if (index % 6 == 0) {
                                        { sidebarFocusRequesters[TvNavTab.MOVIES]?.requestFocus() }
                                    } else null,
                                    onClick = { onSelectMovie(movie) }
                                )
                            }
                        }
                    }
                }

                // ==================== 4. ONGLET SÉRIES (VOD) — RATIO CINÉMA 2:3 ====================
                TvNavTab.SERIES -> {
                    val seriesCatNames = remember(seriesCategories) {
                        listOf("Toutes") + seriesCategories.map { it.name }
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

                    if (isSeriesLoading) {
                        TvLoadingProgress(message = "Chargement des séries de $selectedSeriesCategory...")
                    } else if (series.isEmpty()) {
                        TvEmptyState(message = "Aucune série disponible dans cette catégorie")
                    } else {
                        // 6 colonnes avec ratio standard d'affiche 2:3 (non rognée)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(series.size) { index ->
                                val ser = series[index]
                                TvSeriesGridCard(
                                    series = ser,
                                    onNavigateLeft = if (index % 6 == 0) {
                                        { sidebarFocusRequesters[TvNavTab.SERIES]?.requestFocus() }
                                    } else null,
                                    onClick = { onSelectSeries(ser) }
                                )
                            }
                        }
                    }
                }

                // ==================== 5. ONGLET SETTINGS (PARAMÈTRES & MISES À JOUR) ====================
                TvNavTab.SETTINGS -> {
                    TvSettingsContent(
                        sessionManager = sessionManager,
                        focusRequester = contentFocusRequester,
                        onNavigateLeft = { sidebarFocusRequesters[TvNavTab.SETTINGS]?.requestFocus() },
                        onOpenLogin = onOpenLogin
                    )
                }
            }
        }
    }
}

/**
 * Barre latérale avec 4 onglets sobres et arrondis (TV, Guide TV, Films, Séries)
 */
@Composable
fun TvSidebar(
    selectedTab: TvNavTab,
    sidebarFocusRequesters: Map<TvNavTab, FocusRequester>,
    onTabSelected: (TvNavTab) -> Unit,
    onNavigateRight: () -> Unit
) {
    val tabList = remember { TvNavTab.values().toList() }

    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(SurfaceDark)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        tabList.forEachIndexed { index, tab ->
            val isSelected = tab == selectedTab
            var isFocused by remember { mutableStateOf(false) }

            val scale by animateFloatAsState(
                targetValue = if (isFocused) 1.10f else 1.0f,
                label = "sidebar_scale"
            )

            val myRequester = sidebarFocusRequesters[tab] ?: remember { FocusRequester() }
            val nextTab = tabList.getOrNull(index + 1)
            val prevTab = tabList.getOrNull(index - 1)

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(20.dp))
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
                            isFocused -> Color(0xFF1E2838)
                            isSelected -> NoosBlue.copy(alpha = 0.22f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (isFocused) 3.dp else if (isSelected) 1.dp else 0.dp,
                        color = if (isFocused) FocusGlow else if (isSelected) NoosBlue.copy(alpha = 0.6f) else Color.Transparent,
                        shape = RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (isFocused) Color.White else if (isSelected) NoosCyan else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = if (isFocused) Color.White else if (isSelected) NoosCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * En-tête supérieur moderne : Logo officiel NOOS, pill TV en dégradé, boutons arrondis
 */
@Composable
fun TvHeader(
    isPremium: Boolean,
    onOpenSearch: () -> Unit,
    onOpenUpgrade: () -> Unit,
    onOpenLogin: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo Officiel NOOS + Badge Gradient TV
        Row(
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
                    Text("Débloquer VIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Actions rapides sobres avec forme pilule moderne (50%)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Bouton Recherche Pilule
            Button(
                onClick = onOpenSearch,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderUnfocused),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Recherche", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // Bouton Identifiants IPTV Pilule
            Button(
                onClick = onOpenLogin,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderUnfocused),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Identifiants IPTV", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            ResolutionBadge(resolution = "4K HDR")
        }
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
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
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
                                .onFocusChanged { isRowFocused = it.isFocused }
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
                                        .onFocusChanged { isProgFocused = it.isFocused }
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
