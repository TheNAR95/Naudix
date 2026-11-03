package io.github.thenar95.naudix.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recorta [origen] entre [inicioMs] y [finMs] y lo codifica en el formato elegido.
 *
 * Todo el proceso (decodificar con [MediaCodec], adaptar el PCM y codificar) se hace
 * dentro de la app, sin librerías nativas: así funciona igual en teléfonos de 32 bits.
 *
 * @return el trabajo en curso; al cancelarlo se detiene la exportación.
 */
fun exportarAudio(
    alcance: CoroutineScope,
    context: Context,
    origen: Uri,
    destino: Uri,
    inicioMs: Long,
    finMs: Long,
    opciones: OpcionesExportacion,
    alProgresar: (Float) -> Unit,
    alTerminar: (ResultadoExportacion) -> Unit
): Job = alcance.launch {
    val resultado = try {
        withContext(Dispatchers.Default) {
            exportar(context, origen, destino, inicioMs, finMs, opciones, alProgresar)
        }
        ResultadoExportacion.Exito
    } catch (e: CancellationException) {
        ResultadoExportacion.Cancelado
    } catch (e: Throwable) {
        android.util.Log.e("Naudix", "Error al exportar audio", e)
        ResultadoExportacion.Error
    }
    alTerminar(resultado)
}

private suspend fun exportar(
    context: Context,
    origen: Uri,
    destino: Uri,
    inicioMs: Long,
    finMs: Long,
    opciones: OpcionesExportacion,
    alProgresar: (Float) -> Unit
) {
    val temporal = File.createTempFile(
        "naudix-export-",
        ".${opciones.formato.extension}",
        context.cacheDir
    )
    try {
        recodificar(context, origen, temporal, inicioMs, finMs, opciones, alProgresar)
        copiar(context, temporal, destino, alProgresar)
        alProgresar(1f)
    } finally {
        temporal.delete()
    }
}

private suspend fun recodificar(
    context: Context,
    origen: Uri,
    archivo: File,
    inicioMs: Long,
    finMs: Long,
    opciones: OpcionesExportacion,
    alProgresar: (Float) -> Unit
) {
    val inicioUs = inicioMs * 1000
    val finUs = finMs * 1000
    if (finUs <= inicioUs) throw IllegalArgumentException("Recorte vacío")
    val duracionUs = finUs - inicioUs

    val extractor = MediaExtractor()
    var decodificador: MediaCodec? = null
    var codificador: Codificador? = null
    var terminado = false

    try {
        extractor.setDataSource(context, origen, null)
        val pista = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: throw IOException("El archivo no tiene audio")
        extractor.selectTrack(pista)

        val formatoEntrada = extractor.getTrackFormat(pista)
        val mime = formatoEntrada.getString(MediaFormat.KEY_MIME)
            ?: throw IOException("Pista de audio inválida")

        val decodificadorActual = MediaCodec.createDecoderByType(mime).also {
            it.configure(formatoEntrada, null, null, 0)
            it.start()
        }
        decodificador = decodificadorActual

        extractor.seekTo(inicioUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val info = MediaCodec.BufferInfo()
        var formatoSalida: MediaFormat? = null
        var canalesPcm = 0
        var frecuenciaPcm = 0
        var flotante = false
        var procesador: ProcesadorPcm? = null
        var entradaTerminada = false
        var salidaTerminada = false
        var framesEscritos = 0L

        while (!salidaTerminada) {
            currentCoroutineContext().ensureActive()

            if (!entradaTerminada) {
                val entrada = decodificadorActual.dequeueInputBuffer(10_000)
                if (entrada >= 0) {
                    val buffer = decodificadorActual.getInputBuffer(entrada)
                    val leidos = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                    when {
                        leidos < 0 || extractor.sampleTime >= finUs -> {
                            decodificadorActual.queueInputBuffer(
                                entrada, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            entradaTerminada = true
                        }

                        else -> {
                            decodificadorActual.queueInputBuffer(
                                entrada, 0, leidos, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }
            }

            val salida = decodificadorActual.dequeueOutputBuffer(info, 10_000)
            when {
                salida == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    formatoSalida = decodificadorActual.outputFormat
                }

                salida >= 0 -> {
                    if (codificador == null) {
                        val formato = formatoSalida
                            ?: runCatching { decodificadorActual.outputFormat }.getOrNull()
                            ?: formatoEntrada
                        canalesPcm = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            .coerceAtLeast(1)
                        frecuenciaPcm = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            .coerceAtLeast(8000)
                        flotante = formato.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            formato.getInteger(MediaFormat.KEY_PCM_ENCODING) ==
                            AudioFormat.ENCODING_PCM_FLOAT

                        val canalesSalida = minOf(canalesPcm, canalesSoportados(opciones.formato))
                        val frecuenciaSalida = frecuenciaSoportada(opciones.formato, frecuenciaPcm)
                        procesador = ProcesadorPcm(
                            canalesEntrada = canalesPcm,
                            frecuenciaEntrada = frecuenciaPcm,
                            canalesSalida = canalesSalida,
                            frecuenciaSalida = frecuenciaSalida
                        )
                        codificador = crearCodificador(
                            formato = opciones.formato,
                            archivo = archivo,
                            canales = canalesSalida,
                            frecuencia = frecuenciaSalida,
                            bitrateKbps = opciones.bitrateKbps
                        )
                    }

                    val buffer = decodificadorActual.getOutputBuffer(salida)
                    val procesadorActual = procesador
                    val codificadorActual = codificador
                    if (buffer != null && info.size > 0 && procesadorActual != null) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val frames = info.size / (2 * canalesPcm)
                        if (frames > 0) {
                            val muestras = leerPcm(buffer, frames * canalesPcm, flotante)
                            val primero = framesHasta(
                                info.presentationTimeUs, frecuenciaPcm, inicioUs
                            ).coerceIn(0, frames)
                            val ultimo = framesHasta(
                                info.presentationTimeUs, frecuenciaPcm, finUs
                            ).coerceIn(0, frames)
                            if (ultimo > primero) {
                                val cantidad = ultimo - primero
                                val trozo = if (primero == 0 && cantidad == frames) {
                                    muestras
                                } else {
                                    muestras.copyOfRange(
                                        primero * canalesPcm,
                                        ultimo * canalesPcm
                                    )
                                }
                                procesadorActual.procesar(trozo, cantidad) { bloque, n ->
                                    codificadorActual.escribir(bloque, n)
                                }
                                framesEscritos += cantidad
                            }
                        }

                        val transcurrido = (info.presentationTimeUs - inicioUs)
                            .coerceAtLeast(0L)
                        alProgresar(
                            ((transcurrido.toFloat() / duracionUs) * 0.9f).coerceIn(0f, 0.9f)
                        )
                    }

                    decodificadorActual.releaseOutputBuffer(salida, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        salidaTerminada = true
                    }
                }
            }
        }

        val codificadorFinal = codificador ?: throw IOException("No se pudo decodificar el audio")
        if (framesEscritos <= 0L) throw IOException("El recorte no contiene audio")
        codificadorFinal.terminar()
        terminado = true
    } finally {
        if (!terminado) {
            runCatching { codificador?.abortar() }
        }
        runCatching { decodificador?.stop() }
        runCatching { decodificador?.release() }
        extractor.release()
    }
}

/** Convierte el buffer del decodificador a muestras de 16 bits entrelazadas. */
private fun leerPcm(buffer: ByteBuffer, muestras: Int, flotante: Boolean): ShortArray {
    val salida = ShortArray(muestras)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    if (flotante) {
        val origen = buffer.asFloatBuffer()
        for (i in 0 until muestras) {
            salida[i] = (origen.get(i) * 32767f)
                .toInt()
                .coerceIn(-32768, 32767)
                .toShort()
        }
    } else {
        val origen = buffer.asShortBuffer()
        origen.get(salida, 0, minOf(muestras, origen.remaining()))
    }
    return salida
}

/** Número de frames que hay entre [ptsUs] y [limiteUs] a la frecuencia dada. */
private fun framesHasta(ptsUs: Long, frecuencia: Int, limiteUs: Long): Int {
    val diferencia = limiteUs - ptsUs
    if (diferencia <= 0L) return 0
    return ((diferencia * frecuencia + 999_999L) / 1_000_000L).toInt()
}

private suspend fun copiar(
    context: Context,
    origen: File,
    destino: Uri,
    alProgresar: (Float) -> Unit
) {
    val total = origen.length().coerceAtLeast(1L)
    var copiado = 0L
    val salida = context.contentResolver.openOutputStream(destino, "wt")
        ?: throw IOException("No se pudo escribir el archivo de destino")
    salida.use { out ->
        origen.inputStream().use { entrada ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val leidos = entrada.read(buffer)
                if (leidos < 0) break
                out.write(buffer, 0, leidos)
                copiado += leidos
                alProgresar(0.9f + 0.1f * (copiado.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }
}
