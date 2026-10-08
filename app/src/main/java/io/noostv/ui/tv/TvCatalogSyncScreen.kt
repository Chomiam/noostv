package io.noostv.ui.tv

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.R
import io.noostv.data.repository.SyncProgress
import io.noostv.ui.theme.*

/**
 * Écran de chargement et d'indexation intégral en plein écran pour NoosTV.
 * Style épuré Apple / visionOS avec dégradé sombre profond, lueurs diffuses,
 * barre de progression fluide avec balayage spéculaire (shimmer) et sécurité AES-256.
 */
@Composable
fun TvCatalogSyncScreen(
    syncProgress: SyncProgress?,
    onRetry: () -> Unit,
    onLogout: (() -> Unit)? = null
) {
    val progressValue = syncProgress?.progress ?: 0.05f
    val animatedProgress by animateFloatAsState(
        targetValue = progressValue.coerceIn(0.03f, 1.0f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "sync_progress_anim"
    )

    val stepMessage = syncProgress?.step ?: "Initialisation de la synchronisation..."
    val errorMessage = syncProgress?.error
    val percentText = "${(animatedProgress * 100).toInt()}%"

    // Animations d'ambiance Apple : pulsation douce de lueur et brillance spéculaire
    val infiniteTransition = rememberInfiniteTransition(label = "sync_ambient_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    MacOsDarkGlassBackground(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ==================== 1. LOGO OFFICIEL AVEC HALO LUMINEUX ====================
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                // Halo de lueur diffuse bleu/cyan pulsante en arrière-plan
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NoosCyan.copy(alpha = pulseAlpha * 0.7f),
                                    NoosBlue.copy(alpha = pulseAlpha * 0.35f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                Image(
                    painter = painterResource(id = R.drawable.noos_logo),
                    contentDescription = "NOOS",
                    modifier = Modifier.size(76.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==================== 2. TITRE & BADGE GLASSY ====================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "NOOS",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NoosGradient)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "TV",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (errorMessage != null) "Erreur de chargement" else "Indexation du catalogue IPTV",
                color = if (errorMessage != null) Color(0xFFFF453A) else TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stepMessage,
                color = if (errorMessage != null) Color(0xFFFF8078) else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // ==================== 3. BARRE DE PROGRESSION STYLISÉE APPLE ====================
            if (errorMessage == null) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .fillMaxWidth(0.62f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val barShape = RoundedCornerShape(50)

                    // Track extérieur frosted glass avec ombre douce et contour spéculaire
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = barShape,
                                ambientColor = Color(0x66000000),
                                spotColor = Color(0x99000000)
                            )
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color(0x660C1424),
                                        Color(0x88050912)
                                    )
                                ),
                                shape = barShape
                            )
                            .border(
                                width = 1.2.dp,
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.32f),
                                        Color.White.copy(alpha = 0.08f)
                                    )
                                ),
                                shape = barShape
                            )
                    ) {
                        // Remplissage progressif fluide
                        val fillRatio = animatedProgress.coerceIn(0.02f, 1.0f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fillRatio)
                                .clip(barShape)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF5AC8FA), // Cyan Apple
                                            Color(0xFF3888FF), // Bleu NOOS
                                            Color(0xFF8B5CF6), // Violet lumineux
                                            Color(0xFFA855F7)  // Magenta néon
                                        )
                                    )
                                )
                        ) {
                            // Reflet glossy supérieur façon vitre Apple (specular highlight)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.48f)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            // Balayage spéculaire dynamique (Shimmer)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.40f),
                                                Color.Transparent
                                            ),
                                            startX = (shimmerOffset * 580f) - 120f,
                                            endX = (shimmerOffset * 580f) + 120f
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ligne de détails & Pourcentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(NoosCyan.copy(alpha = pulseAlpha), CircleShape)
                            )
                            Text(
                                text = "Indexation haute performance AES-256",
                                color = TextTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x3300C6FF))
                                .border(0.8.dp, Color(0x6600C6FF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = percentText,
                                color = NoosCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // ==================== 4. ÉTAT D'ERREUR FROSTED GLASS ====================
                val retryRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(120)
                    runCatching { retryRequester.requestFocus() }
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth(0.62f)
                        .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x66000000))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(Color(0x4D331015), Color(0x6618080C))
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(Color(0x80FF453A), Color(0x26FF453A))
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFF8078),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NoosBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            modifier = Modifier.focusRequester(retryRequester)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Réessayer", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        if (onLogout != null) {
                            OutlinedButton(
                                onClick = onLogout,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Changer de compte", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ==================== 5. BADGE DE CONFIANCE INFÉRIEUR ====================
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x18FFFFFF))
                    .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚡ Navigation fluide & métadonnées hors-ligne ultra-rapides",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
