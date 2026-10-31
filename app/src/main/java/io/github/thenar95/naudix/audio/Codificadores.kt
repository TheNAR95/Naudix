package io.github.thenar95.naudix.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import net.sourceforge.javaflacencoder.EncodingConfiguration
import net.sourceforge.javaflacencoder.FLACEncoder
import net.sourceforge.javaflacencoder.FLACOutputStream
import net.sourceforge.javaflacencoder.StreamConfiguration

/**
 * Recibe PCM entero de 16 bits con los canales entrelazados y escribe el archivo
 * final en [archivo].
 */
internal interface Codificador {

    fun escribir(muestras: ShortArray, frames: Int)

    /** Cierra el archivo escribiendo lo que falte (cabeceras, frames pendientes…). */
    fun terminar()

    /** Libera recursos sin necesidad de dejar un archivo válido. */
    fun abortar() = Unit
}

internal fun crearCodificador(
    formato: FormatoSalida,
    archivo: File,
    canales: Int,
    frecuencia: Int,
    bitrateKbps: Int
): Codificador = when (formato) {
    FormatoSalida.M4A -> CodificadorAac(archivo, canales, frecuencia, bitrateKbps)
    FormatoSalida.MP3 -> CodificadorMp3(archivo, canales, frecuencia, bitrateKbps)
    FormatoSalida.WAV -> CodificadorWav(archivo, canales, frecuencia)
    FormatoSalida.FLAC -> CodificadorFlac(archivo, canales, frecuencia)
}

/** Formatos que sólo admiten uno o dos canales; el resto se mezcla antes de codificar. */
internal fun canalesSoportados(formato: FormatoSalida): Int = when (formato) {
    FormatoSalida.M4A, FormatoSalida.MP3 -> 2
    FormatoSalida.WAV, FormatoSalida.FLAC -> 8
}

/** Frecuencia que se usará al codificar: si la original no es válida, se remuestrea. */
internal fun frecuenciaSoportada(formato: FormatoSalida, frecuencia: Int): Int {
    val validas = when (formato) {
        FormatoSalida.MP3 -> setOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)
        FormatoSalida.M4A -> setOf(8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000)
        FormatoSalida.WAV, FormatoSalida.FLAC -> return frecuencia
    }
    return if (frecuencia in validas) frecuencia else 44100
}

// ---------------------------------------------------------------- WAV

private class CodificadorWav(
    private val archivo: File,
    private val canales: Int,
    private val frecuencia: Int
) : Codificador {

    private val flujo = BufferedOutputStream(FileOutputStream(archivo), 128 * 1024)
    private var bytesDatos = 0L

    init {
        // Se reserva la cabecera y se rellena al terminar, cuando ya se sabe el tamaño.
        flujo.write(ByteArray(44))
    }

    override fun escribir(muestras: ShortArray, frames: Int) {
        val bytes = ByteArray(frames * canales * 2)
        var j = 0
        for (i in 0 until frames * canales) {
            val v = muestras[i].toInt()
            bytes[j++] = v.toByte()
            bytes[j++] = (v shr 8).toByte()
        }
        flujo.write(bytes)
        bytesDatos += bytes.size
    }

    override fun terminar() {
        flujo.flush()
        flujo.close()
        RandomAccessFile(archivo, "rw").use { raf ->
            raf.seek(0)
            raf.write(cabeceraWav(canales, frecuencia, bytesDatos))
        }
    }

    override fun abortar() {
        runCatching { flujo.close() }
    }
}

private fun cabeceraWav(canales: Int, frecuencia: Int, bytesDatos: Long): ByteArray {
    val cabecera = ByteArray(44)
    val bloque = canales * 2

    fun texto(posicion: Int, valor: String) {
        for (i in valor.indices) cabecera[posicion + i] = valor[i].code.toByte()
    }

    fun le32(posicion: Int, valor: Int) {
        for (i in 0..3) cabecera[posicion + i] = ((valor shr (8 * i)) and 0xFF).toByte()
    }

    fun le16(posicion: Int, valor: Int) {
        for (i in 0..1) cabecera[posicion + i] = ((valor shr (8 * i)) and 0xFF).toByte()
    }

    val tamano = (bytesDatos + 36).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    val datos = bytesDatos.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    texto(0, "RIFF")
    le32(4, tamano)
    texto(8, "WAVE")
    texto(12, "fmt ")
    le32(16, 16)
    le16(20, 1) // PCM
    le16(22, canales)
    le32(24, frecuencia)
    le32(28, frecuencia * bloque)
    le16(32, bloque)
    le16(34, 16)
    texto(36, "data")
    le32(40, datos)
    return cabecera
}

// ---------------------------------------------------------------- MP3

/**
 * Bitrate que admite MPEG Layer III para cada frecuencia: MPEG-1 (32-48 kHz) llega a
 * 320 kbps, MPEG-2 (16-24 kHz) a 160 y MPEG-2.5 (8-12 kHz) a 64.
 */
private fun bitrateValido(frecuencia: Int, bitrateKbps: Int): Int = when {
    frecuencia >= 32000 -> bitrateKbps.coerceIn(8, 320)
    frecuencia >= 16000 -> bitrateKbps.coerceIn(8, 160)
    else -> bitrateKbps.coerceIn(8, 64)
}

/**
 * Codificador MP3 en Java puro (port de LAME incluido en jump3r). No usa librerías
 * nativas, por lo que funciona igual en móviles de 32 y 64 bits.
 */
private class CodificadorMp3(
    archivo: File,
    private val canales: Int,
    private val frecuencia: Int,
    bitrateKbps: Int
) : Codificador {

    private val lame = Lame()
    private val configuracion: LameGlobalFlags
    private val salida = BufferedOutputStream(FileOutputStream(archivo), 128 * 1024)
    private var buffer = ByteArray(96 * 1024)
    private var izquierda = IntArray(0)
    private var derecha = IntArray(0)

    init {
        val ganancia = GainAnalysis()
        val flujoBits = BitStream()
        val ajustes = Presets()
        val cuantizacion = QuantizePVT()
        val cuantizador = Quantize()
        val vbr = VBRTag()
        val version = Version()
        val id3 = ID3Tag()
        val reserva = Reservoir()
        val takehiro = Takehiro()
        val decodificador = MPGLib()
        val interfaz = Interface()
        val comun = Common()

        lame.setModules(ganancia, flujoBits, ajustes, cuantizacion, cuantizador, vbr, version, id3, decodificador)
        flujoBits.setModules(ganancia, decodificador, version, vbr)
        id3.setModules(flujoBits, version)
        ajustes.setModules(lame)
        cuantizador.setModules(flujoBits, reserva, cuantizacion, takehiro)
        cuantizacion.setModules(takehiro, reserva, lame.enc.psy)
        reserva.setModules(flujoBits)
        takehiro.setModules(cuantizacion)
        vbr.setModules(lame, flujoBits, version)
        decodificador.setModules(interfaz, comun)
        interfaz.setModules(vbr, comun)

        configuracion = lame.lame_init()
        configuracion.num_channels = canales
        configuracion.in_samplerate = frecuencia
        configuracion.brate = bitrateValido(frecuencia, bitrateKbps)
        configuracion.mode = if (canales == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        configuracion.quality = 7
        configuracion.VBR = VbrMode.vbr_off
        configuracion.write_id3tag_automatic = false
        configuracion.bWriteVbrTag = false

        val resultado = lame.lame_init_params(configuracion)
        if (resultado < 0) {
            throw IOException("No se pudo inicializar el codificador MP3 ($resultado)")
        }
    }

    override fun escribir(muestras: ShortArray, frames: Int) {
        if (izquierda.size < frames) {
            izquierda = IntArray(frames)
            derecha = IntArray(frames)
        }
        for (i in 0 until frames) {
            val l = muestras[i * canales].toInt()
            izquierda[i] = l
            derecha[i] = if (canales > 1) muestras[i * canales + 1].toInt() else l
        }

        val necesario = (frames * 1.25).toInt() + 7200
        if (buffer.size < necesario) buffer = ByteArray(necesario)

        val escritos = lame.lame_encode_buffer_int(configuracion, izquierda, derecha, frames, buffer, 0, buffer.size)
        if (escritos < 0) throw IOException("Error del codificador MP3 ($escritos)")
        if (escritos > 0) salida.write(buffer, 0, escritos)
    }

    override fun terminar() {
        val escritos = lame.lame_encode_flush(configuracion, buffer, 0, buffer.size)
        if (escritos > 0) salida.write(buffer, 0, escritos)
        salida.flush()
        salida.close()
        lame.lame_close(configuracion)
    }

    override fun abortar() {
        runCatching { lame.lame_close(configuracion) }
        runCatching { salida.close() }
    }
}

// ---------------------------------------------------------------- FLAC

/** Codificador FLAC en Java puro (java-flac-encoder). */
private class CodificadorFlac(
    archivo: File,
    private val canales: Int,
    frecuencia: Int
) : Codificador {

    private val archivoSalida = RandomAccessFile(archivo, "rw").apply { setLength(0) }
    private val codificador = FLACEncoder()
    private val configuracion = StreamConfiguration(canales, 4096, 4096, frecuencia, 16)

    init {
        codificador.setOutputStream(SalidaFlac(archivoSalida))
        codificador.setThreadCount(1)
        if (!codificador.setStreamConfiguration(configuracion)) {
            throw IOException("Configuración FLAC no válida")
        }
        codificador.setEncodingConfiguration(EncodingConfiguration())
        codificador.openFLACStream()
    }

    override fun escribir(muestras: ShortArray, frames: Int) {
        val enteros = IntArray(frames * canales)
        for (i in enteros.indices) enteros[i] = muestras[i].toInt()
        codificador.addSamples(enteros, frames)
        codificador.encodeSamples(codificador.fullBlockSamplesAvailableToEncode(), false)
    }

    override fun terminar() {
        codificador.encodeSamples(codificador.samplesAvailableToEncode(), true)
        archivoSalida.close()
    }

    override fun abortar() {
        runCatching { archivoSalida.close() }
    }
}

/** Salida FLAC sobre un archivo con acceso aleatorio, para poder reescribir la cabecera. */
private class SalidaFlac(private val archivo: RandomAccessFile) : FLACOutputStream {

    override fun write(datos: ByteArray, offset: Int, cantidad: Int): Int {
        archivo.write(datos, offset, cantidad)
        return cantidad
    }

    override fun write(dato: Byte) = archivo.write(dato.toInt())

    override fun size(): Long = archivo.length()

    override fun seek(posicion: Long): Long {
        archivo.seek(posicion)
        return posicion
    }

    override fun canSeek(): Boolean = true

    override fun getPos(): Long = archivo.filePointer
}

// ---------------------------------------------------------------- M4A (AAC)

/** AAC con el codificador del sistema y contenedor MP4, ambos del propio Android. */
private class CodificadorAac(
    archivo: File,
    private val canales: Int,
    private val frecuencia: Int,
    bitrateKbps: Int
) : Codificador {

    private val codificador = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer = MediaMuxer(archivo.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val info = MediaCodec.BufferInfo()
    private var pista = -1
    private var pistaAgregada = false
    private var bytesEnviados = 0L
    private var terminado = false

    init {
        val formato = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, frecuencia, canales)
        formato.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        formato.setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
        formato.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        codificador.configure(formato, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codificador.start()
    }

    override fun escribir(muestras: ShortArray, frames: Int) {
        val bytes = ByteArray(frames * canales * 2)
        var j = 0
        for (i in 0 until frames * canales) {
            val v = muestras[i].toInt()
            bytes[j++] = v.toByte()
            bytes[j++] = (v shr 8).toByte()
        }

        var offset = 0
        val tamanoFrame = 2 * canales
        while (offset < bytes.size) {
            val indice = codificador.dequeueInputBuffer(10_000)
            if (indice < 0) continue
            val entrada = codificador.getInputBuffer(indice)!!
            entrada.clear()
            val disponible = minOf(entrada.remaining(), bytes.size - offset)
            // El codificador espera frames completos, sin partir muestras.
            val cantidad = (disponible - disponible % tamanoFrame).coerceAtLeast(
                minOf(disponible, tamanoFrame)
            )
            entrada.put(bytes, offset, cantidad)
            val pts = (bytesEnviados / tamanoFrame) * 1_000_000L / frecuencia
            codificador.queueInputBuffer(indice, 0, cantidad, pts, 0)
            bytesEnviados += cantidad
            offset += cantidad
        }

        drenar(fin = false)
    }

    override fun terminar() {
        if (!terminado) {
            terminado = true
            val indice = codificador.dequeueInputBuffer(10_000)
            if (indice >= 0) {
                val pts = (bytesEnviados / (2 * canales)) * 1_000_000L / frecuencia
                codificador.queueInputBuffer(indice, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drenar(fin = true)
        }
        runCatching { codificador.stop() }
        codificador.release()
        if (pistaAgregada) muxer.stop()
        muxer.release()
    }

    private fun drenar(fin: Boolean) {
        var intentos = 0
        while (true) {
            val indice = codificador.dequeueOutputBuffer(info, if (fin) 10_000 else 0)
            when {
                indice == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!fin || intentos++ > 500) return
                }

                indice == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    pista = muxer.addTrack(codificador.outputFormat)
                    muxer.start()
                    pistaAgregada = true
                }

                indice >= 0 -> {
                    val salida = codificador.getOutputBuffer(indice)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        info.size = 0
                    }
                    if (info.size > 0 && salida != null && pista >= 0) {
                        salida.position(info.offset)
                        salida.limit(info.offset + info.size)
                        muxer.writeSampleData(pista, salida, info)
                    }
                    codificador.releaseOutputBuffer(indice, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    override fun abortar() {
        runCatching { codificador.stop() }
        runCatching { codificador.release() }
        runCatching { muxer.release() }
    }
}
