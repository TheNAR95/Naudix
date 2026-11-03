package io.github.thenar95.naudix.ajustes

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import io.github.thenar95.naudix.ui.theme.PaletaColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cómo se elige el tema claro u oscuro. */
enum class ModoTema {
    SISTEMA,
    CLARO,
    OSCURO
}

/** Idioma de la interfaz. El inglés es el idioma por defecto. */
enum class Idioma(val codigo: String) {
    INGLES("en"),
    ESPANOL("es")
}

data class Ajustes(
    val materialYou: Boolean = true,
    val paleta: PaletaColor = PaletaColor.DORADO,
    val colorPersonalizado: Int = PaletaColor.DORADO.semilla.toArgb(),
    val modoTema: ModoTema = ModoTema.SISTEMA,
    val amoled: Boolean = false,
    val idioma: Idioma = Idioma.INGLES
) {
    /** Color semilla real: el elegido a mano o el de la paleta seleccionada. */
    val semilla: Color
        get() = if (paleta == PaletaColor.PERSONALIZADO) {
            Color(colorPersonalizado)
        } else {
            paleta.semilla
        }
}

/**
 * Guarda las preferencias de apariencia en SharedPreferences y las expone como un
 * [StateFlow] para que el tema y la pantalla de ajustes reaccionen al instante.
 */
class AlmacenAjustes(context: Context) {

    private val preferencias = context.applicationContext
        .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    private val _ajustes = MutableStateFlow(leer())
    val ajustes: StateFlow<Ajustes> = _ajustes.asStateFlow()

    fun actualizar(cambio: (Ajustes) -> Ajustes) {
        val actuales = _ajustes.value
        val nuevos = cambio(actuales)
        if (nuevos == actuales) return
        _ajustes.value = nuevos
        guardar(nuevos)
    }

    fun restablecer() {
        actualizar { Ajustes() }
    }

    private fun leer(): Ajustes = Ajustes(
        materialYou = preferencias.getBoolean(CLAVE_MATERIAL_YOU, true),
        paleta = preferencias.getString(CLAVE_PALETA, null)
            ?.let { nombre -> PaletaColor.entries.firstOrNull { it.name == nombre } }
            ?: PaletaColor.DORADO,
        colorPersonalizado = preferencias.getInt(
            CLAVE_COLOR_PERSONALIZADO,
            PaletaColor.DORADO.semilla.toArgb()
        ),
        modoTema = preferencias.getString(CLAVE_MODO_TEMA, null)
            ?.let { nombre -> ModoTema.entries.firstOrNull { it.name == nombre } }
            ?: ModoTema.SISTEMA,
        amoled = preferencias.getBoolean(CLAVE_AMOLED, false),
        idioma = preferencias.getString(CLAVE_IDIOMA, null)
            ?.let { nombre -> Idioma.entries.firstOrNull { it.name == nombre } }
            ?: Idioma.INGLES
    )

    private fun guardar(ajustes: Ajustes) {
        preferencias.edit {
            putBoolean(CLAVE_MATERIAL_YOU, ajustes.materialYou)
            putString(CLAVE_PALETA, ajustes.paleta.name)
            putInt(CLAVE_COLOR_PERSONALIZADO, ajustes.colorPersonalizado)
            putString(CLAVE_MODO_TEMA, ajustes.modoTema.name)
            putBoolean(CLAVE_AMOLED, ajustes.amoled)
            putString(CLAVE_IDIOMA, ajustes.idioma.name)
        }
    }

    companion object {
        /**
         * Idioma guardado, leído sin construir todo el almacén. Se necesita en
         * [android.app.Activity.attachBaseContext] para aplicar el idioma antes de
         * que se carguen los recursos de la actividad.
         */
        fun idiomaGuardado(context: Context): Idioma {
            val valor = context
                .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
                .getString(CLAVE_IDIOMA, null)
            return valor
                ?.let { nombre -> Idioma.entries.firstOrNull { it.name == nombre } }
                ?: Idioma.INGLES
        }

        private const val ARCHIVO = "naudix_ajustes"
        private const val CLAVE_MATERIAL_YOU = "material_you"
        private const val CLAVE_PALETA = "paleta"
        private const val CLAVE_COLOR_PERSONALIZADO = "color_personalizado"
        private const val CLAVE_MODO_TEMA = "modo_tema"
        private const val CLAVE_AMOLED = "amoled"
        private const val CLAVE_IDIOMA = "idioma"
    }
}
