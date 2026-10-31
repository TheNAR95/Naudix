package io.github.thenar95.naudix.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.thenar95.naudix.audio.DatosOnda
import io.github.thenar95.naudix.audio.FormatoSalida
import io.github.thenar95.naudix.audio.OpcionesExportacion
import io.github.thenar95.naudix.audio.ResultadoExportacion
import io.github.thenar95.naudix.audio.calcularOnda
import io.github.thenar95.naudix.audio.exportarAudio
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MensajeEditor {
    ExportacionCorrecta,
    ExportacionCancelada,
    ErrorExportacion
}

data class EditorUiState(
    val nombreArchivo: String? = null,
    val uri: Uri? = null,
    val onda: DatosOnda? = null,
    val calculando: Boolean = false,
    val progresoCalculo: Float = 0f,
    val reproduciendo: Boolean = false,
    val inicio: Float = 0f,
    val fin: Float = 1f,
    val opcionesExportacion: OpcionesExportacion = OpcionesExportacion(),
    val exportando: Boolean = false,
    val progresoExportacion: Float = 0f,
    val cancelandoExportacion: Boolean = false,
    val mensaje: MensajeEditor? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableStateFlow(EditorUiState())
    val estado: StateFlow<EditorUiState> = _estado.asStateFlow()

    private val reproductor = ExoPlayer.Builder(application)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _estado.update { it.copy(reproduciendo = isPlaying) }
                    if (!isPlaying) detenerVigilancia()
                }
            })
        }

    private var trabajoOnda: Job? = null
    private var trabajoVigilancia: Job? = null
    private var trabajoExportacion: Job? = null
    private var destinoExportacion: Uri? = null

    fun seleccionarAudio(uri: Uri, nombre: String?) {
        if (_estado.value.exportando) cancelarExportacion()

        trabajoOnda?.cancel()
        detenerVigilancia()
        reproductor.pause()

        _estado.update {
            it.copy(
                nombreArchivo = nombre,
                uri = uri,
                onda = null,
                calculando = true,
                progresoCalculo = 0f,
                inicio = 0f,
                fin = 1f,
                reproduciendo = false,
                progresoExportacion = 0f,
                cancelandoExportacion = false,
                mensaje = null
            )
        }

        reproductor.setMediaItem(MediaItem.fromUri(uri))
        reproductor.prepare()

        trabajoOnda = viewModelScope.launch {
            val datos = calcularOnda(getApplication(), uri) { progreso ->
                _estado.update {
                    if (it.calculando) it.copy(progresoCalculo = progreso) else it
                }
            }
            if (!isActive) return@launch
            _estado.update {
                it.copy(
                    onda = datos,
                    calculando = false,
                    progresoCalculo = if (datos != null) 1f else 0f,
                    mensaje = null
                )
            }
        }
    }

    /**
     * Un único botón: si está sonando, pausa. Si no, reproduce solo el recorte.
     * Cuando la posición está fuera del recorte, salta al inicio de este.
     */
    fun alternarReproduccion() {
        val actual = _estado.value
        if (actual.exportando) return
        val duracion = actual.onda?.duracionMs ?: return

        if (reproductor.isPlaying) {
            reproductor.pause()
            return
        }

        val inicioMs = (actual.inicio * duracion).toLong()
        val finMs = (actual.fin * duracion).toLong()
        val posicion = reproductor.currentPosition
        if (posicion < inicioMs || posicion >= finMs) {
            reproductor.seekTo(inicioMs)
        }

        _estado.update { it.copy(mensaje = null) }
        reproductor.play()
        vigilarFin()
    }

    fun actualizarRango(inicio: Float, fin: Float) {
        if (_estado.value.exportando) return
        if (_estado.value.reproduciendo) {
            reproductor.pause()
        }
        detenerVigilancia()
        _estado.update {
            it.copy(
                inicio = inicio,
                fin = fin,
                reproduciendo = false
            )
        }
    }

    fun actualizarFormato(formato: FormatoSalida) {
        if (_estado.value.exportando) return
        _estado.update { it.copy(opcionesExportacion = it.opcionesExportacion.copy(formato = formato)) }
    }

    fun actualizarBitrate(bitrateKbps: Int) {
        if (_estado.value.exportando) return
        _estado.update {
            it.copy(opcionesExportacion = it.opcionesExportacion.copy(bitrateKbps = bitrateKbps))
        }
    }

    fun exportar(destino: Uri) {
        val actual = _estado.value
        val origen = actual.uri ?: return
        val onda = actual.onda ?: return
        if (actual.exportando) return

        reproductor.pause()
        detenerVigilancia()

        _estado.update {
            it.copy(
                exportando = true,
                progresoExportacion = 0f,
                cancelandoExportacion = false,
                mensaje = null
            )
        }
        destinoExportacion = destino

        trabajoExportacion = exportarAudio(
            alcance = viewModelScope,
            context = getApplication(),
            origen = origen,
            destino = destino,
            inicioMs = (actual.inicio * onda.duracionMs).toLong(),
            finMs = (actual.fin * onda.duracionMs).toLong(),
            opciones = actual.opcionesExportacion,
            alProgresar = { progreso ->
                _estado.update {
                    if (it.exportando) it.copy(progresoExportacion = progreso) else it
                }
            },
            alTerminar = { resultado ->
                trabajoExportacion = null
                destinoExportacion = null
                if (resultado != ResultadoExportacion.Exito) {
                    eliminarDestino(destino)
                }
                _estado.update {
                    it.copy(
                        exportando = false,
                        progresoExportacion = if (resultado == ResultadoExportacion.Exito) 1f else 0f,
                        cancelandoExportacion = false,
                        mensaje = when (resultado) {
                            ResultadoExportacion.Exito -> MensajeEditor.ExportacionCorrecta
                            ResultadoExportacion.Cancelado -> MensajeEditor.ExportacionCancelada
                            ResultadoExportacion.Error -> MensajeEditor.ErrorExportacion
                        }
                    )
                }
            }
        )
    }

    fun cancelarExportacion() {
        val trabajo = trabajoExportacion ?: return
        if (!_estado.value.exportando || _estado.value.cancelandoExportacion) return
        _estado.update { it.copy(cancelandoExportacion = true) }
        trabajo.cancel()
    }

    private fun vigilarFin() {
        trabajoVigilancia?.cancel()
        trabajoVigilancia = viewModelScope.launch {
            while (isActive) {
                val duracion = _estado.value.onda?.duracionMs ?: break
                val finMs = (_estado.value.fin * duracion).toLong()
                if (reproductor.currentPosition >= finMs) {
                    reproductor.pause()
                    break
                }
                delay(40)
            }
        }
    }

    private fun detenerVigilancia() {
        trabajoVigilancia?.cancel()
        trabajoVigilancia = null
    }

    private fun eliminarDestino(destino: Uri) {
        runCatching { getApplication<Application>().contentResolver.delete(destino, null, null) }
        destinoExportacion = null
    }

    override fun onCleared() {
        trabajoOnda?.cancel()
        detenerVigilancia()
        trabajoExportacion?.cancel()
        trabajoExportacion = null
        destinoExportacion?.let { eliminarDestino(it) }
        reproductor.release()
    }
}
