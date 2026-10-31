package io.github.thenar95.naudix.audio

/**
 * Formatos de exportación. Todos se generan con codificadores incluidos en la app
 * (MediaCodec para AAC y librerías Java para MP3 y FLAC), así que no dependen de
 * librerías nativas y funcionan en cualquier arquitectura, incluidos los móviles
 * de 32 bits.
 */
enum class FormatoSalida(
    val etiqueta: String,
    val extension: String,
    val mimeType: String,
    val conPerdida: Boolean
) {
    M4A("M4A (AAC)", "m4a", "audio/mp4", true),
    MP3("MP3", "mp3", "audio/mpeg", true),
    WAV("WAV", "wav", "audio/wav", false),
    FLAC("FLAC", "flac", "audio/flac", false)
}

data class OpcionesExportacion(
    val formato: FormatoSalida = FormatoSalida.M4A,
    val bitrateKbps: Int = 192
)

enum class ResultadoExportacion {
    Exito,
    Cancelado,
    Error
}

/** Nombre de archivo propuesto para la exportación: "cancion.mp3" -> "cancion.m4a". */
fun nombreSugerido(nombreOriginal: String?, formato: FormatoSalida): String {
    val base = nombreOriginal
        ?.substringBeforeLast('.', nombreOriginal)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "recorte"
    return "$base.${formato.extension}"
}
