package io.noostv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.noostv.ui.theme.*

@Composable
fun UpgradeDialog(
    onDismiss: () -> Unit,
    onActivatePremium: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.dp, GoldVip.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "VIP",
                        tint = GoldVip,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Passez à NoosTV Premium VIP",
                        color = GoldVip,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Débloquez l'expérience ultime optimisée pour Mi Box & Google TV Streamer 4K",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                // Feature List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDarkVariant, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureRow(title = "Flux 4K UHD, HDR10 & Dolby Vision")
                    FeatureRow(title = "Décodage matériel AV1 & HEVC optimisé")
                    FeatureRow(title = "Jusqu'à 4 écrans simultanés (Salon + Mobiles)")
                    FeatureRow(title = "Guide TV 7 jours avec Replay / Catch-up")
                    FeatureRow(title = "Passthrough Audio Multicanal 5.1 / Dolby Atmos")
                }

                // Pairing / Activation Code Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Code d'appairage TV rapide :",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "NOOS-8492",
                            color = NeonCyan,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp
                        )
                        Text(
                            text = "Activez sur votre mobile : noostv.app/pair",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("Fermer")
                    }

                    Button(
                        onClick = onActivatePremium,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldVip,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "Activer l'Accès VIP",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 13.sp
        )
    }
}
