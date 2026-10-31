package io.github.thenar95.naudix.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.thenar95.naudix.R

/**
 * Paletas propias que se usan cuando Material You está desactivado.
 * Cada paleta parte de un color semilla y de él se derivan todos los tonos.
 * [PERSONALIZADO] usa el color elegido por el usuario en los ajustes.
 */
enum class PaletaColor(val semilla: Color, @get:StringRes val etiqueta: Int) {
    DORADO(Color(0xFFE0A81C), R.string.palette_gold),
    TURQUESA(Color(0xFF00897B), R.string.palette_teal),
    AZUL(Color(0xFF1565C0), R.string.palette_blue),
    VERDE(Color(0xFF2E7D32), R.string.palette_green),
    ROSA(Color(0xFFC2185B), R.string.palette_pink),
    VIOLETA(Color(0xFF6A3EBF), R.string.palette_purple),
    NARANJA(Color(0xFFE65100), R.string.palette_orange),
    PERSONALIZADO(Color(0xFFE0A81C), R.string.palette_custom)
}
