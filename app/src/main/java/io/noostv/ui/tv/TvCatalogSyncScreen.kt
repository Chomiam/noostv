package io.noostv.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
 * Écran de synchronisation et d'indexation locale chiffrée pour NoosTV.
 * Affiche une barre de progression Apple-style fluide pendant le téléchargement
 * et la mise en cache locale des catégories et chaînes.
 */
@Composable
fun TvCatalogSyncScreen(
    syncProgress: SyncProgress?,
    onRetry: () -> Unit
) {
    val progressValue = syncProgress?.progress ?: 0.05f
    val animatedProgress by animateFloatAsState(
        targetValue = progressValue.coerceIn(0.02f, 1.0f),
        label = "sync_progress_anim"
    )

    val stepMessage = syncProgress?.step ?: "Initialisation de la synchronisation..."
    val errorMessage = syncProgress?.error
    val percentText = "${(animatedProgress * 100).toInt()}%"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GlassMeshBackground),
        contentAlignment = Alignment.Center
    ) {
        val cardShape = RoundedCornerShape(28.dp)

        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(0.65f)
                .shadow(
                    elevation = 24.dp,
                    shape = cardShape,
                    clip = false,
                    ambientColor = Color(0x66000000),
                    spotColor = Color(0x99000000)
                )
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xE6141D30),
                        0.5f to Color(0xCC0E1524),
                        1.0f to Color(0xF2090D18)
                    ),
                    shape = cardShape
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.35f),
                        0.3f to Color.White.copy(alpha = 0.12f),
                        0.7f to Color.White.copy(alpha = 0.04f),
                        1.0f to Color.White.copy(alpha = 0.15f)
                    ),
                    shape = cardShape
                )
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo Officiel NOOS & Badge TV en dégradé
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.noos_logo),
                    contentDescription = "NOOS",
                    modifier = Modifier.height(48.dp),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NoosGradient)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "TV",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Indexation du catalogue IPTV",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stepMessage,
                color = if (errorMessage != null) Color(0xFFFF453A) else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage == null) {
                // ==================== BARRE DE PROGRESSION GLASSY ====================
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0x33FFFFFF))
                            .border(0.8.dp, Color(0x44FFFFFF), RoundedCornerShape(7.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF5AC8FA),
                                            Color(0xFF3888FF),
                                            Color(0xFF8B5CF6)
                                        )
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Synchronisation sécurisée AES-256",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = percentText,
                            color = NoosCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // ==================== ERREUR ET BOUTON RÉESSAYER ====================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF453A),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFF453A),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NoosBlue,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Réessayer", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Cette étape prépare les métadonnées hors-ligne pour une navigation ultra-fluide sans temps de chargement.",
                color = TextTertiary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}
