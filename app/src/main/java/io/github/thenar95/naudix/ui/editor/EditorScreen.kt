package io.github.thenar95.naudix.ui.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thenar95.naudix.R
import io.github.thenar95.naudix.audio.nombreSugerido
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel()
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mostrarHoja by rememberSaveable { mutableStateOf(false) }

    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.seleccionarAudio(uri, consultarNombre(context, uri))
        }
    }
    val creador = rememberLauncherForActivityResult(CrearDocumentoAudio()) { destino ->
        if (destino != null) {
            viewModel.exportar(destino)
        }
    }

    if (estado.uri == null) {
        EstadoVacio(
            modifier = modifier,
            onElegir = { selector.launch(arrayOf("audio/*")) }
        )
    } else {
        ContenidoEditor(
            estado = estado,
            modifier = modifier,
            onElegir = { selector.launch(arrayOf("audio/*")) },
            onReproducir = viewModel::alternarReproduccion,
            onRango = viewModel::actualizarRango,
            onAbrirExportacion = { mostrarHoja = true },
            onCancelarExportacion = viewModel::cancelarExportacion
        )
    }

    if (mostrarHoja) {
        HojaExportacion(
            opciones = estado.opcionesExportacion,
            onFormato = viewModel::actualizarFormato,
            onBitrate = viewModel::actualizarBitrate,
            onExportar = {
                mostrarHoja = false
                val formato = estado.opcionesExportacion.formato
                creador.launch(
                    SolicitudDocumento(
                        nombre = nombreSugerido(estado.nombreArchivo, formato),
                        mimeType = formato.mimeType
                    )
                )
            },
            onDescartar = { mostrarHoja = false }
        )
    }
}

@Composable
private fun EstadoVacio(
    modifier: Modifier,
    onElegir: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_onda),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onElegir) {
            Text(stringResource(R.string.choose_audio))
        }
    }
}

@Composable
private fun ContenidoEditor(
    estado: EditorUiState,
    modifier: Modifier,
    onElegir: () -> Unit,
    onReproducir: () -> Unit,
    onRango: (Float, Float) -> Unit,
    onAbrirExportacion: () -> Unit,
    onCancelarExportacion: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = estado.nombreArchivo ?: stringResource(R.string.no_file),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        OutlinedButton(
            onClick = onElegir,
            enabled = !estado.exportando
        ) {
            Text(stringResource(R.string.choose_other_audio))
        }

        val datos = estado.onda
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    estado.calculando -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(progress = { estado.progresoCalculo })
                        Text(
                            text = stringResource(
                                R.string.calculating_wave,
                                (estado.progresoCalculo * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    datos != null -> OndaEditor(
                        datos = datos,
                        inicio = estado.inicio,
                        fin = estado.fin,
                        onCambio = onRango,
                        habilitado = !estado.exportando,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 16.dp)
                    )

                    else -> Text(
                        text = stringResource(R.string.wave_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }

        if (datos != null) {
            val duracion = datos.duracionMs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TiempoEtiqueta(
                    etiqueta = stringResource(R.string.trim_start),
                    valor = formatoTiempo((estado.inicio * duracion).toLong()),
                    modifier = Modifier.weight(1f)
                )
                TiempoEtiqueta(
                    etiqueta = stringResource(R.string.trim_end),
                    valor = formatoTiempo((estado.fin * duracion).toLong()),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = stringResource(
                    R.string.trim_duration,
                    formatoTiempo(((estado.fin - estado.inicio) * duracion).toLong())
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onReproducir,
                enabled = !estado.exportando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(
                        if (estado.reproduciendo) R.drawable.ic_pausa else R.drawable.ic_play
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        if (estado.reproduciendo) R.string.pause_crop else R.string.play_crop
                    )
                )
            }

            OutlinedButton(
                onClick = onAbrirExportacion,
                enabled = !estado.exportando,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.export_trim))
            }

            if (estado.exportando) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.export_progress,
                                (estado.progresoExportacion * 100).roundToInt()
                            ),
                            style = MaterialTheme.typography.titleSmall
                        )
                        LinearProgressIndicator(
                            progress = { estado.progresoExportacion },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = onCancelarExportacion,
                            enabled = !estado.cancelandoExportacion,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                stringResource(
                                    if (estado.cancelandoExportacion) {
                                        R.string.export_cancelling
                                    } else {
                                        R.string.export_cancel
                                    }
                                )
                            )
                        }
                    }
                }
            }

            estado.mensaje?.let { mensaje ->
                val texto = when (mensaje) {
                    MensajeEditor.ExportacionCorrecta -> stringResource(R.string.export_success)
                    MensajeEditor.ExportacionCancelada -> stringResource(R.string.export_cancelled)
                    MensajeEditor.ErrorExportacion -> stringResource(R.string.export_error)
                }
                val color = when (mensaje) {
                    MensajeEditor.ExportacionCorrecta -> MaterialTheme.colorScheme.primary
                    MensajeEditor.ExportacionCancelada -> MaterialTheme.colorScheme.onSurfaceVariant
                    MensajeEditor.ErrorExportacion -> MaterialTheme.colorScheme.error
                }
                Text(
                    text = texto,
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TiempoEtiqueta(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun consultarNombre(context: Context, uri: Uri): String? =
    context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
