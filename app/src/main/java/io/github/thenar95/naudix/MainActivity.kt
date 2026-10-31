package io.github.thenar95.naudix

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
import io.github.thenar95.naudix.ajustes.ModoTema
import io.github.thenar95.naudix.ui.NaudixApp
import io.github.thenar95.naudix.ui.theme.NaudixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val almacen = remember { AlmacenAjustes(applicationContext) }
            val ajustes by almacen.ajustes.collectAsStateWithLifecycle()

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
