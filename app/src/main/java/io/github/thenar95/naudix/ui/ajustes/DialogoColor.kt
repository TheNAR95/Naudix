package io.github.thenar95.naudix.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thenar95.naudix.R
import io.github.thenar95.naudix.ui.theme.colorPrincipal
import io.github.thenar95.naudix.ui.theme.tonoSaturacion
import kotlin.math.roundToInt

/**
 * Selector de color libre: el usuario mueve el tono y la saturación y ve cómo
 * quedarían los colores principal y de contenedor antes de aplicarlo.
 */
@Composable
fun DialogoColor(
    semillaInicial: Color,
    onAceptar: (Color) -> Unit,
    onCancelar: () -> Unit
) {
    val (tonoInicial, saturacionInicial) = remember(semillaInicial) {
        tonoSaturacion(semillaInicial)
    }
    var tono by remember { mutableFloatStateOf(tonoInicial) }
    var saturacion by remember { mutableFloatStateOf(saturacionInicial) }
    val semilla = Color.hsl(tono.coerceIn(0f, 359.9f), saturacion, 0.5f)

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(stringResource(R.string.color_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(40, 60, 80, 90).forEach { nivel ->
                        Surface(
                            shape = CircleShape,
                            color = colorPrincipal(semilla, nivel),
                            modifier = Modifier.size(44.dp)
                        ) {}
                    }
                }

                Column {
                    Text(
                        text = stringResource(R.string.color_hue, tono.roundToInt()),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = tono,
                        onValueChange = { tono = it },
                        valueRange = 0f..360f
                    )
                }

                Column {
                    Text(
                        text = stringResource(
                            R.string.color_saturation,
                            (saturacion * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = saturacion,
                        onValueChange = { saturacion = it },
                        valueRange = 0.30f..0.85f
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAceptar(semilla) }) {
                Text(stringResource(R.string.color_use))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.color_cancel))
            }
        }
    )
}
