package io.github.thenar95.naudix

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PantallaInicio()
                }
            }
        }
    }
}

@Composable
fun PantallaInicio() {
    val context = LocalContext.current
    var nombreArchivo by remember { mutableStateOf<String?>(null) }
    var reproduciendo by remember { mutableStateOf(false) }
    var uriActual by remember { mutableStateOf<Uri?>(null) }
    var onda by remember { mutableStateOf<DatosOnda?>(null) }
    var calculando by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                reproduciendo = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(uriActual) {
        onda = null
        val uri = uriActual ?: return@LaunchedEffect
        calculando = true
        onda = calcularOnda(context, uri)
        calculando = false
    }

    val selector = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val indice = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && indice >= 0) {
                    nombreArchivo = cursor.getString(indice)
                }
            }
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            uriActual = uri
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { selector.launch(arrayOf("audio/*")) }) {
            Text("Elegir audio")
        }
        Spacer(Modifier.height(16.dp))
        Text(nombreArchivo ?: "Ningún archivo seleccionado")

        if (nombreArchivo != null) {
            Spacer(Modifier.height(16.dp))
            when {
                calculando -> Text("Calculando onda…")
                onda != null -> OndaView(
                    datos = onda!!,
                    modifier = Modifier.fillMaxWidth().height(160.dp)
                )
                else -> Text("No se pudo leer la onda de este archivo")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Text(if (reproduciendo) "Pausar" else "Reproducir")
            }
        }
    }
}