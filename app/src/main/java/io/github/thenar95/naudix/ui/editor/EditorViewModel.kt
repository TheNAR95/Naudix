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

/** Duración mínima que puede tener un recorte para que siga siendo válido. */
const val MINIMO_RECORTE_MS = 100L

/** Extensiones que se tratan como video si el proveedor no devuelve un MIME. */
private val EXTENSIONES_VIDEO = setOf(
    "mp4", "m4v", "mkv", "webm", "3gp", "3g2", "mov", "avi", "ts", "mts", "m2ts"
)

enum class MensajeEditor {
    ExportacionCorrecta,
    ExportacionCancelada,
    ErrorExportacion
}

data class EditorUiState(
    val nombreArchivo: String? = null,
    val uri: Uri? = null,
    /** El archivo elegido es un video y solo se usará su pista de audio. */
    val esVideo: Boolean = false,
    val onda: DatosOnda? = null,
    val calculando: Boolean = false,
    val progresoCalculo: Float = 0f,
    val reproduciendo: Boolean = false,
    /** Punto exacto que está sonando (o donde quedó la reproducción). */
    val posicionMs: Long = 0L,
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

    private val reproductor: ExoPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private var trabajoOnda: Job? = null
    private var trabajoVigilancia: Job? = null
    private var trabajoExportacion: Job? = null
    private var destinoExportacion: Uri? = null

    init {
        reproductor.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val posicion = reproductor.currentPosition.coerceAtLeast(0L)
                _estado.update {
                    val limite = it.onda
                        ?.let { onda -> (it.fin * onda.duracionMs).toLong() }
                        ?: posicion
                    it.copy(
                        reproduciendo = isPlaying,
                        posicionMs = if (isPlaying) {
                            it.posicionMs
                        } else {
                            posicion.coerceAtMost(limite)
                        }
                    )
                }
                if (isPlaying) {
                    vigilarFin()
                } else {
                    detenerVigilancia()
                }
            }
        })
    }

    fun seleccionarAudio(uri: Uri, nombre: String?) {
        if (_estado.value.exportando) cancelarExportacion()

        trabajoOnda?.cancel()
        detenerVigilancia()
        reproductor.stop()
        reproductor.clearMediaItems()

        val esVideo = runCatching {
            val tipo = getApplication<Application>().contentResolver.getType(uri)
            if (tipo != null) {
                tipo.startsWith("video/")
            } else {
                val extension = nombre?.substringAfterLast('.', "")?.lowercase().orEmpty()
                extension in EXTENSIONES_VIDEO
            }
        }.getOrDefault(false)

        _estado.update {
            it.copy(
                nombreArchivo = nombre,
                uri = uri,
                esVideo = esVideo,
                onda = null,
                calculando = true,
                progresoCalculo = 0f,
                inicio = 0f,
                fin = 1f,
                reproduciendo = false,
                posicionMs = 0L,
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
            _estado.update { it.copy(posicionMs = reproductor.currentPosition.coerceAtLeast(0L)) }
            return
        }

        val inicioMs = (actual.inicio * duracion).toLong()
        val finMs = (actual.fin * duracion).toLong()
        val posicion = reproductor.currentPosition
        if (posicion < inicioMs || posicion >= finMs) {
            reproductor.seekTo(inicioMs)
            _estado.update { it.copy(posicionMs = inicioMs) }
        }

        _estado.update { it.copy(mensaje = null) }
        reproductor.play()
        vigilarFin()
    }

    /**
     * Mueve el punto de reproducción a [ms]. Si el audio está sonando, sigue
     * desde esa posición; si está en pausa, el próximo "reproducir" empieza ahí.
     */
    fun buscar(ms: Long) {
        val actual = _estado.value
        if (actual.exportando) return
        val duracion = actual.onda?.duracionMs ?: return
        val inicioMs = (actual.inicio * duracion).toLong()
        val finMs = (actual.fin * duracion).toLong()
        val objetivo = ms.coerceIn(inicioMs, finMs)
        reproductor.seekTo(objetivo)
        _estado.update { it.copy(posicionMs = objetivo, mensaje = null) }
    }

    fun actualizarRango(inicio: Float, fin: Float) {
        if (_estado.value.exportando) return
        val duracion = _estado.value.onda?.duracionMs ?: return
        val inicioAjustado = inicio.coerceIn(0f, 1f)
        val finAjustado = fin.coerceIn(inicioAjustado, 1f)

        if (_estado.value.reproduciendo) {
            reproductor.pause()
        }
        detenerVigilancia()
        _estado.update {
            it.copy(
                inicio = inicioAjustado,
                fin = finAjustado,
                reproduciendo = false,
                posicionMs = (inicioAjustado * duracion).toLong()
            )
        }
    }

    /** Fija el inicio exacto del recorte en milisegundos, respetando el mínimo. */
    fun fijarInicio(ms: Long) {
        val actual = _estado.value
        if (actual.exportando) return
        val duracion = actual.onda?.duracionMs ?: return
        val maximo = (actual.fin * duracion).toLong() - MINIMO_RECORTE_MS
        val nuevo = ms.coerceIn(0L, maximo.coerceAtLeast(0L))
        actualizarRango(nuevo.toFloat() / duracion, actual.fin)
    }

    /** Fija el fin exacto del recorte en milisegundos, respetando el mínimo. */
    fun fijarFin(ms: Long) {
        val actual = _estado.value
        if (actual.exportando) return
        val duracion = actual.onda?.duracionMs ?: return
        val minimo = (actual.inicio * duracion).toLong() + MINIMO_RECORTE_MS
        val nuevo = ms.coerceIn(minimo.coerceAtMost(duracion), duracion)
        actualizarRango(actual.inicio, nuevo.toFloat() / duracion)
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

    /** Cierra el archivo actual y vuelve a la pantalla de inicio. */
    fun cerrarArchivo() {
        if (_estado.value.exportando) cancelarExportacion()
        trabajoOnda?.cancel()
        trabajoOnda = null
        detenerVigilancia()
        reproductor.stop()
        reproductor.clearMediaItems()
        _estado.value = EditorUiState()
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
                // Si el usuario cerró la edición mientras exportaba, no se debe
                // reabrir un mensaje sobre un archivo que ya no está cargado.
                if (_estado.value.uri == origen) {
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
                val actual = _estado.value
                val duracion = actual.onda?.duracionMs ?: break
                val finMs = (actual.fin * duracion).toLong()
                val posicion = reproductor.currentPosition.coerceAtLeast(0L)

                _estado.update {
                    if (it.reproduciendo) {
                        it.copy(posicionMs = posicion.coerceAtMost(finMs))
                    } else {
                        it
                    }
                }

                if (posicion >= finMs) {
                    _estado.update { it.copy(posicionMs = finMs) }
                    reproductor.pause()
                    break
                }
                delay(30)
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
