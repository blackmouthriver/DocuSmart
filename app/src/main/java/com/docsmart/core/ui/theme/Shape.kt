package com.docsmart.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DocuSmartShapes = Shapes(
    // Chips, badges pequeños
    extraSmall = RoundedCornerShape(8.dp),
    // Inputs, campos de formulario
    small = RoundedCornerShape(14.dp),
    // Botones
    medium = RoundedCornerShape(16.dp),
    // Tarjetas principales
    large = RoundedCornerShape(20.dp),
    // Bottom sheets, modales
    extraLarge = RoundedCornerShape(24.dp)
)