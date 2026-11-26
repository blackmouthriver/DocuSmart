package com.docsmart.features.utilities.presentation

import android.net.Uri

/** Un archivo elegido con el selector del sistema (tamaño -1 si el proveedor no lo informa). */
data class PickedFile(
    val uri: Uri,
    val name: String,
    val size: Long,
)
