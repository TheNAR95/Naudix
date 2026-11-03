package io.github.thenar95.naudix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.thenar95.naudix.R
import io.github.thenar95.naudix.ajustes.AlmacenAjustes
import io.github.thenar95.naudix.ui.ajustes.HojaAjustes
import io.github.thenar95.naudix.ui.editor.EditorScreen
import io.github.thenar95.naudix.ui.editor.EditorViewModel
import io.github.thenar95.naudix.ui.theme.PaletaColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NaudixApp(almacen: AlmacenAjustes) {
    val ajustes by almacen.ajustes.collectAsStateWithLifecycle()
    var mostrarAjustes by rememberSaveable { mutableStateOf(false) }
    val editor: EditorViewModel = viewModel()
    val estadoEditor by editor.estado.collectAsStateWithLifecycle()

    BackHandler(enabled = estadoEditor.uri != null && !mostrarAjustes) {
        editor.cerrarArchivo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (estadoEditor.uri != null) {
                        IconButton(onClick = editor::cerrarArchivo) {
                            Icon(
                                painter = painterResource(R.drawable.ic_atras),
                                contentDescription = stringResource(R.string.close_editor)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarAjustes = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ajustes),
                            contentDescription = stringResource(R.string.settings_open)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorScreen(viewModel = editor)
        }
    }

    if (mostrarAjustes) {
        HojaAjustes(
            ajustes = ajustes,
            onIdioma = { idioma ->
                almacen.actualizar { it.copy(idioma = idioma) }
            },
            onMaterialYou = { activo ->
                almacen.actualizar { it.copy(materialYou = activo) }
            },
            onPaleta = { paleta ->
                almacen.actualizar { it.copy(paleta = paleta) }
            },
            onColorPersonalizado = { color ->
                almacen.actualizar {
                    it.copy(
                        paleta = PaletaColor.PERSONALIZADO,
                        colorPersonalizado = color.toArgb()
                    )
                }
            },
            onModoTema = { modo ->
                almacen.actualizar { it.copy(modoTema = modo) }
            },
            onAmoled = { activo ->
                almacen.actualizar { it.copy(amoled = activo) }
            },
            onRestablecer = almacen::restablecer,
            onCerrar = { mostrarAjustes = false }
        )
    }
}
