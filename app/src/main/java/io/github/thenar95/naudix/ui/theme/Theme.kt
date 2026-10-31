package io.github.thenar95.naudix.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

@Composable
fun NaudixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You: color dinámico disponible en Android 12+.
    dynamicColor: Boolean = true,
    semilla: Color = PaletaColor.DORADO.semilla,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, dynamicColor, semilla, amoled) {
        val base = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> esquemaOscuro(semilla, amoled)
            else -> esquemaClaro(semilla)
        }
        if (amoled && darkTheme) base.conAmoled() else base
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
