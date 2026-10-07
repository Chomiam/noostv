package io.noostv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Palette Moderne Glassy / Frosted Glass (Apple visionOS & Google Modern TV)
val DarkOledBackground = Color(0xFF0B0E17) // Moins austère, teinté bleu nuit profond
val GlassBackgroundTop = Color(0xFF141927)
val GlassBackgroundBottom = Color(0xFF0A0D15)
val GlassMeshBackground = Brush.verticalGradient(listOf(GlassBackgroundTop, GlassBackgroundBottom))

// Surfaces Glassy translucides
val SurfaceDark = Color(0xFF121724)
val SurfaceDarkVariant = Color(0xFF1B2234)
val CardBackground = Color(0xFF151B2A)
val CardBorderUnfocused = Color(0x26FFFFFF)

val GlassSurface = Color(0x401D273D)
val GlassSurfaceElevated = Color(0x6624314C)
val GlassSurfaceHigh = Color(0x802E3D5E)
val GlassCard = Color(0x4D1B2438)
val GlassPill = Color(0x38283754)
val GlassBorder = Color(0x2EFFFFFF)
val GlassBorderHighlight = Color(0x59FFFFFF)

// Dégradés Glassy
val GlassCardGradient = Brush.verticalGradient(
    listOf(Color(0x59283756), Color(0x2E161F33))
)
val GlassBorderGradient = Brush.verticalGradient(
    listOf(Color(0x59FFFFFF), Color(0x1AFFFFFF))
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
val NeonCyan = Color(0xFF5AC8FA)
val DeepCyan = Color(0xFF3888FF)
val NeonOrange = Color(0xFFFF9500)

// Aliases de compatibilité
val DarkSurface = SurfaceDark
val DarkCard = CardBackground
val AccentAmber = GoldVip
val AccentRed = RedLive
