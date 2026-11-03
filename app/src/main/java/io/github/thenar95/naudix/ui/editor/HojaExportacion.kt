package io.github.thenar95.naudix.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.thenar95.naudix.R
import io.github.thenar95.naudix.audio.FormatoSalida
import io.github.thenar95.naudix.audio.OpcionesExportacion

private val BITRATES = listOf(128, 192, 256, 320)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaExportacion(
    opciones: OpcionesExportacion,
    onFormato: (FormatoSalida) -> Unit,
    onBitrate: (Int) -> Unit,
    onExportar: () -> Unit,
    onDescartar: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDescartar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.export_sheet_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.export_sheet_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = stringResource(R.string.export_format_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormatoSalida.entries.forEach { formato ->
                        FilterChip(
                            selected = formato == opciones.formato,
                            onClick = { onFormato(formato) },
                            label = { Text(formato.etiqueta) }
                        )
                    }
                }

                if (opciones.formato.conPerdida) {
                    Text(
                        text = stringResource(R.string.export_quality_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BITRATES.forEach { bitrate ->
                            FilterChip(
                                selected = bitrate == opciones.bitrateKbps,
                                onClick = { onBitrate(bitrate) },
                                label = {
                                    Text(stringResource(R.string.export_bitrate, bitrate))
                                }
                            )
                        }
                    }
                }

            }
            Button(
                onClick = onExportar,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    stringResource(
                        R.string.export_action,
                        opciones.formato.etiqueta
                    )
                )
            }
        }
    }
}
