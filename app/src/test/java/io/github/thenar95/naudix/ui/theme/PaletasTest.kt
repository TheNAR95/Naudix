package io.github.thenar95.naudix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las paletas se generan a partir de un color semilla; estas pruebas garantizan que
 * cualquier semilla produzca textos legibles y que el modo AMOLED use negro puro.
 */
class PaletasTest {

    private fun contraste(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test
    fun `las paletas claras mantienen contraste de texto`() {
        PaletaColor.entries.forEach { paleta ->
            val esquema = esquemaClaro(paleta.semilla)
            assertTrue(
                "${paleta.name}: texto sobre principal",
                contraste(esquema.primary, esquema.onPrimary) >= 4.5f
            )
            assertTrue(
                "${paleta.name}: texto sobre contenedor",
                contraste(esquema.primaryContainer, esquema.onPrimaryContainer) >= 4.5f
            )
            assertTrue(
                "${paleta.name}: texto sobre fondo",
                contraste(esquema.background, esquema.onBackground) >= 4.5f
            )
            assertTrue(
                "${paleta.name}: variante de superficie",
                contraste(esquema.surfaceVariant, esquema.onSurfaceVariant) >= 3.5f
            )
        }
    }

    @Test
    fun `las paletas oscuras mantienen contraste de texto`() {
        PaletaColor.entries.forEach { paleta ->
            val esquema = esquemaOscuro(paleta.semilla, amoled = false)
            assertTrue(
                "${paleta.name}: texto sobre principal",
                contraste(esquema.primary, esquema.onPrimary) >= 4.5f
            )
            assertTrue(
                "${paleta.name}: texto sobre contenedor",
                contraste(esquema.primaryContainer, esquema.onPrimaryContainer) >= 4.5f
            )
            assertTrue(
                "${paleta.name}: texto sobre fondo",
                contraste(esquema.background, esquema.onBackground) >= 4.5f
            )
        }
    }

    @Test
    fun `el modo amoled usa negro puro en el fondo`() {
        PaletaColor.entries.forEach { paleta ->
            val esquema = esquemaOscuro(paleta.semilla, amoled = true)
            assertEquals(Color.Black, esquema.background)
            assertEquals(Color.Black, esquema.surface)
            assertEquals(Color.Black, esquema.surfaceContainerLowest)
        }
    }

    @Test
    fun `se puede generar una paleta desde un color personalizado`() {
        val esquema = esquemaClaro(Color(0xFF00CED1))
        assertTrue(contraste(esquema.primary, esquema.onPrimary) >= 4.5f)
        assertTrue(contraste(esquema.background, esquema.onBackground) >= 4.5f)
        assertEquals(esquema.primary, colorPrincipal(Color(0xFF00CED1), 40))
    }
}
