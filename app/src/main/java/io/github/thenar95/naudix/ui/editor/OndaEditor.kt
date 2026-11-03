package io.github.thenar95.naudix.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.thenar95.naudix.R
import io.github.thenar95.naudix.audio.DatosOnda
import java.util.Locale
import kotlin.math.abs

fun formatoTiempo(ms: Long): String {
    val decimas = (ms / 100).coerceAtLeast(0)
    val d = decimas % 10
    val s = (decimas / 10) % 60
    val m = decimas / 600
    return String.format(Locale.getDefault(), "%d:%02d.%d", m, s, d)
}

@Composable
fun OndaEditor(
    datos: DatosOnda,
    inicio: Float,
    fin: Float,
    onCambio: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    /** Posición de reproducción como fracción de la duración (0..1). */
    posicion: Float = 0f,
    /** Se llama al pulsar o arrastrar sobre la onda para mover la reproducción. */
    onBuscar: (Float) -> Unit = {}
) {
    val maximo = remember(datos) {
        datos.picos.maxOrNull()?.takeIf { it > 0f } ?: 1f
    }
    val colorSel = MaterialTheme.colorScheme.primary
    val colorFuera = MaterialTheme.colorScheme.outlineVariant
    val colorManija = MaterialTheme.colorScheme.tertiary
    val colorPosicion = MaterialTheme.colorScheme.onSurface
    val inicioActual by rememberUpdatedState(inicio)
    val finActual by rememberUpdatedState(fin)
    val cambioActual by rememberUpdatedState(onCambio)
    val buscarActual by rememberUpdatedState(onBuscar)
    val descripcion = stringResource(R.string.waveform_description)

    val arrastre = if (habilitado) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                if (size.width <= 0) return@awaitEachGesture
                val pulsacion = awaitFirstDown(requireUnconsumed = false)
                val fraccion = (pulsacion.position.x / size.width).coerceIn(0f, 1f)
                val umbral = 28.dp.toPx() / size.width
                val distanciaInicio = abs(fraccion - inicioActual)
                val distanciaFin = abs(fraccion - finActual)
                val manija = when {
                    distanciaInicio <= umbral && distanciaInicio <= distanciaFin -> 1
                    distanciaFin <= umbral -> 2
                    else -> 0
                }

                pulsacion.consume()
                if (manija == 0) {
                    // Pulsar o arrastrar fuera de las manijas mueve la barra de
                    // reproducción: la reproducción continúa desde ese punto.
                    buscarActual(fraccion)
                    drag(pulsacion.id) { cambio ->
                        cambio.consume()
                        buscarActual((cambio.position.x / size.width).coerceIn(0f, 1f))
                    }
                } else {
                    drag(pulsacion.id) { cambio ->
                        cambio.consume()
                        val f = (cambio.position.x / size.width).coerceIn(0f, 1f)
                        if (manija == 1) {
                            cambioActual(f.coerceAtMost(finActual - 0.01f), finActual)
                        } else {
                            cambioActual(inicioActual, f.coerceAtLeast(inicioActual + 0.01f))
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .then(arrastre)
            .semantics { contentDescription = descripcion }
    ) {
        val n = datos.picos.size
        val ancho = size.width / n
        val medio = size.height / 2
        datos.picos.forEachIndexed { i, pico ->
            val x = i * ancho + ancho / 2
            val frac = x / size.width
            val alto = (pico / maximo) * medio
            drawLine(
                color = if (frac in inicio..fin) colorSel else colorFuera,
                start = Offset(x, medio - alto),
                end = Offset(x, medio + alto),
                strokeWidth = ancho.coerceAtLeast(1f)
            )
        }
        for (f in listOf(inicio, fin)) {
            val x = f * size.width
            drawLine(colorManija, Offset(x, 0f), Offset(x, size.height), strokeWidth = 6f)
            drawCircle(colorManija, radius = 16f, center = Offset(x, medio))
        }

        // Línea que marca el punto exacto que está sonando.
        val xPosicion = posicion.coerceIn(0f, 1f) * size.width
        drawLine(
            color = colorPosicion,
            start = Offset(xPosicion, 0f),
            end = Offset(xPosicion, size.height),
            strokeWidth = 3f
        )
        drawCircle(colorPosicion, radius = 8f, center = Offset(xPosicion, medio))
    }
}
