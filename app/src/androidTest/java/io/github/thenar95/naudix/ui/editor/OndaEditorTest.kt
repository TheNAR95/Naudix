package io.github.thenar95.naudix.ui.editor

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.thenar95.naudix.audio.DatosOnda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Comprueba que pulsar sobre la onda mueve la barra de reproducción. */
@RunWith(AndroidJUnit4::class)
class OndaEditorTest {

    @get:Rule
    val regla = createComposeRule()

    @Test
    fun pulsarSobreLaOndaMueveLaPosicion() {
        var buscado: Float? = null
        regla.setContent {
            MaterialTheme {
                OndaEditor(
                    datos = DatosOnda(FloatArray(120) { 0.5f }, 10_000L),
                    inicio = 0f,
                    fin = 1f,
                    onCambio = { _, _ -> },
                    onBuscar = { buscado = it },
                    modifier = Modifier
                        .size(400.dp, 120.dp)
                        .testTag("onda")
                )
            }
        }

        regla.onNodeWithTag("onda").performTouchInput {
            click(Offset(width * 0.5f, height * 0.5f))
        }

        regla.runOnIdle {
            assertNotNull("onBuscar no se llamó", buscado)
            assertEquals(0.5f, buscado!!, 0.08f)
        }
    }

    @Test
    fun arrastrarCercaDelInicioAjustaElRecorteYNoLaReproduccion() {
        var buscado: Float? = null
        var rango: Pair<Float, Float>? = null
        regla.setContent {
            MaterialTheme {
                OndaEditor(
                    datos = DatosOnda(FloatArray(120) { 0.5f }, 10_000L),
                    inicio = 0.3f,
                    fin = 0.8f,
                    onCambio = { inicio, fin -> rango = inicio to fin },
                    onBuscar = { buscado = it },
                    modifier = Modifier
                        .size(400.dp, 120.dp)
                        .testTag("onda")
                )
            }
        }

        regla.onNodeWithTag("onda").performTouchInput {
            down(Offset(width * 0.31f, height * 0.5f))
            moveTo(Offset(width * 0.4f, height * 0.5f))
            up()
        }

        regla.runOnIdle {
            assertNotNull("no se ajustó el recorte", rango)
            assertEquals(0.4f, rango!!.first, 0.05f)
            assertEquals(0.8f, rango!!.second, 0.01f)
            assertEquals("la reproducción no debe moverse al arrastrar la manija", null, buscado)
        }
    }
}
