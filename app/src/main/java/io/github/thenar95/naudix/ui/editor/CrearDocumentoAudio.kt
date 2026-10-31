package io.github.thenar95.naudix.ui.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** Datos que necesita el selector de destino: nombre propuesto y tipo MIME. */
data class SolicitudDocumento(val nombre: String, val mimeType: String)

/**
 * [ActivityResultContracts.CreateDocument] fija el tipo MIME al crearse, pero aquí el
 * formato se elige en tiempo de ejecución (M4A, MP3, WAV o FLAC).
 */
class CrearDocumentoAudio : ActivityResultContract<SolicitudDocumento, Uri?>() {

    override fun createIntent(context: Context, input: SolicitudDocumento): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.mimeType)
            .putExtra(Intent.EXTRA_TITLE, input.nombre)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
