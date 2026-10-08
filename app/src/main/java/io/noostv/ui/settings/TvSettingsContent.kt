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
import io.noostv.core.localization.AppLanguage
import io.noostv.core.localization.LocalAppLanguage
import io.noostv.core.localization.LocalStrings
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
    onOpenLogin: () -> Unit,
    onLogout: () -> Unit,
    onLanguageChanged: (AppLanguage) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateManager = remember { UpdateManager(context) }
    val strings = LocalStrings.current
    val currentLanguage = LocalAppLanguage.current

    var selectedChannel by remember { mutableStateOf(sessionManager.updateChannel) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val currentAppVersion = io.noostv.BuildConfig.VERSION_NAME

    // Focus requesters pour D-Pad télécommande
    val stableBtnFocus = remember { FocusRequester() }
    val testingBtnFocus = remember { FocusRequester() }
    val checkBtnFocus = remember { FocusRequester() }
    val logoutBtnFocus = remember { FocusRequester() }

    // Focus requesters pour les 7 langues
    val langFocusRequesters = remember { List(AppLanguage.entries.size) { FocusRequester() } }

    fun checkUpdates(channel: String) {
        updateState = UpdateState.Checking
        coroutineScope.launch {
            val state = updateManager.checkForUpdates(
                channel = channel,
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
                    text = strings.settingsTitle,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.settingsSubtitle,
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
                    text = "${strings.version} $currentAppVersion",
                    color = NoosCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ==================== SECTION 1 : CANAL GITHUB (STABLE VS TESTING) ====================
        Text(
            text = strings.githubChannelTitle,
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
                title = strings.stableBranchTitle,
                description = strings.stableBranchDesc,
                branchName = "stable",
                isSelected = selectedChannel == "stable",
                activeLabel = strings.activeBadge,
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
                title = strings.testingBranchTitle,
                description = strings.testingBranchDesc,
                branchName = "testing",
                isSelected = selectedChannel == "testing",
                activeLabel = strings.activeBadge,
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
            text = strings.checkUpdates.uppercase(),
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
                                text = "${strings.checkingUpdates} ($selectedChannel)",
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
                                        .background(GreenLive.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = GreenLive, modifier = Modifier.size(18.dp))
                                }
                                Column {
                                    Text(
                                        text = strings.noUpdateAvailable,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "NoosTV v$currentAppVersion • ${if (selectedChannel == "stable") strings.channelStableBadge else strings.channelTestingBadge}",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            SettingsActionButton(
                                text = strings.checkAgain,
                                icon = Icons.Default.Refresh,
                                focusRequester = checkBtnFocus,
                                onNavigateLeft = onNavigateLeft,
                                onNavigateUp = { focusRequester.requestFocus() },
                                onNavigateDown = {
                                    if (langFocusRequesters.isNotEmpty()) langFocusRequesters[0].requestFocus()
                                },
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
                                            .background(NoosBlue.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.SystemUpdate, contentDescription = null, tint = NoosBlue, modifier = Modifier.size(18.dp))
                                    }
                                    Column {
                                        Text(
                                            text = "${strings.updateAvailable} ${release.title.ifBlank { release.tag }}",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${if (release.isPrerelease) strings.channelTestingBadge else strings.channelStableBadge} • ${strings.publishedOn} ${release.publishedAt.take(10)} • ${strings.size} ${"%.1f".format(release.apkSizeMb)} Mo",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                SettingsActionButton(
                                    text = strings.downloadAndInstall,
                                    icon = Icons.Default.Download,
                                    isPrimary = true,
                                    focusRequester = checkBtnFocus,
                                    onNavigateLeft = onNavigateLeft,
                                    onNavigateUp = { focusRequester.requestFocus() },
                                    onNavigateDown = {
                                        if (langFocusRequesters.isNotEmpty()) langFocusRequesters[0].requestFocus()
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            val dlResult = updateManager.downloadApk(
                                                release = release,
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
                                                    updateState = UpdateState.Error("Erreur : ${err.localizedMessage}")
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
                                        .padding(12.dp)
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
                                Text(strings.downloadingUpdate, color = TextPrimary, fontSize = 13.sp)
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
                            Text(strings.installerReady, color = TextPrimary, fontSize = 13.sp)
                            SettingsActionButton(
                                text = strings.reopenInstaller,
                                icon = Icons.Default.InstallMobile,
                                isPrimary = true,
                                focusRequester = checkBtnFocus,
                                onNavigateLeft = onNavigateLeft,
                                onNavigateUp = { focusRequester.requestFocus() },
                                onNavigateDown = {
                                    if (langFocusRequesters.isNotEmpty()) langFocusRequesters[0].requestFocus()
                                },
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
                                text = strings.retry,
                                icon = Icons.Default.Refresh,
                                focusRequester = checkBtnFocus,
                                onNavigateLeft = onNavigateLeft,
                                onNavigateUp = { focusRequester.requestFocus() },
                                onNavigateDown = {
                                    if (langFocusRequesters.isNotEmpty()) langFocusRequesters[0].requestFocus()
                                },
                                onClick = { checkUpdates(selectedChannel) }
                            )
                        }
                    }

                    is UpdateState.Idle -> {
                        SettingsActionButton(
                            text = strings.checkUpdates,
                            icon = Icons.Default.Refresh,
                            focusRequester = checkBtnFocus,
                            onNavigateLeft = onNavigateLeft,
                            onNavigateUp = { focusRequester.requestFocus() },
                            onNavigateDown = {
                                if (langFocusRequesters.isNotEmpty()) langFocusRequesters[0].requestFocus()
                            },
                            onClick = { checkUpdates(selectedChannel) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==================== SECTION 3 : LANGUE DE L'INTERFACE ====================
        Text(
            text = strings.languageSectionTitle,
            color = NoosCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.languageSectionSubtitle,
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Rangée 1 : Français, English, Español, Deutsch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val row1 = listOf(AppLanguage.FRENCH, AppLanguage.ENGLISH, AppLanguage.SPANISH, AppLanguage.GERMAN)
            row1.forEachIndexed { index, lang ->
                LanguageSelectionCard(
                    language = lang,
                    isSelected = currentLanguage == lang,
                    focusRequester = langFocusRequesters[index],
                    onNavigateLeft = if (index == 0) onNavigateLeft else { { langFocusRequesters[index - 1].requestFocus() } },
                    onNavigateRight = if (index < row1.size - 1) { { langFocusRequesters[index + 1].requestFocus() } } else null,
                    onNavigateUp = { checkBtnFocus.requestFocus() },
                    onNavigateDown = {
                        val nextIdx = 4 + index.coerceAtMost(2)
                        langFocusRequesters[nextIdx].requestFocus()
                    },
                    onSelect = { onLanguageChanged(lang) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Rangée 2 : Italiano, العربية, Português
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val row2 = listOf(AppLanguage.ITALIAN, AppLanguage.ARABIC, AppLanguage.PORTUGUESE)
            row2.forEachIndexed { index, lang ->
                val actualIndex = 4 + index
                LanguageSelectionCard(
                    language = lang,
                    isSelected = currentLanguage == lang,
                    focusRequester = langFocusRequesters[actualIndex],
                    onNavigateLeft = if (index == 0) onNavigateLeft else { { langFocusRequesters[actualIndex - 1].requestFocus() } },
                    onNavigateRight = if (index < row2.size - 1) { { langFocusRequesters[actualIndex + 1].requestFocus() } } else null,
                    onNavigateUp = { langFocusRequesters[index].requestFocus() },
                    onNavigateDown = { logoutBtnFocus.requestFocus() },
                    onSelect = { onLanguageChanged(lang) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Espaceur pour garder l'alignement
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==================== SECTION 4 : COMPTE IPTV & INFOS TECHNIQUES ====================
        Text(
            text = strings.subscriptionSectionTitle,
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
                        Text(strings.iptvSession, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        PremiumVipBadge()
                    }
                    Text("${strings.server} ${sessionManager.getMaskedServerUrl()}", color = TextSecondary, fontSize = 11.sp)
                    Text("${strings.username} ${sessionManager.getMaskedUsername()}", color = TextSecondary, fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.focusRequester(logoutBtnFocus),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDarkVariant),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.logout, color = Color(0xFFFF5252), fontSize = 11.sp)
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
                    Text(strings.systemDiag, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${strings.device} ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})", color = TextSecondary, fontSize = 11.sp)
                    Text("ExoPlayer Media3 (4K HDR10, H.265, AV1)", color = TextSecondary, fontSize = 11.sp)
                    Text("Coil 2.6 (100 Mo cache / Mali Hardware-safe)", color = TextSecondary, fontSize = 11.sp)
                    Text("Git: $selectedChannel", color = NoosCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==================== SECTION 5 : EXPÉRIENCE & AUDIO ====================
        Text(
            text = "EXPÉRIENCE & AUDIO",
            color = NoosCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        var isSoundEnabled by remember { mutableStateOf(sessionManager.isSoundEffectsEnabled) }
        var isSoundCardFocused by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (isSoundCardFocused) SurfaceDarkVariant else SurfaceDark)
                .border(if (isSoundCardFocused) 2.dp else 1.dp, if (isSoundCardFocused) NoosCyan else CardBorderUnfocused, RoundedCornerShape(20.dp))
                .onFocusChanged { isSoundCardFocused = it.isFocused }
                .focusable()
                .clickable {
                    val newVal = !isSoundEnabled
                    isSoundEnabled = newVal
                    sessionManager.isSoundEffectsEnabled = newVal
                }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSoundEnabled) NoosBlue.copy(alpha = 0.2f) else Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = null,
                            tint = if (isSoundEnabled) NoosCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sons de navigation (style Apple TV)",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Retours sonores subtils et élégants lors des déplacements à la télécommande",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isSoundEnabled,
                    onCheckedChange = {
                        isSoundEnabled = it
                        sessionManager.isSoundEffectsEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NoosBlue,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SurfaceDarkVariant
                    )
                )
            }
        }
    }
}

/**
 * Carte de sélection de langue d'interface avec drapeau émoji et navigation D-Pad
 */
@Composable
fun LanguageSelectionCard(
    language: AppLanguage,
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
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "lang_card")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) { onNavigateLeft(); true } else false
                        }
                        Key.DirectionRight -> {
                            if (onNavigateRight != null) { onNavigateRight(); true } else false
                        }
                        Key.DirectionDown -> {
                            if (onNavigateDown != null) { onNavigateDown(); true } else false
                        }
                        Key.DirectionUp -> {
                            if (onNavigateUp != null) { onNavigateUp(); true } else false
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
                    isSelected -> NoosBlue.copy(alpha = 0.25f)
                    else -> SurfaceDark
                }
            )
            .border(
                width = if (isFocused) 3.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> FocusGlow
                    isSelected -> NoosCyan
                    else -> CardBorderUnfocused
                },
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Drapeau stylisé
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = language.flagEmoji,
                    fontSize = 18.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.nativeName,
                    color = if (isFocused || isSelected) Color.White else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = language.displayName,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = NoosCyan,
                    modifier = Modifier.size(16.dp)
                )
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
    activeLabel: String = "ACTIF",
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
                        Text(activeLabel, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
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
                Text(text = "Git : $branchName", color = NoosCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
    onNavigateDown: (() -> Unit)? = null,
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
                        Key.DirectionDown -> {
                            if (onNavigateDown != null) {
                                onNavigateDown()
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
