package io.noostv.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.storage.SessionManager
import io.noostv.core.update.ReleaseInfo
import io.noostv.core.update.UpdateManager
import io.noostv.core.update.UpdateState
import io.noostv.ui.common.PremiumVipBadge
import io.noostv.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun TvSettingsContent(
    sessionManager: SessionManager,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    onOpenLogin: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }

    var selectedChannel by remember { mutableStateOf(sessionManager.updateChannel) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val currentAppVersion = io.noostv.BuildConfig.VERSION_NAME

    // Focus requesters pour D-Pad télécommande
    val stableBtnFocus = remember { FocusRequester() }
    val testingBtnFocus = remember { FocusRequester() }
    val checkBtnFocus = remember { FocusRequester() }

    fun checkUpdates(channel: String) {
        updateState = UpdateState.Checking
        coroutineScope.launch {
            val state = updateManager.checkForUpdates(
                channel = channel,
                token = sessionManager.githubToken,
                currentVersion = currentAppVersion
            )
            updateState = state
        }
    }

    LaunchedEffect(selectedChannel) {
        checkUpdates(selectedChannel)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 36.dp)
    ) {
        // En-tête Paramètres
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Paramètres & Mises à Jour",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Gestion des canaux de version GitHub et diagnostic système",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x223888FF))
                    .border(1.dp, Color(0x443888FF), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Version $currentAppVersion",
                    color = NoosCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ==================== SECTION 1 : CANAL GITHUB (STABLE VS TESTING) ====================
        Text(
            text = "CANAL DE DISTRIBUTION GITHUB",
            color = NoosCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Option 1 : Branche Stable
            ChannelSelectionCard(
                title = "Branche Stable (Recommandé)",
                description = "Versions officielles éprouvées, stabilité maximale pour un usage quotidien.",
                branchName = "stable",
                isSelected = selectedChannel == "stable",
                focusRequester = focusRequester,
                onNavigateLeft = onNavigateLeft,
                onNavigateRight = { testingBtnFocus.requestFocus() },
                onNavigateDown = { checkBtnFocus.requestFocus() },
                onSelect = {
                    selectedChannel = "stable"
                    sessionManager.updateChannel = "stable"
                },
                modifier = Modifier.weight(1f)
            )

            // Option 2 : Branche Testing
            ChannelSelectionCard(
                title = "Branche Testing (Expérimental)",
                description = "Mises à jour préliminaires et nouvelles fonctionnalités en avant-première.",
                branchName = "testing",
                isSelected = selectedChannel == "testing",
                focusRequester = testingBtnFocus,
                onNavigateLeft = { focusRequester.requestFocus() },
                onNavigateRight = null,
                onNavigateDown = { checkBtnFocus.requestFocus() },
                onSelect = {
                    selectedChannel = "testing"
                    sessionManager.updateChannel = "testing"
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==================== SECTION 2 : ÉTAT DES MISES À JOUR ====================
        Text(
            text = "ÉTAT DU LOGICIEL",
            color = NoosCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorderUnfocused, RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (val state = updateState) {
                    is UpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = NoosBlue,
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Recherche des versions sur GitHub ($selectedChannel)...",
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }

                    is UpdateState.UpToDate -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x3334C759)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text(
                                        text = "Votre application est à jour !",
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Version $currentAppVersion installée sur le canal $selectedChannel",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            // Bouton Vérifier
                            SettingsActionButton(
                                text = "Vérifier à nouveau",
                                icon = Icons.Default.Refresh,
                                focusRequester = checkBtnFocus,
                                onNavigateLeft = onNavigateLeft,
                                onNavigateUp = { focusRequester.requestFocus() },
                                onClick = { checkUpdates(selectedChannel) }
                            )
                        }
                    }

                    is UpdateState.UpdateAvailable -> {
                        val release = state.release
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x33FF9500)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text(
                                            text = "Mise à jour disponible : ${release.title}",
                                            color = NoosCyan,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Canal ${state.channel} • Publiée le ${release.publishedAt.take(10)} • Taille : ${String.format(java.util.Locale.US, "%.1f", release.apkSizeMb)} Mo",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                SettingsActionButton(
                                    text = "Télécharger & Installer",
                                    icon = Icons.Default.Download,
                                    isPrimary = true,
                                    focusRequester = checkBtnFocus,
                                    onNavigateLeft = onNavigateLeft,
                                    onNavigateUp = { focusRequester.requestFocus() },
                                    onClick = {
                                        updateState = UpdateState.Downloading(0, 0, release.apkSizeBytes)
                                        coroutineScope.launch {
                                            val dlResult = updateManager.downloadApk(
                                                release = release,
                                                token = sessionManager.githubToken,
                                                onProgress = { pct, dl, tot ->
                                                    updateState = UpdateState.Downloading(pct, dl, tot)
                                                }
                                            )
                                            dlResult.fold(
                                                onSuccess = { file ->
                                                    updateState = UpdateState.ReadyToInstall(file)
                                                    updateManager.installApk(file)
                                                },
                                                onFailure = { err ->
                                                    updateState = UpdateState.Error("Erreur téléchargement : ${err.localizedMessage}")
                                                }
                                            )
                                        }
                                    }
                                )
                            }

                            if (release.notes.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceDarkVariant)
                                ) {
                                    Text(
                                        text = release.notes,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    is UpdateState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Téléchargement de la mise à jour...", color = TextPrimary, fontSize = 13.sp)
                                Text("${state.progressPercent} %", color = NoosCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = NoosBlue,
                                trackColor = SurfaceDarkVariant
                            )
                        }
                    }

                    is UpdateState.ReadyToInstall -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("APK prêt ! L'installateur Android a été ouvert.", color = TextPrimary, fontSize = 13.sp)
                            SettingsActionButton(
                                text = "Réouvrir l'installateur",
                                icon = Icons.Default.InstallMobile,
                                isPrimary = true,
                                focusRequester = checkBtnFocus,
                                onNavigateLeft = onNavigateLeft,
                                onNavigateUp = { focusRequester.requestFocus() },
                                onClick = { updateManager.installApk(state.apkFile) }
                            )
                        }
                    }

                    is UpdateState.Error -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = RedLive)
                                Text(text = state.message, color = RedLive, fontSize = 12.sp)
                            }
                            SettingsActionButton(
                                text = "Réessayer",
                                icon = Icons.Default.Refresh,
                                focusRequester = checkBtnFocus,
                                onNavigateLeft = onNavigateLeft,
                                onNavigateUp = { focusRequester.requestFocus() },
                                onClick = { checkUpdates(selectedChannel) }
                            )
                        }
                    }

                    is UpdateState.Idle -> {
                        SettingsActionButton(
                            text = "Vérifier les mises à jour",
                            icon = Icons.Default.Refresh,
                            focusRequester = checkBtnFocus,
                            onNavigateLeft = onNavigateLeft,
                            onNavigateUp = { focusRequester.requestFocus() },
                            onClick = { checkUpdates(selectedChannel) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==================== SECTION 3 : COMPTE IPTV & INFOS TECHNIQUES ====================
        Text(
            text = "ABONNEMENT IPTV & MATÉRIEL",
            color = NoosCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Carte Compte
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorderUnfocused, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Session IPTV", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        PremiumVipBadge()
                    }
                    Text("Serveur : ${sessionManager.serverUrl.takeIf { it.isNotBlank() } ?: "Non connecté"}", color = TextSecondary, fontSize = 11.sp)
                    Text("Identifiant : ${sessionManager.username.takeIf { it.isNotBlank() } ?: "-"}", color = TextSecondary, fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onOpenLogin,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Changer d'identifiants", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }

            // Carte Matériel TV
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceDark)
                    .border(1.dp, CardBorderUnfocused, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Informations Système", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Appareil : ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})", color = TextSecondary, fontSize = 11.sp)
                    Text("Décodeur vidéo : ExoPlayer Media3 (4K HDR10, H.265, AV1)", color = TextSecondary, fontSize = 11.sp)
                    Text("Moteur d'images : Coil 2.6 (100 Mo disque / Hardware-safe Mali)", color = TextSecondary, fontSize = 11.sp)
                    Text("Branche Git active : $selectedChannel", color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Carte de sélection de canal (Stable vs Testing) avec navigation D-Pad
 */
@Composable
fun ChannelSelectionCard(
    title: String,
    description: String,
    branchName: String,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, label = "channel_card")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
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
                        Key.DirectionDown -> {
                            if (onNavigateDown != null) {
                                onNavigateDown()
                                true
                            } else false
                        }
                        Key.DirectionUp -> {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        }
                        Key.Enter, Key.DirectionCenter, Key.Spacebar -> {
                            onSelect()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .background(
                when {
                    isFocused -> Color(0xFF1E2838)
                    isSelected -> NoosBlue.copy(alpha = 0.15f)
                    else -> SurfaceDark
                }
            )
            .border(
                width = if (isFocused) 3.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> FocusGlow
                    isSelected -> NoosBlue
                    else -> CardBorderUnfocused
                },
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isFocused || isSelected) Color.White else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(NoosBlue)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("ACTIF", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(imageVector = Icons.Default.AltRoute, contentDescription = null, tint = NoosCyan, modifier = Modifier.size(14.dp))
                Text(text = "Cible Git : $branchName", color = NoosCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Bouton d'action élégant en forme de pilule pour les paramètres
 */
@Composable
fun SettingsActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "btn_scale")

    Button(
        onClick = onClick,
        modifier = Modifier
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        Key.DirectionUp -> {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) NoosBlue else SurfaceDarkVariant,
            contentColor = if (isPrimary) Color.White else TextPrimary
        ),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) FocusGlow else CardBorderUnfocused
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (isPrimary) Color.White else NoosCyan, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
