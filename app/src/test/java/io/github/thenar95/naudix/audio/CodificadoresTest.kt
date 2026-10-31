package io.github.thenar95.naudix.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Comprueba los codificadores que funcionan sin Android (MP3 y FLAC, en Java puro)
 * y el WAV, que se escribe a mano.
 */
class CodificadoresTest {

    private fun tono(frecuencia: Int, canales: Int, segundos: Double): ShortArray {
        val frames = (frecuencia * segundos).toInt()
        val datos = ShortArray(frames * canales)
        for (f in 0 until frames) {
            val v = (sin(2 * PI * 440.0 * f / frecuencia) * 12000).toInt().toShort()
            for (c in 0 until canales) datos[f * canales + c] = v
        }
        return datos
    }

    private fun codificar(
        formato: FormatoSalida,
        muestras: ShortArray,
        canales: Int,
        frecuencia: Int,
        bitrateKbps: Int = 192
    ): File {
        val archivo = File.createTempFile("naudix-test-", ".${formato.extension}")
        archivo.deleteOnExit()
        val codificador = crearCodificador(formato, archivo, canales, frecuencia, bitrateKbps)
        val bloque = 4096
        val total = muestras.size / canales
        var inicio = 0
        while (inicio < total) {
            val n = minOf(bloque, total - inicio)
            codificador.escribir(muestras.copyOfRange(inicio * canales, (inicio + n) * canales), n)
            inicio += n
        }
        codificador.terminar()
        return archivo
    }

    @Test
    fun `mp3 produce frames validos`() {
        val archivo = codificar(FormatoSalida.MP3, tono(44100, 2, 2.0), 2, 44100)
        assertTrue("El MP3 debería tener datos", archivo.length() > 20_000)

        val cabecera = archivo.readBytes().take(2).map { it.toInt() and 0xFF }
        assertEquals("Un MP3 empieza con 0xFF", 0xFF, cabecera[0])
        assertEquals("Frame MPEG sin protección", 0xE0, cabecera[1] and 0xE0)
    }

    @Test
    fun `flac produce stream valido`() {
        val archivo = codificar(FormatoSalida.FLAC, tono(48000, 2, 2.0), 2, 48000)
        val cabecera = archivo.readBytes().take(4).toByteArray().decodeToString()
        assertEquals("fLaC", cabecera)
        assertTrue("El FLAC debería tener datos", archivo.length() > 10_000)
    }

    @Test
    fun `wav escribe cabecera y datos`() {
        val canales = 2
        val frecuencia = 44100
        val muestras = tono(frecuencia, canales, 1.0)
        val archivo = codificar(FormatoSalida.WAV, muestras, canales, frecuencia)
        val bytes = archivo.readBytes()

        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())
        assertEquals("fmt ", bytes.copyOfRange(12, 16).decodeToString())
        assertEquals("data", bytes.copyOfRange(36, 40).decodeToString())

        val datos = leerInt(bytes, 40)
        assertEquals((muestras.size * 2).toLong(), datos.toLong())
        assertEquals((datos + 44).toLong(), bytes.size.toLong())
        assertEquals(frecuencia.toLong(), leerInt(bytes, 24).toLong())
        assertEquals(canales.toLong(), (leerInt(bytes, 22) and 0xFFFF).toLong())
    }

    private fun leerInt(bytes: ByteArray, posicion: Int): Int =
        (bytes[posicion].toInt() and 0xFF) or
            ((bytes[posicion + 1].toInt() and 0xFF) shl 8) or
            ((bytes[posicion + 2].toInt() and 0xFF) shl 16) or
            ((bytes[posicion + 3].toInt() and 0xFF) shl 24)
}
