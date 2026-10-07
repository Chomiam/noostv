package io.noostv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.storage.SessionManager
import io.noostv.data.model.Category
import io.noostv.ui.theme.*

enum class FilterCategoryType {
    MOVIES,
    SERIES
}

/**
 * Onglet Filtres : Permet de choisir précisément les catégories de Films et de Séries
 * à afficher ou masquer dans la barre supérieure pour le profil actif.
 */
@Composable
fun TvCategoryFiltersContent(
    vodCategories: List<Category>,
    seriesCategories: List<Category>,
    sessionManager: SessionManager,
    focusRequester: FocusRequester? = null,
    onNavigateLeftToSidebar: (() -> Unit)? = null,
    onFiltersUpdated: () -> Unit
) {
    var selectedType by remember { mutableStateOf(FilterCategoryType.MOVIES) }

    var hiddenVodIds by remember { mutableStateOf(sessionManager.getHiddenVodCategoryIds()) }
    var hiddenSeriesIds by remember { mutableStateOf(sessionManager.getHiddenSeriesCategoryIds()) }

    val currentCategories = if (selectedType == FilterCategoryType.MOVIES) vodCategories else seriesCategories
    val currentHiddenIds = if (selectedType == FilterCategoryType.MOVIES) hiddenVodIds else hiddenSeriesIds

    val visibleCount = currentCategories.count { !currentHiddenIds.contains(it.id) }

    val defaultMoviesRequester = remember { FocusRequester() }
    val moviesTabRequester = focusRequester ?: defaultMoviesRequester
    val seriesTabRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // En-tête avec titre, profil et onglets de sélection
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
                        text = "Filtres des Catégories",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Profil actif : ${sessionManager.getActiveProfile().name}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Boutons d'action globale
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (selectedType == FilterCategoryType.MOVIES) {
                            sessionManager.showAllVodCategories()
                            hiddenVodIds = emptySet()
                        } else {
                            sessionManager.showAllSeriesCategories()
                            hiddenSeriesIds = emptySet()
                        }
                        onFiltersUpdated()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceElevated),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tout", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (selectedType == FilterCategoryType.MOVIES) {
                            currentCategories.forEach { sessionManager.setVodCategoryVisibility(it.id, false) }
                            hiddenVodIds = sessionManager.getHiddenVodCategoryIds()
                        } else {
                            currentCategories.forEach { sessionManager.setSeriesCategoryVisibility(it.id, false) }
                            hiddenSeriesIds = sessionManager.getHiddenSeriesCategoryIds()
                        }
                        onFiltersUpdated()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassSurfaceElevated),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aucun", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Sélecteur de type : Films VOD vs Séries VOD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterTypeTabButton(
                title = "Films VOD (${vodCategories.size})",
                icon = Icons.Default.Movie,
                isSelected = selectedType == FilterCategoryType.MOVIES,
                focusRequester = moviesTabRequester,
                onNavigateLeft = onNavigateLeftToSidebar,
                onClick = { selectedType = FilterCategoryType.MOVIES }
            )

            FilterTypeTabButton(
                title = "Séries VOD (${seriesCategories.size})",
                icon = Icons.Default.VideoLibrary,
                isSelected = selectedType == FilterCategoryType.SERIES,
                focusRequester = seriesTabRequester,
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
                    text = "$visibleCount / ${currentCategories.size} catégories visibles",
                    color = NoosCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Grille à 3 colonnes des catégories avec bascule visible/masqué
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
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentCategories, key = { it.id }) { cat ->
                    val isVisible = !currentHiddenIds.contains(cat.id)
                    var isFocused by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "filter_cat_scale")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    isFocused -> Color(0xFF283652)
                                    isVisible -> GlassSurfaceElevated
                                    else -> GlassSurface
                                }
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) NoosCyan else if (isVisible) GlassBorderHighlight else GlassBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (selectedType == FilterCategoryType.MOVIES) {
                                    sessionManager.toggleVodCategoryVisibility(cat.id)
                                    hiddenVodIds = sessionManager.getHiddenVodCategoryIds()
                                } else {
                                    sessionManager.toggleSeriesCategoryVisibility(cat.id)
                                    hiddenSeriesIds = sessionManager.getHiddenSeriesCategoryIds()
                                }
                                onFiltersUpdated()
                            }
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat.name,
                                color = if (isFocused) Color.White else if (isVisible) TextPrimary else TextTertiary,
                                fontSize = 12.sp,
                                fontWeight = if (isVisible) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Badge statut visible / masqué
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isVisible) NoosBlue.copy(alpha = 0.25f) else Color(0x33FF3B30)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = if (isVisible) NoosCyan else RedLive,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (isVisible) "Visible" else "Masqué",
                                        color = if (isVisible) NoosCyan else RedLive,
                                        fontSize = 10.sp,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onNavigateLeft: (() -> Unit)? = null,
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
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) NoosCyan else if (isSelected) NoosCyan.copy(alpha = 0.6f) else GlassBorder,
                shape = RoundedCornerShape(50)
            )
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionLeft && onNavigateLeft != null) {
                    onNavigateLeft()
                    true
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
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
