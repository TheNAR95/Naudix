package io.github.thenar95.naudix.audio

/** Entrega un bloque de PCM entero de 16 bits con los canales entrelazados. */
internal typealias EmisorPcm = (muestras: ShortArray, frames: Int) -> Unit

/**
 * Adapta el PCM que entrega el decodificador al formato que necesita el codificador:
 * convierte el número de canales (mezcla o duplica) y remuestrea con interpolación
 * lineal cuando la frecuencia no es compatible con el codificador elegido.
 *
 * Los buffers llegan por bloques, así que el remuestreador guarda los frames que
 * sobran para que el audio quede continuo entre bloques.
 */
internal class ProcesadorPcm(
    private val canalesEntrada: Int,
    private val frecuenciaEntrada: Int,
    private val canalesSalida: Int,
    private val frecuenciaSalida: Int
) {
    private val convertirCanales = canalesEntrada != canalesSalida
    private val remuestrear = frecuenciaEntrada != frecuenciaSalida
    private val paso = frecuenciaEntrada.toDouble() / frecuenciaSalida

    private var cola = ShortArray(0)
    private var colaFrames = 0
    private var posicion = 0.0

    fun procesar(muestras: ShortArray, frames: Int, emitir: EmisorPcm) {
        if (frames <= 0) return
        val (bloque, cantidad) = convertir(muestras, frames)
        if (!remuestrear) {
            emitir(bloque, cantidad)
            return
        }
        agregar(bloque, cantidad)
        emitirRemuestreo(emitir)
    }

    private fun convertir(muestras: ShortArray, frames: Int): Pair<ShortArray, Int> {
        if (!convertirCanales) return muestras to frames

        val salida = ShortArray(frames * canalesSalida)
        if (canalesEntrada > canalesSalida) {
            // Mezcla: cada canal de salida promedia los canales repartidos.
            for (f in 0 until frames) {
                val base = f * canalesEntrada
                for (c in 0 until canalesSalida) {
                    var suma = 0
                    var cuenta = 0
                    var k = c
                    while (k < canalesEntrada) {
                        suma += muestras[base + k].toInt()
                        cuenta++
                        k += canalesSalida
                    }
                    salida[f * canalesSalida + c] = (suma / cuenta).toShort()
                }
            }
        } else {
            // Mono a más canales: se duplica la misma muestra.
            for (f in 0 until frames) {
                val v = muestras[f * canalesEntrada]
                for (c in 0 until canalesSalida) {
                    salida[f * canalesSalida + c] = v
                }
            }
        }
        return salida to frames
    }

    private fun agregar(muestras: ShortArray, frames: Int) {
        val total = (colaFrames + frames) * canalesSalida
        val nuevo = ShortArray(total)
        System.arraycopy(cola, 0, nuevo, 0, colaFrames * canalesSalida)
        System.arraycopy(muestras, 0, nuevo, colaFrames * canalesSalida, frames * canalesSalida)
        cola = nuevo
        colaFrames += frames
    }

    private fun emitirRemuestreo(emitir: EmisorPcm) {
        val estimado = ((colaFrames - posicion) / paso).toInt().coerceAtLeast(0) + 2
        val salida = ShortArray(estimado * canalesSalida)
        var escritos = 0
        var pos = posicion

        while (pos + 1 < colaFrames) {
            val i = pos.toInt()
            val fraccion = pos - i
            for (c in 0 until canalesSalida) {
                val a = cola[i * canalesSalida + c].toDouble()
                val b = cola[(i + 1) * canalesSalida + c].toDouble()
                salida[escritos++] = (a + (b - a) * fraccion).toInt().toShort()
            }
            pos += paso
        }

        val consumidos = pos.toInt()
        if (consumidos > 0) {
            val restantes = colaFrames - consumidos
            val resto = ShortArray(restantes * canalesSalida)
            System.arraycopy(
                cola, consumidos * canalesSalida,
                resto, 0, restantes * canalesSalida
            )
            cola = resto
            colaFrames = restantes
            posicion = pos - consumidos
        } else {
            posicion = pos
        }

        if (escritos > 0) emitir(salida, escritos / canalesSalida)
    }
}
