package io.noostv.ui.login

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.data.api.XtreamCodesClient
import io.noostv.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (serverUrl: String, username: String, password: String) -> Unit,
    onDemoSelected: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val xtreamClient = remember { XtreamCodesClient() }

    var serverUrl by remember { mutableStateOf("https://t.mowlabs.ovh") }
    var username by remember { mutableStateOf("chomiam") }
    var password by remember { mutableStateOf("choco") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Focus Requesters pour navigation D-Pad télécommande
    val serverFocus = remember { FocusRequester() }
    val userFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }
    val demoFocus = remember { FocusRequester() }

    // Auto-focus sur le bouton de connexion pour validation immédiate à la télécommande
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        try {
            submitFocus.requestFocus()
        } catch (e: Exception) {
            // Ignorer si pas encore attaché
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkOledBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo & Titre
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "NOOS",
                    color = NeonCyan,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "TV",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Connexion IPTV — Xtream Codes API",
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Message d'erreur
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RedLive.copy(alpha = 0.2f))
                        .border(1.dp, RedLive, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = RedLive)
                        Text(text = errorMessage ?: "", color = Color.White, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Champ 1 : URL du serveur
            TvInputField(
                label = "URL du Serveur IPTV",
                value = serverUrl,
                onValueChange = { serverUrl = it; errorMessage = null },
                placeholder = "http://serveur-iptv.com:8080",
                icon = Icons.Default.Dns,
                focusRequester = serverFocus,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { userFocus.requestFocus() })
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Champ 2 : Identifiant (Username)
            TvInputField(
                label = "Identifiant (Username)",
                value = username,
                onValueChange = { username = it; errorMessage = null },
                placeholder = "Votre identifiant...",
                icon = Icons.Default.Person,
                focusRequester = userFocus,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passFocus.requestFocus() })
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Champ 3 : Mot de passe
            TvInputField(
                label = "Mot de passe",
                value = password,
                onValueChange = { password = it; errorMessage = null },
                placeholder = "••••••••",
                icon = Icons.Default.Lock,
                focusRequester = passFocus,
                isPassword = true,
                passwordVisible = passwordVisible,
                onTogglePassword = { passwordVisible = !passwordVisible },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitFocus.requestFocus() })
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Bouton 1 : Se connecter
            TvActionButton(
                text = if (isLoading) "Vérification et connexion..." else "Se connecter à mon IPTV",
                icon = Icons.Default.Login,
                focusRequester = submitFocus,
                isPrimary = true,
                enabled = !isLoading,
                onClick = {
                    if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                        errorMessage = "Veuillez renseigner le serveur, l'identifiant et le mot de passe."
                        return@TvActionButton
                    }

                    isLoading = true
                    errorMessage = null

                    coroutineScope.launch {
                        val result = xtreamClient.authenticate(serverUrl, username, password)
                        isLoading = false
                        result.onSuccess { accountInfo ->
                            onLoginSuccess(serverUrl, username, password)
                        }.onFailure { ex ->
                            errorMessage = "Échec de connexion : ${ex.message ?: "Serveur injoignable"}"
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bouton 2 : Mode Démo (sans identifiant)
            TvActionButton(
                text = "Explorer en Mode Démo (4K HDR Gratuit)",
                icon = Icons.Default.PlayArrow,
                focusRequester = demoFocus,
                isPrimary = false,
                onClick = onDemoSelected
            )
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
            color = if (isFocused) NeonCyan else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) FocusGlow else SurfaceDarkVariant,
                    shape = RoundedCornerShape(12.dp)
                ),
            placeholder = { Text(placeholder, color = TextSecondary.copy(alpha = 0.6f)) },
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) NeonCyan else TextSecondary
                )
            },
            trailingIcon = if (isPassword && onTogglePassword != null) {
                {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Afficher mot de passe",
                            tint = if (isFocused) NeonCyan else TextSecondary
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
            shape = RoundedCornerShape(12.dp),
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
    isPrimary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1.0f, label = "btn_scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> SurfaceDarkVariant.copy(alpha = 0.5f)
                    isPrimary && isFocused -> NeonCyan
                    isPrimary -> DeepCyan
                    isFocused -> SurfaceDarkVariant
                    else -> SurfaceDark
                }
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else if (isPrimary) Color.Transparent else SurfaceDarkVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
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
                tint = if (isPrimary && isFocused) Color.Black else if (isPrimary) Color.Black else NeonCyan,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = if (isPrimary && isFocused) Color.Black else if (isPrimary) Color.Black else Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
