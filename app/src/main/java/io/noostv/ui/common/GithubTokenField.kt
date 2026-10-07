package io.noostv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.noostv.core.storage.SessionManager
import io.noostv.ui.theme.NoosCyan
import io.noostv.ui.theme.TextSecondary

/**
 * Champ de saisie du jeton GitHub, utilisé pour l'OTA des dépôts privés.
 *
 * Le jeton n'est JAMAIS embarqué dans l'APK : il est stocké chiffré (AES-256-GCM,
 * Android Keystore) via [SessionManager.githubToken] sur chaque appareil, et saisi
 * directement dans les Réglages. Aucun `buildConfigField` ni ressource ne doit le
 * contenir (règle de sécurité, cf. AGENT.md).
 */
@Composable
fun GithubTokenField(
    sessionManager: SessionManager,
    modifier: Modifier = Modifier
) {
    var token by remember { mutableStateOf(sessionManager.githubToken) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "JETON GITHUB (DÉPÔT PRIVÉ)",
            color = NoosCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Requis uniquement si le dépôt est privé. Stocké chiffré sur l'appareil, jamais embarqué dans l'APK.",
            color = TextSecondary,
            fontSize = 11.sp
        )
        OutlinedTextField(
            value = token,
            onValueChange = { newValue ->
                token = newValue
                sessionManager.githubToken = newValue
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("ghp_…", color = TextSecondary, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = NoosCyan,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f)
            )
        )
    }
}
