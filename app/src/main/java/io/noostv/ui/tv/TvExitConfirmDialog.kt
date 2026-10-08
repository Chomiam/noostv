@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.noostv.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.localization.LocalStrings
import io.noostv.ui.theme.*

/**
 * Boîte de dialogue de confirmation de sortie complète de l'application sur TV.
 * S'affiche après 2 appuis consécutifs sur Retour au menu principal.
 * Le bouton "Annuler" reçoit le focus par défaut pour éviter toute fermeture accidentelle.
 * Implémentée en Box overlay plein écran (identique aux modales VOD de NoosTV) pour
 * conserver la hiérarchie de focus Compose TV et une compatibilité matérielle optimale.
 */
@Composable
fun TvExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val strings = LocalStrings.current
    val cancelFocusRequester = remember { FocusRequester() }

    BackHandler {
        onDismiss()
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60)
        runCatching { cancelFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC050811))
            .clickable(onClick = onDismiss)
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
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark,
            border = BorderStroke(1.dp, GlassBorder),
            shadowElevation = 24.dp,
            modifier = Modifier
                .width(460.dp)
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(RedLive.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = RedLive,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = strings.exitConfirmTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = strings.exitConfirmMsg,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(26.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton Annuler — Focus par défaut
                    var isCancelFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .focusRequester(cancelFocusRequester)
                            .onFocusChanged { isCancelFocused = it.isFocused }
                            .border(
                                width = if (isCancelFocused) 2.dp else 1.dp,
                                color = if (isCancelFocused) NoosCyan else GlassBorder,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCancelFocused) NoosCyan.copy(alpha = 0.25f) else SurfaceDarkVariant,
                            contentColor = if (isCancelFocused) Color.White else TextPrimary
                        )
                    ) {
                        Text(
                            text = strings.cancel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    // Bouton Quitter
                    var isQuitFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onConfirmExit,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .onFocusChanged { isQuitFocused = it.isFocused }
                            .border(
                                width = if (isQuitFocused) 2.dp else 1.dp,
                                color = if (isQuitFocused) RedLive else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isQuitFocused) RedLive else RedLive.copy(alpha = 0.8f),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = strings.exitQuitButton,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
