package io.github.thenar95.naudix.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs

class DatosOnda(val picos: FloatArray, val duracionMs: Long)

/**
 * Decodifica el audio a PCM y reduce la amplitud a [cantidad] picos para dibujar la onda.
 * Se ejecuta en un hilo de CPU y responde a la cancelación del coroutine.
 * [alProgresar] recibe el avance del análisis (0..1) para poder mostrarlo en pantalla.
 */
suspend fun calcularOnda(
    context: Context,
    uri: Uri,
    cantidad: Int = 800,
    alProgresar: (Float) -> Unit = {}
): DatosOnda? =
    withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val pista = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return@withContext null

            extractor.selectTrack(pista)
            val formato = extractor.getTrackFormat(pista)
            val mime = formato.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val duracionUs = if (formato.containsKey(MediaFormat.KEY_DURATION))
                formato.getLong(MediaFormat.KEY_DURATION) else 0L
            if (duracionUs <= 0L) return@withContext null

            val decodificador = MediaCodec.createDecoderByType(mime)
            codec = decodificador
            decodificador.configure(formato, null, null, 0)
            decodificador.start()

            val picos = FloatArray(cantidad)
            val info = MediaCodec.BufferInfo()
            var canales = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var frecuencia = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var entradaTerminada = false
            var salidaTerminada = false
            var ultimoProgreso = -1f

            while (!salidaTerminada) {
                ensureActive()

                if (!entradaTerminada) {
                    val i = decodificador.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buffer = decodificador.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buffer, 0)
                        if (n < 0) {
                            decodificador.queueInputBuffer(
                                i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            entradaTerminada = true
                        } else {
                            decodificador.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val o = decodificador.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decodificador.outputFormat
                        canales = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        frecuencia = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    o >= 0 -> {
                        if (duracionUs > 0L) {
                            val progreso = (info.presentationTimeUs.toFloat() / duracionUs)
                                .coerceIn(0f, 1f)
                            if (progreso - ultimoProgreso >= 0.01f || progreso >= 1f) {
                                ultimoProgreso = progreso
                                alProgresar(progreso)
                            }
                        }
                        val buffer = decodificador.getOutputBuffer(o)!!
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val muestras = buffer.asShortBuffer()
                        val frames = muestras.remaining() / canales
                        for (f in 0 until frames) {
                            val tUs = info.presentationTimeUs + f * 1_000_000L / frecuencia
                            val idx = ((tUs * cantidad) / duracionUs).toInt()
                                .coerceIn(0, cantidad - 1)
                            val v = abs(muestras.get(f * canales).toInt()) / 32768f
                            if (v > picos[idx]) picos[idx] = v
                        }
                        decodificador.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            salidaTerminada = true
                        }
                    }
                }
            }
            DatosOnda(picos, duracionUs / 1000)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
