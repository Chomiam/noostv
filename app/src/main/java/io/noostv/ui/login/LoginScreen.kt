package io.noostv.ui.login

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.R
import io.noostv.core.security.CryptoManager
import io.noostv.core.storage.SessionManager
import io.noostv.data.api.XtreamCodesClient
import io.noostv.data.model.SavedAccount
import io.noostv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    sessionManager: SessionManager,
    onLoginSuccess: (serverUrl: String, username: String, password: String) -> Unit,
    onDemoSelected: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val xtreamClient = remember { XtreamCodesClient() }

    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Historique local et chiffré des comptes connectés
    val savedAccounts = remember { mutableStateListOf<SavedAccount>() }

    LaunchedEffect(Unit) {
        savedAccounts.clear()
        savedAccounts.addAll(sessionManager.getSavedAccounts())
    }

    val hasHistory = savedAccounts.isNotEmpty()
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600 || configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Focus Requesters pour navigation D-Pad télécommande
    val serverFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val demoFocus = remember { FocusRequester() }
    val quickAccountsFocus = remember { FocusRequester() }

    // Auto-focus sur le premier champ ou sur la liste si disponible
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        try {
            serverFocus.requestFocus()
        } catch (_: Exception) {}
    }

    fun performLogin(targetServer: String, targetUser: String, targetPass: String) {
        val cleanUrl = targetServer.trim()
        val cleanUser = targetUser.trim()
        val cleanPass = targetPass.trim()

        if (cleanUrl.isBlank() || cleanUser.isBlank() || cleanPass.isBlank()) {
            errorMessage = "Veuillez renseigner tous les champs de connexion"
            return
        }

        isLoading = true
        errorMessage = null

        coroutineScope.launch {
            val authResult = xtreamClient.authenticate(cleanUrl, cleanUser, cleanPass)
            isLoading = false
            authResult.fold(
                onSuccess = { _ ->
                    onLoginSuccess(cleanUrl, cleanUser, cleanPass)
                },
                onFailure = { err ->
                    errorMessage = "Échec de connexion : ${err.localizedMessage ?: "Identifiants ou URL invalides"}"
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkOledBackground),
        contentAlignment = Alignment.Center
    ) {
        if (!hasHistory) {
            // ==================== DISPOSITION 1 : AUCUN HISTORIQUE (CENTRÉ STANDARD) ====================
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StandardLoginForm(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it; errorMessage = null },
                    username = username,
                    onUsernameChange = { username = it; errorMessage = null },
                    password = password,
                    onPasswordChange = { password = it; errorMessage = null },
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    serverFocus = serverFocus,
                    userFocus = userFocus,
                    passFocus = passFocus,
                    submitFocus = submitFocus,
                    demoFocus = demoFocus,
                    onNavigateRight = null,
                    onSubmit = { performLogin(serverUrl, username, password) },
                    onDemo = onDemoSelected
                )
            }
        } else if (isWideScreen) {
            // ==================== DISPOSITION 2 : ÉCRAN LARGE / TV (SPLIT GAUCHE / DROITE) ====================
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Partie gauche (52%) : Formulaire standard de connexion
                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    StandardLoginForm(
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it; errorMessage = null },
                        username = username,
                        onUsernameChange = { username = it; errorMessage = null },
                        password = password,
                        onPasswordChange = { password = it; errorMessage = null },
                        passwordVisible = passwordVisible,
                        onTogglePassword = { passwordVisible = !passwordVisible },
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        serverFocus = serverFocus,
                        userFocus = userFocus,
                        passFocus = passFocus,
                        submitFocus = submitFocus,
                        demoFocus = demoFocus,
                        onNavigateRight = { quickAccountsFocus.requestFocus() },
                        onSubmit = { performLogin(serverUrl, username, password) },
                        onDemo = onDemoSelected
                    )
                }

                // Partie droite (48%) : Liste des identifiants enregistrés chiffrés pour connexion rapide
                QuickConnectPanel(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                    savedAccounts = savedAccounts,
                    quickAccountsFocus = quickAccountsFocus,
                    onNavigateLeft = { submitFocus.requestFocus() },
                    onSelectAccount = { acc ->
                        serverUrl = acc.serverUrl
                        username = acc.username
                        password = acc.password
                        performLogin(acc.serverUrl, acc.username, acc.password)
                    },
                    onDeleteAccount = { acc ->
                        sessionManager.removeAccountFromHistory(acc.id)
                        savedAccounts.remove(acc)
                        Toast.makeText(context, "Identifiant supprimé de l'historique", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        } else {
            // ==================== DISPOSITION 3 : SMARTPHONE PORTRAIT ====================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // En-tête Connexions Rapides
                QuickConnectPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    savedAccounts = savedAccounts,
                    quickAccountsFocus = quickAccountsFocus,
                    onNavigateLeft = null,
                    onSelectAccount = { acc ->
                        serverUrl = acc.serverUrl
                        username = acc.username
                        password = acc.password
                        performLogin(acc.serverUrl, acc.username, acc.password)
                    },
                    onDeleteAccount = { acc ->
                        sessionManager.removeAccountFromHistory(acc.id)
                        savedAccounts.remove(acc)
                        Toast.makeText(context, "Identifiant supprimé de l'historique", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(color = Color(0x33FFFFFF), thickness = 1.dp)

                Text(
                    text = "OU NOUVELLE CONNEXION",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Formulaire standard en dessous
                StandardLoginForm(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it; errorMessage = null },
                    username = username,
                    onUsernameChange = { username = it; errorMessage = null },
                    password = password,
                    onPasswordChange = { password = it; errorMessage = null },
                    passwordVisible = passwordVisible,
                    onTogglePassword = { passwordVisible = !passwordVisible },
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    serverFocus = serverFocus,
                    userFocus = userFocus,
                    passFocus = passFocus,
                    submitFocus = submitFocus,
                    demoFocus = demoFocus,
                    onNavigateRight = null,
                    onSubmit = { performLogin(serverUrl, username, password) },
                    onDemo = onDemoSelected
                )
            }
        }
    }
}

/**
 * Formulaire standard de connexion IPTV Xtream Codes
 */
@Composable
private fun StandardLoginForm(
    modifier: Modifier = Modifier,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    serverFocus: FocusRequester,
    userFocus: FocusRequester,
    passFocus: FocusRequester,
    submitFocus: FocusRequester,
    demoFocus: FocusRequester,
    onNavigateRight: (() -> Unit)?,
    onSubmit: () -> Unit,
    onDemo: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo Officiel NOOS & Badge TV en dégradé
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.noos_logo),
                contentDescription = "NOOS",
                modifier = Modifier.height(44.dp),
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NoosGradient)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "TV",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        Text(
            text = "Connexion IPTV — Xtream Codes API",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
        )

        // Message d'erreur
        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RedLive.copy(alpha = 0.15f))
                    .border(1.dp, RedLive.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = RedLive, modifier = Modifier.size(18.dp))
                    Text(text = errorMessage, color = Color.White, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Champ 1 : URL du serveur
        TvInputField(
            label = "URL du Serveur IPTV",
            value = serverUrl,
            onValueChange = onServerUrlChange,
            placeholder = "http://serveur-iptv.com:8080",
            icon = Icons.Default.Dns,
            focusRequester = serverFocus,
            onNavigateRight = onNavigateRight,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() })
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Champ 2 : Identifiant
        TvInputField(
            label = "Identifiant (Username)",
            value = username,
            onValueChange = onUsernameChange,
            placeholder = "Votre identifiant...",
            icon = Icons.Default.Person,
            focusRequester = userFocus,
            onNavigateRight = onNavigateRight,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() })
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Champ 3 : Mot de passe
        TvInputField(
            label = "Mot de passe",
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "••••••••",
            icon = Icons.Default.Lock,
            focusRequester = passFocus,
            onNavigateRight = onNavigateRight,
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = onTogglePassword,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submitFocus.requestFocus() })
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Bouton 1 : Se connecter
        TvActionButton(
            text = if (isLoading) "Vérification et connexion..." else "Se connecter à mon IPTV",
            icon = Icons.Default.Login,
            focusRequester = submitFocus,
            onNavigateRight = onNavigateRight,
            isPrimary = true,
            enabled = !isLoading,
            onClick = onSubmit
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bouton 2 : Mode Démo
        TvActionButton(
            text = "Accéder au Mode Démo Rapide",
            icon = Icons.Default.PlayArrow,
            focusRequester = demoFocus,
            onNavigateRight = onNavigateRight,
            isPrimary = false,
            enabled = !isLoading,
            onClick = onDemo
        )
    }
}

/**
 * Panneau droit : liste des comptes enregistrés localement de manière chiffrée
 */
@Composable
private fun QuickConnectPanel(
    modifier: Modifier = Modifier,
    savedAccounts: List<SavedAccount>,
    quickAccountsFocus: FocusRequester,
    onNavigateLeft: (() -> Unit)?,
    onSelectAccount: (SavedAccount) -> Unit,
    onDeleteAccount: (SavedAccount) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorderUnfocused, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // En-tête avec icône éclair & badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NoosBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = NoosCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "CONNEXION RAPIDE",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Identifiants enregistrés sur cet appareil",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x2200E5FF))
                    .border(1.dp, NoosCyan.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 9.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${savedAccounts.size}",
                    color = NoosCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

        // Liste défilante des comptes sauvegardés
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(savedAccounts, key = { it.id }) { account ->
                val isFirst = savedAccounts.firstOrNull()?.id == account.id
                SavedAccountCard(
                    account = account,
                    focusRequester = if (isFirst) quickAccountsFocus else null,
                    onNavigateLeft = onNavigateLeft,
                    onSelect = { onSelectAccount(account) },
                    onDelete = { onDeleteAccount(account) }
                )
            }
        }

        // Mention de confidentialité et sécurité
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Chiffrement matériel AES-256-GCM • 100% Stockage Local",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Carte individuelle d'un compte précédemment utilisé
 */
@Composable
private fun SavedAccountCard(
    account: SavedAccount,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, label = "acc_scale")

    var mod = Modifier
        .fillMaxWidth()
        .scale(scale)
        .clip(RoundedCornerShape(14.dp))

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    Box(
        modifier = mod
            .background(if (isFocused) Color(0xFF1E2838) else SurfaceDarkVariant)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(14.dp)
            )
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
                        Key.Enter, Key.DirectionCenter, Key.Spacebar -> {
                            onSelect()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Informations utilisateur & serveur
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Avatar avec initiales
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NoosBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.username.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = account.username,
                            color = if (isFocused) Color.White else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x3300E5FF))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "RAPIDE",
                                color = NoosCyan,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = CryptoManager.maskUrl(account.serverUrl),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Actions : Connexion & Suppression
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Bouton Connexion directe
                IconButton(
                    onClick = onSelect,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = "Se connecter avec ce compte",
                        tint = NoosCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Bouton Supprimer de l'historique local
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Supprimer de l'historique",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TvInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester,
    onNavigateRight: (() -> Unit)? = null,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (isFocused) NoosCyan else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight && onNavigateRight != null) {
                        onNavigateRight()
                        true
                    } else false
                }
                .focusable()
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) FocusGlow else CardBorderUnfocused,
                    shape = RoundedCornerShape(16.dp)
                ),
            placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.5f)) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) NoosCyan else TextSecondary
                )
            },
            trailingIcon = if (isPassword && onTogglePassword != null) {
                {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Afficher mot de passe",
                            tint = if (isFocused) NoosCyan else TextSecondary
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions
        )
    }
}

@Composable
fun TvActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    focusRequester: FocusRequester,
    onNavigateRight: (() -> Unit)? = null,
    isPrimary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "btn_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    !enabled -> SurfaceDarkVariant.copy(alpha = 0.5f)
                    isPrimary && isFocused -> Color.White
                    isPrimary -> NoosBlue
                    isFocused -> SurfaceDarkVariant
                    else -> SurfaceDark
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGlow else CardBorderUnfocused,
                shape = RoundedCornerShape(50)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight && onNavigateRight != null) {
                    onNavigateRight()
                    true
                } else false
            }
            .focusable()
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPrimary && isFocused) Color.Black else if (isPrimary) Color.White else NoosCyan,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = if (isPrimary && isFocused) Color.Black else if (isPrimary) Color.White else TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
