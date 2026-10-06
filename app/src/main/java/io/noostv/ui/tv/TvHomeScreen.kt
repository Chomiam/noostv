package io.noostv.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.noostv.core.entitlement.EntitlementManager
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

/**
 * 4 Catégories latérales demandées :
 * 1. TV (Direct)
 * 2. Guide TV (EPG)
 * 3. Films (VOD Films)
 * 4. Séries (VOD Séries)
 */
enum class TvNavTab(val label: String, val icon: ImageVector) {
    TV("TV", Icons.Default.Tv),
    EPG("Guide TV", Icons.Default.DateRange),
    MOVIES("Films", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.VideoLibrary)
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
    var selectedTab by remember { mutableStateOf(TvNavTab.TV) }
    var selectedLiveCategory by remember { mutableStateOf("Toutes") }
    var selectedVodCategory by remember { mutableStateOf("Toutes") }
    var selectedSeriesCategory by remember { mutableStateOf("Toutes") }

    val subscription by entitlementManager.subscription.collectAsState()

    // Focus Requesters pour navigation fluide télécommande D-Pad
    val sidebarFocusRequesters = remember {
        mapOf(
            TvNavTab.TV to FocusRequester(),
            TvNavTab.EPG to FocusRequester(),
            TvNavTab.MOVIES to FocusRequester(),
            TvNavTab.SERIES to FocusRequester()
        )
    }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        sidebarFocusRequesters[TvNavTab.TV]?.requestFocus()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == TvNavTab.MOVIES && movies.isEmpty() && vodCategories.isNotEmpty()) {
            onSelectVodCategory(vodCategories.first())
        } else if (selectedTab == TvNavTab.SERIES && series.isEmpty() && seriesCategories.isNotEmpty()) {
            onSelectSeriesCategory(seriesCategories.first())
        }
    }

    // Filtrage des chaînes en direct selon la catégorie sélectionnée
    val filteredChannels = remember(channels, selectedLiveCategory) {
        if (selectedLiveCategory == "Toutes") {
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
        // ==================== BARRE LATÉRALE GAUCHE : TV, GUIDE TV, FILMS, SÉRIES ====================
        TvSidebar(
            selectedTab = selectedTab,
            sidebarFocusRequesters = sidebarFocusRequesters,
            onTabSelected = { tab ->
                selectedTab = tab
            },
            onNavigateRight = {
                contentFocusRequester.requestFocus()
            }
        )

        // ==================== CONTENU PRINCIPAL SELON L'ONGLET ACTIF ====================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 18.dp, end = 24.dp)
        ) {
            // Header supérieur (Logo, statut VIP, Recherche, Identifiants)
            TvHeader(
                selectedTab = selectedTab,
                isPremium = subscription.isPremium,
                onOpenSearch = onOpenSearch,
                onOpenUpgrade = onOpenUpgrade,
                onOpenLogin = onOpenLogin
            )

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                // ==================== 1. ONGLET TV (DIRECT) ====================
                TvNavTab.TV -> {
                    val liveCatNames = remember(categories, channels) {
                        listOf("Toutes") + (if (categories.isNotEmpty()) categories.map { it.name } else channels.map { it.categoryName }.distinct())
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
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(filteredChannels.size) { index ->
                                val channel = filteredChannels[index]
                                TvChannelGridCard(
                                    channel = channel,
                                    onNavigateLeft = if (index % 4 == 0) {
                                        { sidebarFocusRequesters[TvNavTab.TV]?.requestFocus() }
                                    } else null,
                                    onClick = { onSelectChannel(channel) }
                                )
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

                // ==================== 3. ONGLET FILMS (VOD) ====================
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(movies.size) { index ->
                                val movie = movies[index]
                                TvMovieGridCard(
                                    movie = movie,
                                    onNavigateLeft = if (index % 5 == 0) {
                                        { sidebarFocusRequesters[TvNavTab.MOVIES]?.requestFocus() }
                                    } else null,
                                    onClick = { onSelectMovie(movie) }
                                )
                            }
                        }
                    }
                }

                // ==================== 4. ONGLET SÉRIES (VOD) ====================
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(series.size) { index ->
                                val ser = series[index]
                                TvSeriesGridCard(
                                    series = ser,
                                    onNavigateLeft = if (index % 5 == 0) {
                                        { sidebarFocusRequesters[TvNavTab.SERIES]?.requestFocus() }
                                    } else null,
                                    onClick = { onSelectSeries(ser) }
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
 * Barre latérale avec 4 onglets : TV, Guide TV, Films, Séries
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
            .width(96.dp)
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
                targetValue = if (isFocused) 1.15f else 1.0f,
                label = "sidebar_scale"
            )

            val myRequester = sidebarFocusRequesters[tab] ?: remember { FocusRequester() }
            val nextTab = tabList.getOrNull(index + 1)
            val prevTab = tabList.getOrNull(index - 1)

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(14.dp))
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
                            isFocused -> Color(0xFF1D3554)
                            isSelected -> NeonCyan.copy(alpha = 0.25f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (isFocused) 3.5.dp else if (isSelected) 1.5.dp else 0.dp,
                        color = if (isFocused) FocusGlow else if (isSelected) NeonCyan else Color.Transparent,
                        shape = RoundedCornerShape(14.dp)
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
                        tint = if (isFocused) Color.White else if (isSelected) NeonCyan else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = if (isFocused) Color.White else if (isSelected) NeonCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * En-tête supérieur : Logo NOOS TV, statut VIP, bouton Recherche et bouton Identifiants IPTV
 */
@Composable
fun TvHeader(
    selectedTab: TvNavTab,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("NOOS", color = NeonCyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
            Text("TV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            if (isPremium) {
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

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Bouton Recherche
            Button(
                onClick = onOpenSearch,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Recherche", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Bouton Identifiants IPTV
            Button(
                onClick = onOpenLogin,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Dns, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Identifiants IPTV", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            ResolutionBadge(resolution = "4K HDR READY")
        }
    }
}

/**
 * Barre de défilement horizontal des catégories
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(categories) { category ->
            val isSelected = category.equals(selectedCategory, ignoreCase = true)
            var isFocused by remember { mutableStateOf(false) }

            var chipMod = Modifier
                .clip(RoundedCornerShape(8.dp))

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
                            isFocused -> NeonCyan
                            isSelected -> DeepCyan.copy(alpha = 0.4f)
                            else -> SurfaceDark
                        }
                    )
                    .border(
                        width = if (isFocused) 2.5.dp else if (isSelected) 1.dp else 0.dp,
                        color = if (isFocused) Color.White else if (isSelected) NeonCyan else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = category,
                    color = if (isFocused) Color.Black else if (isSelected) NeonCyan else TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Carte de chaîne TV avec chargement d'image Coil et barre de progression miniature
 */
@Composable
fun TvChannelGridCard(
    channel: Channel,
    onNavigateLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "card_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
                } else false
            }
            .background(if (isFocused) Color(0xFF1B2B42) else CardBackground)
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
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
                LiveIndicatorBadge()
                if (channel.isHdr) HdrBadge()
            }

            // Zone logo avec Coil & barre de chargement de miniature
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDarkVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(channel.logoUrl)
                                .crossfade(true)
                                .allowHardware(false)
                                .build(),
                            contentDescription = channel.name,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .height(3.dp),
                                        color = NeonCyan,
                                        trackColor = SurfaceDark
                                    )
                                }
                            },
                            error = {
                                Icon(imageVector = Icons.Default.Tv, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(24.dp))
                            },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(4.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = if (isFocused) NeonCyan else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${channel.categoryName} • ${channel.resolution}",
                        color = if (isFocused) Color.White.copy(alpha = 0.8f) else TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Carte de film VOD avec chargement d'affiche Coil et barre de progression miniature
 */
@Composable
fun TvMovieGridCard(
    movie: VodMovie,
    onNavigateLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "vod_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
                } else false
            }
            .background(if (isFocused) Color(0xFF1B2B42) else CardBackground)
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Zone affiche avec barre de chargement miniature
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .background(SurfaceDarkVariant),
                contentAlignment = Alignment.Center
            ) {
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
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .width(70.dp)
                                            .height(3.dp),
                                        color = NeonCyan,
                                        trackColor = SurfaceDark
                                    )
                                    Text("Chargement...", color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                        },
                        error = {
                            Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(imageVector = Icons.Default.Movie, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                }

                if (movie.isHdr) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                        HdrBadge(text = movie.hdrFormat ?: "HDR")
                    }
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
                    text = "${movie.releaseYear ?: ""} ★ ${movie.rating}",
                    color = if (isFocused) Color.White.copy(alpha = 0.8f) else TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Carte de série VOD avec chargement d'affiche Coil et barre de progression miniature
 */
@Composable
fun TvSeriesGridCard(
    series: Series,
    onNavigateLeft: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "series_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
                } else false
            }
            .background(if (isFocused) Color(0xFF261D38) else CardBackground)
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .background(SurfaceDarkVariant),
                contentAlignment = Alignment.Center
            ) {
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
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .width(70.dp)
                                            .height(3.dp),
                                        color = NeonCyan,
                                        trackColor = SurfaceDark
                                    )
                                    Text("Chargement...", color = TextSecondary, fontSize = 9.sp)
                                }
                            }
                        },
                        error = {
                            Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(36.dp))
                }
            }

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
                    text = "${series.seasons.size.takeIf { it > 0 }?.let { "$it Saisons • " } ?: ""}★ ${series.rating}",
                    color = if (isFocused) Color.White.copy(alpha = 0.8f) else TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Contenu interactif Guide TV (EPG)
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Heure actuelle : ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(currentTime)}",
                color = NeonCyan,
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(channels) { channel ->
                    var isRowFocused by remember { mutableStateOf(false) }

                    // Récupérer les programmes réels ou générer une grille temporelle cohérente
                    val realProgs = epgPrograms.filter { it.channelId == channel.epgChannelId || it.channelId == channel.id }
                    val programs = if (realProgs.isNotEmpty()) {
                        realProgs
                    } else {
                        // Programme par défaut calé sur l'heure
                        val hourMs = 3600_000L
                        val startHour = (currentTime / hourMs) * hourMs
                        listOf(
                            EpgProgram(
                                id = "${channel.id}_current",
                                channelId = channel.id,
                                title = "Direct : ${channel.name}",
                                description = "Émission en cours de diffusion",
                                startEpochMs = startHour,
                                stopEpochMs = startHour + hourMs,
                                category = channel.categoryName,
                                hasCatchup = channel.hasCatchup
                            ),
                            EpgProgram(
                                id = "${channel.id}_next",
                                channelId = channel.id,
                                title = "Programme Suivant",
                                description = "Suite des programmes sur ${channel.name}",
                                startEpochMs = startHour + hourMs,
                                stopEpochMs = startHour + (hourMs * 2),
                                category = channel.categoryName,
                                hasCatchup = channel.hasCatchup
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        var boxMod = Modifier
                            .width(170.dp)
                            .height(82.dp)
                            .clip(RoundedCornerShape(10.dp))

                        if (channel == channels.firstOrNull()) {
                            boxMod = boxMod.focusRequester(focusRequester)
                        }

                        Box(
                            modifier = boxMod
                                .onFocusChanged { isRowFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectChannel(channel) }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.Back)) {
                                        onNavigateLeft()
                                        true
                                    } else false
                                }
                                .background(if (isRowFocused) SurfaceDarkVariant else SurfaceDark)
                                .border(
                                    width = if (isRowFocused) 3.dp else 1.dp,
                                    color = if (isRowFocused) FocusGlow else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = channel.name,
                                    color = if (isRowFocused) NeonCyan else TextPrimary,
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
                                        .height(82.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .onFocusChanged { isProgFocused = it.isFocused }
                                        .focusable()
                                        .clickable { onSelectChannel(channel) }
                                        .background(if (isLive) Color(0xFF132238) else SurfaceDark)
                                        .border(
                                            width = if (isProgFocused) 3.dp else if (isLive) 1.dp else 0.dp,
                                            color = if (isProgFocused) FocusGlow else if (isLive) NeonCyan.copy(alpha = 0.4f) else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
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
                                                color = if (isLive) NeonCyan else TextSecondary,
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
                                            color = if (isProgFocused) NeonCyan else TextPrimary,
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
                                                color = NeonCyan,
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
 * Indicateur de chargement pour le basculement de catégories
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
                modifier = Modifier.size(42.dp),
                color = NeonCyan,
                strokeWidth = 3.5.dp
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
 * État vide quand une catégorie ne contient aucun contenu
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
