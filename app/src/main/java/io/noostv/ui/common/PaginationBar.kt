package io.noostv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import io.noostv.core.audio.LocalSoundEffectManager
import io.noostv.ui.theme.DarkCard
import io.noostv.ui.theme.NoosCyan
import io.noostv.ui.theme.TextPrimary
import io.noostv.ui.theme.TextSecondary

/**
 * Barre de pagination moderne et ergonomique pour TV (D-Pad) et Mobile (Touch).
 * Permet d'explorer l'intégralité du catalogue sans surcharger la mémoire.
 */
@Composable
fun NoosPaginationBar(
    currentPage: Int,
    totalPages: Int,
    totalItems: Int,
    itemLabel: String,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
    prevFocusRequester: FocusRequester? = null,
    nextFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateUpFromPrev: (() -> Unit)? = onNavigateUp,
    onNavigateUpFromNext: (() -> Unit)? = onNavigateUp,
    onNavigateLeftToSidebar: (() -> Unit)? = null
) {
    if (totalPages <= 1) return

    val internalPrevFocusRequester = prevFocusRequester ?: remember { FocusRequester() }
    val internalNextFocusRequester = nextFocusRequester ?: remember { FocusRequester() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton Précédent
        PaginationButton(
            text = "Précédent",
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Précédent",
                    modifier = Modifier.size(16.dp),
                    tint = if (currentPage > 1) NoosCyan else TextSecondary.copy(alpha = 0.4f)
                )
            },
            enabled = currentPage > 1,
            onClick = {
                val newPage = currentPage - 1
                onPageChange(newPage)
                if (newPage <= 1) {
                    runCatching { internalNextFocusRequester.requestFocus() }
                }
            },
            isTv = isTv,
            focusRequester = internalPrevFocusRequester,
            rightFocusRequester = if (currentPage < totalPages) internalNextFocusRequester else null,
            onNavigateRight = if (currentPage < totalPages) {
                {
                    runCatching { internalNextFocusRequester.requestFocus() }
                    Unit
                }
            } else null,
            onNavigateLeft = onNavigateLeftToSidebar,
            onNavigateUp = onNavigateUpFromPrev
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Indicateur d'état central
        Box(
            modifier = Modifier
                .background(DarkCard, RoundedCornerShape(12.dp))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Page $currentPage / $totalPages",
                    color = TextPrimary,
                    fontSize = if (isTv) 14.sp else 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (totalItems > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "($totalItems $itemLabel)",
                        color = TextSecondary,
                        fontSize = if (isTv) 12.sp else 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Bouton Suivant
        PaginationButton(
            text = "Suivant",
            iconRight = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Suivant",
                    modifier = Modifier.size(16.dp),
                    tint = if (currentPage < totalPages) NoosCyan else TextSecondary.copy(alpha = 0.4f)
                )
            },
            enabled = currentPage < totalPages,
            onClick = {
                val newPage = currentPage + 1
                onPageChange(newPage)
                if (newPage >= totalPages) {
                    runCatching { internalPrevFocusRequester.requestFocus() }
                }
            },
            isTv = isTv,
            focusRequester = internalNextFocusRequester,
            leftFocusRequester = if (currentPage > 1) internalPrevFocusRequester else null,
            onNavigateLeft = if (currentPage > 1) {
                {
                    runCatching { internalPrevFocusRequester.requestFocus() }
                    Unit
                }
            } else onNavigateLeftToSidebar,
            onNavigateUp = onNavigateUpFromNext
        )
    }
}

@Composable
private fun PaginationButton(
    text: String,
    icon: (@Composable () -> Unit)? = null,
    iconRight: (@Composable () -> Unit)? = null,
    enabled: Boolean,
    onClick: () -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null
) {
    val soundManager = LocalSoundEffectManager.current
    var isFocused by remember { mutableStateOf(false) }

    val bg = when {
        isFocused -> Color(0x3300E5FF)
        enabled -> DarkCard
        else -> DarkCard.copy(alpha = 0.3f)
    }
    val borderColor = when {
        isFocused -> NoosCyan
        enabled -> Color(0x22FFFFFF)
        else -> Color.Transparent
    }
    val textColor = when {
        isFocused -> NoosCyan
        enabled -> TextPrimary
        else -> TextSecondary.copy(alpha = 0.4f)
    }

    var buttonModifier = Modifier
        .clip(RoundedCornerShape(10.dp))
        .background(bg)
        .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
        .clickable(enabled = enabled) {
            soundManager?.playSelect()
            onClick()
        }

    if (focusRequester != null) {
        buttonModifier = buttonModifier.focusRequester(focusRequester)
    }

    if (isTv && enabled) {
        buttonModifier = buttonModifier
            .focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
            }
            .focusable()
            .onFocusChanged {
                if (it.isFocused && !isFocused) {
                    soundManager?.playFocus()
                }
                isFocused = it.isFocused
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        }
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (onNavigateRight != null) {
                                onNavigateRight()
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
    }

    Row(
        modifier = buttonModifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon?.invoke()
        Text(
            text = text,
            color = textColor,
            fontSize = if (isTv) 13.sp else 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium
        )
        iconRight?.invoke()
    }
}
