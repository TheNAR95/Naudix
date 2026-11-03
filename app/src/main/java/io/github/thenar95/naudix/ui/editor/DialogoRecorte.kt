package io.github.thenar95.naudix.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.thenar95.naudix.R

/**
 * Permite escribir el minuto y el segundo exactos de un extremo del recorte.
 * El botón de aceptar se desactiva mientras el valor esté fuera del rango válido.
 */
@Composable
fun DialogoRecorte(
    titulo: String,
    inicialMs: Long,
    minimoMs: Long,
    maximoMs: Long,
    onAceptar: (Long) -> Unit,
    onCancelar: () -> Unit
) {
    var minutos by remember { mutableStateOf((inicialMs / 60_000L).toString()) }
    var segundos by remember { mutableStateOf(((inicialMs / 1_000L) % 60L).toString()) }

    val minutosNumero = minutos.toIntOrNull()
    val segundosNumero = segundos.toIntOrNull()
    val totalMs = if (
        minutosNumero != null && segundosNumero != null &&
        minutosNumero >= 0 && segundosNumero in 0..59
    ) {
        minutosNumero * 60_000L + segundosNumero * 1_000L
    } else {
        null
    }
    val valido = totalMs != null && totalMs in minimoMs..maximoMs

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = minutos,
                        onValueChange = { nuevo ->
                            minutos = nuevo.filter { it.isDigit() }.take(5)
                        },
                        label = { Text(stringResource(R.string.trim_minutes)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = totalMs != null && !valido,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = segundos,
                        onValueChange = { nuevo ->
                            segundos = nuevo.filter { it.isDigit() }.take(2)
                        },
                        label = { Text(stringResource(R.string.trim_seconds)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = totalMs != null && !valido,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(
                        R.string.trim_range_hint,
                        formatoTiempo(minimoMs),
                        formatoTiempo(maximoMs)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valido) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { totalMs?.let(onAceptar) },
                enabled = valido
            ) {
                Text(stringResource(R.string.dialog_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
