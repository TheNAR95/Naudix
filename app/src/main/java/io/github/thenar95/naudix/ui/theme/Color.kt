package io.github.thenar95.naudix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

// Los esquemas se generan a partir de un color semilla. Cada color se coloca en un
// "tono" de luminosidad perceptual (como hace Material 3): el tono 40 se usa para el
// color principal en tema claro y el 80 en tema oscuro, lo que garantiza buen contraste
// con cualquier semilla (azul, dorado, rosa...).

private val ErrorClaro = Color(0xFFBA1A1A)
private val SobreErrorClaro = Color(0xFFFFFFFF)
private val ContenedorErrorClaro = Color(0xFFFFDAD6)
private val SobreContenedorErrorClaro = Color(0xFF410002)

private val ErrorOscuro = Color(0xFFFFB4AB)
private val SobreErrorOscuro = Color(0xFF690005)
private val ContenedorErrorOscuro = Color(0xFF93000A)
private val SobreContenedorErrorOscuro = Color(0xFFFFDAD6)

internal fun esquemaClaro(semilla: Color): ColorScheme {
    val base = hslDe(semilla)
    val h = base.tono
    val s = base.saturacion.coerceIn(0.30f, 0.85f)

    return lightColorScheme(
        primary = tono(h, s, 40),
        onPrimary = tono(h, s * 0.10f, 100),
        primaryContainer = tono(h, s * 0.90f, 90),
        onPrimaryContainer = tono(h, s, 10),
        inversePrimary = tono(h, s, 80),

        secondary = tono(h, s * 0.30f, 40),
        onSecondary = tono(h, s * 0.10f, 100),
        secondaryContainer = tono(h, s * 0.35f, 90),
        onSecondaryContainer = tono(h, s * 0.30f, 10),

        tertiary = tono(h + 60f, s * 0.45f, 40),
        onTertiary = tono(h + 60f, s * 0.10f, 100),
        tertiaryContainer = tono(h + 60f, s * 0.50f, 90),
        onTertiaryContainer = tono(h + 60f, s * 0.45f, 10),

        error = ErrorClaro,
        onError = SobreErrorClaro,
        errorContainer = ContenedorErrorClaro,
        onErrorContainer = SobreContenedorErrorClaro,

        background = tono(h, s * 0.08f, 98),
        onBackground = tono(h, s * 0.08f, 10),
        surface = tono(h, s * 0.08f, 98),
        onSurface = tono(h, s * 0.08f, 10),
        surfaceVariant = tono(h, s * 0.18f, 90),
        onSurfaceVariant = tono(h, s * 0.10f, 30),
        outline = tono(h, s * 0.06f, 50),
        outlineVariant = tono(h, s * 0.12f, 80),

        surfaceDim = tono(h, s * 0.08f, 87),
        surfaceBright = tono(h, s * 0.08f, 98),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = tono(h, s * 0.05f, 96),
        surfaceContainer = tono(h, s * 0.05f, 94),
        surfaceContainerHigh = tono(h, s * 0.05f, 92),
        surfaceContainerHighest = tono(h, s * 0.05f, 90),
        inverseSurface = tono(h, s * 0.08f, 20),
        inverseOnSurface = tono(h, s * 0.05f, 95),
        scrim = Color.Black
    )
}

internal fun esquemaOscuro(semilla: Color, amoled: Boolean): ColorScheme {
    val base = hslDe(semilla)
    val h = base.tono
    val s = base.saturacion.coerceIn(0.30f, 0.85f)

    val fondo = if (amoled) Color.Black else tono(h, s * 0.08f, 6)

    return darkColorScheme(
        primary = tono(h, s * 0.80f, 80),
        onPrimary = tono(h, s * 0.85f, 20),
        primaryContainer = tono(h, s * 0.80f, 30),
        onPrimaryContainer = tono(h, s * 0.85f, 90),
        inversePrimary = tono(h, s, 40),

        secondary = tono(h, s * 0.30f, 80),
        onSecondary = tono(h, s * 0.30f, 20),
        secondaryContainer = tono(h, s * 0.30f, 30),
        onSecondaryContainer = tono(h, s * 0.30f, 90),

        tertiary = tono(h + 60f, s * 0.40f, 80),
        onTertiary = tono(h + 60f, s * 0.40f, 20),
        tertiaryContainer = tono(h + 60f, s * 0.40f, 30),
        onTertiaryContainer = tono(h + 60f, s * 0.40f, 90),

        error = ErrorOscuro,
        onError = SobreErrorOscuro,
        errorContainer = ContenedorErrorOscuro,
        onErrorContainer = SobreContenedorErrorOscuro,

        background = fondo,
        onBackground = tono(h, s * 0.05f, 90),
        surface = fondo,
        onSurface = tono(h, s * 0.05f, 90),
        surfaceVariant = tono(h, s * 0.12f, 30),
        onSurfaceVariant = tono(h, s * 0.08f, 80),
        outline = tono(h, s * 0.05f, 60),
        outlineVariant = tono(h, s * 0.10f, 30),

        surfaceDim = fondo,
        surfaceBright = tono(h, s * 0.08f, 24),
        surfaceContainerLowest = if (amoled) Color.Black else tono(h, s * 0.08f, 4),
        surfaceContainerLow = if (amoled) tono(h, s * 0.06f, 2) else tono(h, s * 0.08f, 10),
        surfaceContainer = if (amoled) tono(h, s * 0.06f, 3) else tono(h, s * 0.08f, 12),
        surfaceContainerHigh = if (amoled) tono(h, s * 0.06f, 4) else tono(h, s * 0.08f, 17),
        surfaceContainerHighest = if (amoled) tono(h, s * 0.06f, 6) else tono(h, s * 0.08f, 22),
        inverseSurface = tono(h, s * 0.05f, 90),
        inverseOnSurface = tono(h, s * 0.05f, 20),
        scrim = Color.Black
    )
}

/** Fuerza superficies negras puras cuando el usuario elige AMOLED con Material You. */
internal fun ColorScheme.conAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF101010),
    surfaceContainerHighest = Color(0xFF151515)
)

private data class Hsl(val tono: Float, val saturacion: Float, val luminosidad: Float)

/** Tono y saturación normalizados de un color, tal como los usan las paletas. */
internal fun tonoSaturacion(color: Color): Pair<Float, Float> {
    val base = hslDe(color)
    return base.tono to base.saturacion.coerceIn(0.30f, 0.85f)
}

/** Color resultante del color semilla en un tono perceptual concreto (40 claro, 80 oscuro). */
internal fun colorPrincipal(semilla: Color, nivel: Int = 40): Color {
    val base = hslDe(semilla)
    return tono(base.tono, base.saturacion.coerceIn(0.30f, 0.85f), nivel)
}

private fun hslDe(color: Color): Hsl {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return Hsl(0f, 0f, l)

    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> (g - b) / d + if (g < b) 6f else 0f
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } / 6f
    return Hsl(h * 360f, s, l)
}

/**
 * Devuelve el color con el tono y la saturación indicados, ajustando la luminosidad
 * hasta alcanzar el tono perceptual [nivel] (0 = negro, 100 = blanco).
 */
private fun tono(tono: Float, saturacion: Float, nivel: Int): Color {
    val objetivo = luminanciaDeNivel(nivel)
    var bajo = 0f
    var alto = 1f
    var resultado = Color.hsl(normalizarTono(tono), saturacion.coerceIn(0f, 1f), 0.5f)
    repeat(18) {
        val medio = (bajo + alto) / 2f
        resultado = Color.hsl(normalizarTono(tono), saturacion.coerceIn(0f, 1f), medio)
        if (luminancia(resultado) < objetivo) bajo = medio else alto = medio
    }
    return resultado
}

private fun normalizarTono(tono: Float): Float = (tono % 360f + 360f) % 360f

/** Luminancia relativa WCAG de un tono perceptual (0..100). */
private fun luminanciaDeNivel(nivel: Int): Float {
    val l = nivel.coerceIn(0, 100) / 100f
    return if (l <= 0.08f) l / 9.032962f
    else ((l + 0.16f) / 1.16f).pow(3f)
}

private fun luminancia(color: Color): Float =
    0.2126f * canal(color.red) + 0.7152f * canal(color.green) + 0.0722f * canal(color.blue)

private fun canal(valor: Float): Float {
    val c = valor.coerceIn(0f, 1f)
    return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
}
