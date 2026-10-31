package io.github.thenar95.naudix.ui.ajustes

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.thenar95.naudix.R
import io.github.thenar95.naudix.ajustes.Ajustes
import io.github.thenar95.naudix.ajustes.ModoTema
import io.github.thenar95.naudix.ui.theme.PaletaColor

/**
 * Hoja de ajustes de apariencia: Material You, paletas propias (incluido un color
 * a medida), tema claro/oscuro y fondo negro puro (AMOLED).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaAjustes(
    ajustes: Ajustes,
    onMaterialYou: (Boolean) -> Unit,
    onPaleta: (PaletaColor) -> Unit,
    onColorPersonalizado: (Color) -> Unit,
    onModoTema: (ModoTema) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onRestablecer: () -> Unit,
    onCerrar: () -> Unit
) {
    val soportaMaterialYou = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val materialYouActivo = ajustes.materialYou && soportaMaterialYou
    var eligiendoColor by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )

            FilaInterruptor(
                titulo = stringResource(R.string.settings_material_you),
                descripcion = stringResource(
                    if (soportaMaterialYou) {
                        R.string.settings_material_you_desc
                    } else {
                        R.string.settings_material_you_unavailable
                    }
                ),
                activado = materialYouActivo,
                habilitado = soportaMaterialYou,
                onCambio = onMaterialYou
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_palette),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        if (materialYouActivo) {
                            R.string.settings_palette_dynamic
                        } else {
                            R.string.settings_palette_desc
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaletaColor.entries.forEach { paleta ->
                        val esPersonalizada = paleta == PaletaColor.PERSONALIZADO
                        MuestraPaleta(
                            color = if (esPersonalizada) {
                                Color(ajustes.colorPersonalizado)
                            } else {
                                paleta.semilla
                            },
                            etiqueta = stringResource(paleta.etiqueta),
                            seleccionada = paleta == ajustes.paleta,
                            habilitada = !materialYouActivo,
                            onClick = {
                                if (esPersonalizada) {
                                    eligiendoColor = true
                                } else {
                                    onPaleta(paleta)
                                }
                            }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleMedium
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ModoTema.entries.forEachIndexed { indice, modo ->
                        SegmentedButton(
                            selected = ajustes.modoTema == modo,
                            onClick = { onModoTema(modo) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = indice,
                                count = ModoTema.entries.size
                            )
                        ) {
                            Text(stringResource(modo.etiqueta))
                        }
                    }
                }
            }

            FilaInterruptor(
                titulo = stringResource(R.string.settings_amoled),
                descripcion = stringResource(R.string.settings_amoled_desc),
                activado = ajustes.amoled,
                onCambio = onAmoled
            )

            TextButton(
                onClick = onRestablecer,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.settings_reset))
            }
        }
    }

    if (eligiendoColor) {
        DialogoColor(
            semillaInicial = Color(ajustes.colorPersonalizado),
            onAceptar = { color ->
                onColorPersonalizado(color)
                eligiendoColor = false
            },
            onCancelar = { eligiendoColor = false }
        )
    }
}

@Composable
private fun FilaInterruptor(
    titulo: String,
    descripcion: String,
    activado: Boolean,
    habilitado: Boolean = true,
    onCambio: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = titulo, style = MaterialTheme.typography.titleMedium)
            Text(
                text = descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = activado,
            onCheckedChange = onCambio,
            enabled = habilitado
        )
    }
}

@Composable
private fun MuestraPaleta(
    color: Color,
    etiqueta: String,
    seleccionada: Boolean,
    habilitada: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = seleccionada,
                enabled = habilitada,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 6.dp)
            .alpha(if (habilitada) 1f else 0.45f)
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            border = BorderStroke(
                width = if (seleccionada) 3.dp else 1.dp,
                color = if (seleccionada) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            modifier = Modifier.size(44.dp)
        ) {}
        Spacer(Modifier.height(6.dp))
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private val ModoTema.etiqueta: Int
    @StringRes get() = when (this) {
        ModoTema.SISTEMA -> R.string.theme_system
        ModoTema.CLARO -> R.string.theme_light
        ModoTema.OSCURO -> R.string.theme_dark
    }
