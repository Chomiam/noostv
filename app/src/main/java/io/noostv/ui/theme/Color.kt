package io.noostv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette Moderne Glassy / Frosted Glass (Apple visionOS & macOS Dark Sequoia)
val DarkOledBackground = Color(0xFF04060A) // Noir obsidienne profond
val GlassBackgroundTop = Color(0xFF090E1B) // Teinte nuit bleue profonde macOS
val GlassBackgroundMid = Color(0xFF050812) // Obsidienne velours
val GlassBackgroundBottom = Color(0xFF020306) // Noir néant abyssal
val GlassMeshBackground = Brush.verticalGradient(
    listOf(
        Color(0xFF090E1B),
        Color(0xFF050812),
        Color(0xFF020306)
    )
)

// Surfaces Glassy translucides sombres & contrastées
val SurfaceDark = Color(0xFF0A0E18)
val SurfaceDarkVariant = Color(0xFF121826)
val CardBackground = Color(0xFF0D121F)
val CardBorderUnfocused = Color(0x1FFFFFFF)

val GlassSurface = Color(0x400C1220)
val GlassSurfaceElevated = Color(0x66101728)
val GlassSurfaceHigh = Color(0x88141D32)
val GlassCard = Color(0x550E1526)
val GlassPill = Color(0x40162035)
val GlassBorder = Color(0x26FFFFFF)
val GlassBorderHighlight = Color(0x50FFFFFF)

// Dégradés Glassy
val GlassCardGradient = Brush.verticalGradient(
    listOf(Color(0x66182236), Color(0x330C1220))
)
val GlassBorderGradient = Brush.verticalGradient(
    listOf(Color(0x50FFFFFF), Color(0x14FFFFFF))
)
val GlassFocusedGradient = Brush.verticalGradient(
    listOf(Color(0xFF5AC8FA), Color(0xFF3888FF))
)

// Couleurs de marque NOOS
val NoosBlue = Color(0xFF3888FF)
val NoosCyan = Color(0xFF5AC8FA)
val NoosPurple = Color(0xFF8B5CF6)
val NoosGradient = Brush.horizontalGradient(listOf(Color(0xFF3888FF), Color(0xFF8B5CF6)))

// Typographie
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)

// Accents & Statuts
val GoldVip = Color(0xFFF5C518)
val PurpleHdr = Color(0xFF8B5CF6)
val FocusGlow = Color(0xFF3888FF)
val RedLive = Color(0xFFFF3B30)
val GreenLive = Color(0xFF34C759)
val NeonCyan = Color(0xFF5AC8FA)
val DeepCyan = Color(0xFF3888FF)
val NeonOrange = Color(0xFFFF9500)

// Aliases de compatibilité
val DarkSurface = SurfaceDark
val DarkCard = CardBackground
val AccentAmber = GoldVip
val AccentRed = RedLive
