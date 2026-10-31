package io.github.thenar95.naudix.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class ProcesadorPcmTest {

    private fun tono(frecuencia: Int, canales: Int, frames: Int, hz: Double = 440.0): ShortArray {
        val datos = ShortArray(frames * canales)
        for (f in 0 until frames) {
            val v = (sin(2 * PI * hz * f / frecuencia) * 20000).toInt().toShort()
            for (c in 0 until canales) datos[f * canales + c] = v
        }
        return datos
    }

    private fun procesar(
        procesador: ProcesadorPcm,
        muestras: ShortArray,
        canales: Int,
        bloque: Int
    ): ShortArray {
        val salida = ArrayList<Short>()
        var inicio = 0
        val frames = muestras.size / canales
        while (inicio < frames) {
            val n = minOf(bloque, frames - inicio)
            val trozo = muestras.copyOfRange(inicio * canales, (inicio + n) * canales)
            procesador.procesar(trozo, n) { bloqueSalida, cantidad ->
                for (i in 0 until cantidad * canales) salida.add(bloqueSalida[i])
            }
            inicio += n
        }
        return salida.toShortArray()
    }

    @Test
    fun `sin cambios deja el audio intacto`() {
        val procesador = ProcesadorPcm(2, 44100, 2, 44100)
        val entrada = tono(44100, 2, 4410)
        var salida = ShortArray(0)
        procesador.procesar(entrada, 4410) { bloque, cantidad ->
            salida += bloque.copyOf(cantidad * 2)
        }
        assertEquals(entrada.size, salida.size)
        assertTrue(entrada.contentEquals(salida))
    }

    @Test
    fun `convierte estereo a mono promediando`() {
        val procesador = ProcesadorPcm(2, 44100, 1, 44100)
        val entrada = shortArrayOf(1000, 3000, -1000, -3000)
        var salida = ShortArray(0)
        procesador.procesar(entrada, 2) { bloque, cantidad ->
            salida += bloque.copyOf(cantidad)
        }
        assertEquals(2, salida.size)
        assertEquals(2000, salida[0].toInt())
        assertEquals(-2000, salida[1].toInt())
    }

    @Test
    fun `remuestrea 48000 a 44100 manteniendo la duracion`() {
        val entrada = tono(48000, 2, 48000 * 2)
        val procesador = ProcesadorPcm(2, 48000, 2, 44100)
        var frames = 0L
        procesador.procesar(entrada, 48000 * 2) { _, cantidad -> frames += cantidad }

        val esperado = 44100L * 2
        assertTrue(
            "Frames remuestreados: $frames (esperado ~$esperado)",
            abs(frames - esperado) < 200
        )
    }

    @Test
    fun `remuestrea en bloques sin perder la continuidad`() {
        val entrada = tono(96000, 2, 96000)
        val procesador = ProcesadorPcm(2, 96000, 2, 44100)
        var frames = 0L
        var inicio = 0
        val porBloque = 4096
        while (inicio < 96000) {
            val n = minOf(porBloque, 96000 - inicio)
            val trozo = entrada.copyOfRange(inicio * 2, (inicio + n) * 2)
            procesador.procesar(trozo, n) { _, cantidad -> frames += cantidad }
            inicio += n
        }
        assertTrue("Frames remuestreados: $frames", abs(frames - 44100L) < 200)
    }
}
