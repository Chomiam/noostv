package io.noostv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonOrange,
    tertiary = GoldVip,
    background = DarkOledBackground,
    surface = SurfaceDark,
    onPrimary = DarkOledBackground,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

/**
 * Arrière-plan global NoosTV style macOS Sequoia sombre / visionOS :
 * Dégradé d'obsidienne profonde, lueur ambiante saphir & violette (frosted blur effect)
 * et reflet brillant spéculaire supérieur (glossy window highlight).
 */
@Composable
fun MacOsDarkGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassMeshBackground)
    ) {
        // macOS Ambient Sapphire Pool (Haut Gauche)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x243888FF),
                            Color(0x0C1B3B6F),
                            Color.Transparent
                        ),
                        center = Offset(0f, 0f),
                        radius = 1100f
                    )
                )
        )
        // macOS Ambient Indigo/Violet Aura (Bas Droite)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x1C8B5CF6),
                            Color(0x084F2B8D),
                            Color.Transparent
                        ),
                        center = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        radius = 1300f
                    )
                )
        )
        // macOS Specular Top Gloss Sheen (Reflet glossy supérieur façon fenêtre Mac)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.045f),
                        0.35f to Color.White.copy(alpha = 0.015f),
                        1.0f to Color.Transparent
                    )
                )
        )

        content()
    }
}

@Composable
fun NoosTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
