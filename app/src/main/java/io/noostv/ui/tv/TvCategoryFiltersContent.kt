package io.noostv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.localization.LocalStrings
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Category
import io.noostv.ui.theme.*

enum class FilterCategoryType {
    LIVE,
    MOVIES,
    SERIES
}

/**
 * Onglet Filtres : Permet de choisir précisément les catégories de Chaînes Direct, Films et Séries
 * à afficher ou masquer.
 * Présentation optimisée pour TV à 3 colonnes lisibles, avec curseur de focus bien visible et navigation D-Pad complète.
 */
@Composable
fun TvCategoryFiltersContent(
    liveCategories: List<Category> = emptyList(),
    vodCategories: List<Category> = emptyList(),
    seriesCategories: List<Category> = emptyList(),
    sessionManager: SessionManager,
    focusRequester: FocusRequester? = null,
    onNavigateLeftToSidebar: (() -> Unit)? = null,
    onFiltersUpdated: () -> Unit
) {
    val strings = LocalStrings.current
    var selectedType by remember {
        mutableStateOf(
            if (vodCategories.isNotEmpty()) FilterCategoryType.MOVIES
            else if (liveCategories.isNotEmpty()) FilterCategoryType.LIVE
            else FilterCategoryType.SERIES
        )
    }

    var hiddenLiveIds by remember { mutableStateOf(sessionManager.getHiddenLiveCategoryIds()) }
    var hiddenVodIds by remember { mutableStateOf(sessionManager.getHiddenVodCategoryIds()) }
    var hiddenSeriesIds by remember { mutableStateOf(sessionManager.getHiddenSeriesCategoryIds()) }

    val currentCategories = when (selectedType) {
        FilterCategoryType.LIVE -> liveCategories
        FilterCategoryType.MOVIES -> vodCategories
        FilterCategoryType.SERIES -> seriesCategories
    }
    val currentHiddenIds = when (selectedType) {
        FilterCategoryType.LIVE -> hiddenLiveIds
        FilterCategoryType.MOVIES -> hiddenVodIds
        FilterCategoryType.SERIES -> hiddenSeriesIds
    }

    val visibleCount = currentCategories.count { !currentHiddenIds.contains(it.id) }

    val defaultTabRequester = remember { FocusRequester() }
    val mainTabRequester = focusRequester ?: defaultTabRequester
    val liveTabRequester = remember { FocusRequester() }
    val moviesTabRequester = remember { FocusRequester() }
    val seriesTabRequester = remember { FocusRequester() }
    val firstCategoryFocusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // En-tête avec titre, profil et boutons d'action rapide
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
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = NoosCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = strings.filtersTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${strings.activeProfile} : ${sessionManager.getActiveProfile().name} • Sélectionnez les catégories à masquer (enregistré automatiquement)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Boutons d'action globale (Tout afficher / Tout masquer)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var isShowAllFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isShowAllFocused) Color(0xFF283652) else GlassSurfaceElevated)
                        .border(
                            width = if (isShowAllFocused) 2.dp else 1.dp,
                            color = if (isShowAllFocused) FocusGlow else GlassBorder,
                            shape = RoundedCornerShape(50)
                        )
                        .onFocusChanged { isShowAllFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        runCatching { firstCategoryFocusRequester.requestFocus() }
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter -> {
                                        when (selectedType) {
                                            FilterCategoryType.LIVE -> {
                                                sessionManager.showAllLiveCategories()
                                                hiddenLiveIds = emptySet()
                                            }
                                            FilterCategoryType.MOVIES -> {
                                                sessionManager.showAllVodCategories()
                                                hiddenVodIds = emptySet()
                                            }
                                            FilterCategoryType.SERIES -> {
                                                sessionManager.showAllSeriesCategories()
                                                hiddenSeriesIds = emptySet()
                                            }
                                        }
                                        onFiltersUpdated()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .focusable()
                        .clickable {
                            when (selectedType) {
                                FilterCategoryType.LIVE -> {
                                    sessionManager.showAllLiveCategories()
                                    hiddenLiveIds = emptySet()
                                }
                                FilterCategoryType.MOVIES -> {
                                    sessionManager.showAllVodCategories()
                                    hiddenVodIds = emptySet()
                                }
                                FilterCategoryType.SERIES -> {
                                    sessionManager.showAllSeriesCategories()
                                    hiddenSeriesIds = emptySet()
                                }
                            }
                            onFiltersUpdated()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(13.dp))
                        Text("Tout afficher", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                var isHideAllFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isHideAllFocused) Color(0xFF283652) else GlassSurfaceElevated)
                        .border(
                            width = if (isHideAllFocused) 2.dp else 1.dp,
                            color = if (isHideAllFocused) FocusGlow else GlassBorder,
                            shape = RoundedCornerShape(50)
                        )
                        .onFocusChanged { isHideAllFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        runCatching { firstCategoryFocusRequester.requestFocus() }
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter -> {
                                        when (selectedType) {
                                            FilterCategoryType.LIVE -> {
                                                currentCategories.forEach { sessionManager.setLiveCategoryVisibility(it.id, false) }
                                                hiddenLiveIds = sessionManager.getHiddenLiveCategoryIds()
                                            }
                                            FilterCategoryType.MOVIES -> {
                                                currentCategories.forEach { sessionManager.setVodCategoryVisibility(it.id, false) }
                                                hiddenVodIds = sessionManager.getHiddenVodCategoryIds()
                                            }
                                            FilterCategoryType.SERIES -> {
                                                currentCategories.forEach { sessionManager.setSeriesCategoryVisibility(it.id, false) }
                                                hiddenSeriesIds = sessionManager.getHiddenSeriesCategoryIds()
                                            }
                                        }
                                        onFiltersUpdated()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .focusable()
                        .clickable {
                            when (selectedType) {
                                FilterCategoryType.LIVE -> {
                                    currentCategories.forEach { sessionManager.setLiveCategoryVisibility(it.id, false) }
                                    hiddenLiveIds = sessionManager.getHiddenLiveCategoryIds()
                                }
                                FilterCategoryType.MOVIES -> {
                                    currentCategories.forEach { sessionManager.setVodCategoryVisibility(it.id, false) }
                                    hiddenVodIds = sessionManager.getHiddenVodCategoryIds()
                                }
                                FilterCategoryType.SERIES -> {
                                    currentCategories.forEach { sessionManager.setSeriesCategoryVisibility(it.id, false) }
                                    hiddenSeriesIds = sessionManager.getHiddenSeriesCategoryIds()
                                }
                            }
                            onFiltersUpdated()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = RedLive, modifier = Modifier.size(13.dp))
                        Text("Tout masquer", color = RedLive, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Sélecteur de type : Direct TV vs Films VOD vs Séries VOD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (liveCategories.isNotEmpty()) {
                FilterTypeTabButton(
                    title = "Chaînes TV (${liveCategories.size})",
                    icon = Icons.Default.Tv,
                    isSelected = selectedType == FilterCategoryType.LIVE,
                    focusRequester = liveTabRequester,
                    onNavigateLeft = onNavigateLeftToSidebar,
                    onNavigateDown = { runCatching { firstCategoryFocusRequester.requestFocus() } },
                    onClick = { selectedType = FilterCategoryType.LIVE }
                )
            }

            FilterTypeTabButton(
                title = "${strings.movieCategories} (${vodCategories.size})",
                icon = Icons.Default.Movie,
                isSelected = selectedType == FilterCategoryType.MOVIES,
                focusRequester = if (liveCategories.isEmpty()) mainTabRequester else moviesTabRequester,
                onNavigateLeft = if (liveCategories.isEmpty()) onNavigateLeftToSidebar else null,
                onNavigateDown = { runCatching { firstCategoryFocusRequester.requestFocus() } },
                onClick = { selectedType = FilterCategoryType.MOVIES }
            )

            FilterTypeTabButton(
                title = "${strings.seriesCategories} (${seriesCategories.size})",
                icon = Icons.Default.VideoLibrary,
                isSelected = selectedType == FilterCategoryType.SERIES,
                focusRequester = seriesTabRequester,
                onNavigateDown = { runCatching { firstCategoryFocusRequester.requestFocus() } },
                onClick = { selectedType = FilterCategoryType.SERIES }
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassPill)
                    .border(1.dp, GlassBorder, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "$visibleCount / ${currentCategories.size} visibles",
                    color = NoosCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Grille des catégories : 3 colonnes larges & spacieuses pour une lecture complète
        if (currentCategories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucune catégorie disponible. Connectez-vous à un fournisseur IPTV.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(currentCategories, key = { _, it -> it.id }) { index, cat ->
                    val isVisible = !currentHiddenIds.contains(cat.id)
                    var isFocused by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "filter_cat_scale")

                    val isFirstColumn = (index % 3 == 0)
                    val isTopRow = (index < 3)

                    val toggleVisibilityAction: () -> Unit = {
                        when (selectedType) {
                            FilterCategoryType.LIVE -> {
                                sessionManager.toggleLiveCategoryVisibility(cat.id)
                                hiddenLiveIds = sessionManager.getHiddenLiveCategoryIds()
                            }
                            FilterCategoryType.MOVIES -> {
                                sessionManager.toggleVodCategoryVisibility(cat.id)
                                hiddenVodIds = sessionManager.getHiddenVodCategoryIds()
                            }
                            FilterCategoryType.SERIES -> {
                                sessionManager.toggleSeriesCategoryVisibility(cat.id)
                                hiddenSeriesIds = sessionManager.getHiddenSeriesCategoryIds()
                            }
                        }
                        onFiltersUpdated()
                    }

                    var cardMod = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .clip(RoundedCornerShape(16.dp))

                    if (index == 0) {
                        cardMod = cardMod.focusRequester(firstCategoryFocusRequester)
                    }

                    Box(
                        modifier = cardMod
                            .background(
                                when {
                                    isFocused -> Color(0xFF1E3860)
                                    isVisible -> GlassSurfaceElevated
                                    else -> GlassSurface.copy(alpha = 0.5f)
                                }
                            )
                            .border(
                                width = if (isFocused) 3.dp else 1.dp,
                                color = if (isFocused) FocusGlow else if (isVisible) GlassBorderHighlight else GlassBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            if (isFirstColumn && onNavigateLeftToSidebar != null) {
                                                onNavigateLeftToSidebar()
                                                true
                                            } else false
                                        }
                                        Key.DirectionUp -> {
                                            if (isTopRow) {
                                                runCatching {
                                                    when (selectedType) {
                                                        FilterCategoryType.LIVE -> liveTabRequester.requestFocus()
                                                        FilterCategoryType.MOVIES -> moviesTabRequester.requestFocus()
                                                        FilterCategoryType.SERIES -> seriesTabRequester.requestFocus()
                                                    }
                                                }
                                                true
                                            } else false
                                        }
                                        Key.DirectionCenter, Key.Enter -> {
                                            toggleVisibilityAction()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .clickable { toggleVisibilityAction() }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Curseur visuel ou puce indicateur quand l'élément est focalisé
                            if (isFocused) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(FocusGlow)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Nom de la catégorie sur 2 lignes complètes sans troncature brutale
                            Text(
                                text = cat.name,
                                color = if (isFocused) Color.White else if (isVisible) TextPrimary else TextTertiary,
                                fontSize = 13.sp,
                                fontWeight = if (isFocused || isVisible) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            // Badge statut visible / masqué (avec icône explicite)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isVisible) NoosBlue.copy(alpha = 0.28f) else Color(0x33FF3B30)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isVisible) NoosCyan.copy(alpha = 0.5f) else RedLive.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isVisible) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (isVisible) NoosCyan else RedLive,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isVisible) "Visible" else "Masqué",
                                        color = if (isVisible) NoosCyan else RedLive,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun FilterTypeTabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
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
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else if (isSelected) NoosCyan.copy(alpha = 0.6f) else GlassBorder,
                shape = RoundedCornerShape(50)
            )
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (onNavigateDown != null) {
                                onNavigateDown()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected || isFocused) NoosCyan else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                color = if (isSelected || isFocused) Color.White else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
