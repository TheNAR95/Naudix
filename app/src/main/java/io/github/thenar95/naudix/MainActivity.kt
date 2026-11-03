package io.github.thenar95.naudix

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.thenar95.naudix.ajustes.AlmacenAjustes
import io.github.thenar95.naudix.ajustes.Idioma
import io.github.thenar95.naudix.ajustes.ModoTema
import io.github.thenar95.naudix.ui.NaudixApp
import io.github.thenar95.naudix.ui.theme.NaudixTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Idioma con el que se creó esta actividad, para recrearla al cambiarlo. */
    private var idiomaAplicado: Idioma? = null

    /**
     * Aplica el idioma elegido en los ajustes (inglés por defecto) antes de que
     * la actividad cargue recursos, para que toda la interfaz use ese idioma
     * aunque el teléfono esté configurado en otro.
     */
    override fun attachBaseContext(newBase: Context) {
        val idioma = AlmacenAjustes.idiomaGuardado(newBase)
        val locale = Locale.forLanguageTag(idioma.codigo)
        Locale.setDefault(locale)
        val configuracion = Configuration(newBase.resources.configuration)
        configuracion.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(configuracion))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val almacen = remember { AlmacenAjustes(applicationContext) }
            val ajustes by almacen.ajustes.collectAsStateWithLifecycle()

            // Al cambiar el idioma en los ajustes se recrea la actividad para que
            // los recursos se carguen en el idioma nuevo.
            LaunchedEffect(ajustes.idioma) {
                val anterior = idiomaAplicado
                idiomaAplicado = ajustes.idioma
                if (anterior != null && anterior != ajustes.idioma) recreate()
            }

            val oscuro = when (ajustes.modoTema) {
                ModoTema.SISTEMA -> isSystemInDarkTheme()
                ModoTema.CLARO -> false
                ModoTema.OSCURO -> true
            }

            // Los iconos de las barras del sistema deben contrastar con el tema elegido,
            // aunque no coincida con el tema del dispositivo.
            LaunchedEffect(oscuro) {
                val estilo = if (oscuro) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = estilo,
                    navigationBarStyle = estilo
                )
            }

            NaudixTheme(
                darkTheme = oscuro,
                dynamicColor = ajustes.materialYou,
                semilla = ajustes.semilla,
                amoled = ajustes.amoled
            ) {
                NaudixApp(almacen = almacen)
            }
        }
    }
}
