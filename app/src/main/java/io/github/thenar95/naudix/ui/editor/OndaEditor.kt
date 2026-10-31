package io.github.thenar95.naudix.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
    habilitado: Boolean = true
) {
    val maximo = remember(datos) {
        datos.picos.maxOrNull()?.takeIf { it > 0f } ?: 1f
    }
    val colorSel = MaterialTheme.colorScheme.primary
    val colorFuera = MaterialTheme.colorScheme.outlineVariant
    val colorManija = MaterialTheme.colorScheme.tertiary
    val inicioActual by rememberUpdatedState(inicio)
    val finActual by rememberUpdatedState(fin)
    val cambioActual by rememberUpdatedState(onCambio)
    val descripcion = stringResource(R.string.waveform_description)

    val arrastre = if (habilitado) {
        Modifier.pointerInput(Unit) {
            var manijaActiva = 0
            detectDragGestures(
                onDragStart = { punto ->
                    val f = punto.x / size.width
                    manijaActiva =
                        if (abs(f - inicioActual) <= abs(f - finActual)) 1 else 2
                },
                onDrag = { cambio, _ ->
                    cambio.consume()
                    val f = (cambio.position.x / size.width).coerceIn(0f, 1f)
                    if (manijaActiva == 1) {
                        cambioActual(f.coerceAtMost(finActual - 0.01f), finActual)
                    } else {
                        cambioActual(inicioActual, f.coerceAtLeast(inicioActual + 0.01f))
                    }
                }
            )
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
    }
}
