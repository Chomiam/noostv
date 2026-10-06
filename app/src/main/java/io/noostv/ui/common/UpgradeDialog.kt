package io.noostv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
    var devCodeInput by remember { mutableStateOf("") }
    var codeError by remember { mutableStateOf<String?>(null) }
    var codeSuccess by remember { mutableStateOf(false) }

    val validDevCodes = remember {
        setOf("NOOS-DEV-VIP", "DEV2026", "NOOS4K", "VIP", "DEV")
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark)
                .border(1.5.dp, GoldVip.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        text = "NoosTV Premium VIP",
                        color = GoldVip,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Débloquez les flux 4K UHD, HDR10, Dolby Vision & Multi-écrans",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                // Feature List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDarkVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureRow(title = "Flux 4K UHD, HDR10, HLG & Dolby Vision")
                    FeatureRow(title = "Décodage matériel AV1 & HEVC optimisé")
                    FeatureRow(title = "Jusqu'à 4 écrans simultanés (Salon + Mobiles)")
                    FeatureRow(title = "Guide TV 7 jours avec Replay / Catch-up")
                    FeatureRow(title = "Passthrough Audio Multicanal 5.1 / Dolby Atmos")
                }

                // Section Code Développeur
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Code Développeur / Licence VIP :",
                                color = NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = devCodeInput,
                                onValueChange = {
                                    devCodeInput = it
                                    codeError = null
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ex: NOOS-DEV-VIP", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = SurfaceDark,
                                    unfocusedContainerColor = SurfaceDark,
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = SurfaceDarkVariant
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Button(
                                onClick = {
                                    val cleanCode = devCodeInput.trim().uppercase()
                                    if (cleanCode in validDevCodes) {
                                        codeSuccess = true
                                        onActivatePremium()
                                    } else {
                                        codeError = "Code invalide. Utilisez : NOOS-DEV-VIP"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Activer", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        if (codeError != null) {
                            Text(text = codeError ?: "", color = RedLive, fontSize = 11.sp)
                        }

                        Text(
                            text = "Code développeur officiel : NOOS-DEV-VIP",
                            color = GoldVip,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("Fermer")
                    }

                    // Bouton d'activation rapide en 1 clic
                    Button(
                        onClick = onActivatePremium,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldVip,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "Débloquer VIP Direct",
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
