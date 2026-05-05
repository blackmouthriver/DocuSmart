package com.docsmart.features.pdftools.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.cards.DocuSmartToolCard
import com.docsmart.core.ui.theme.*
import com.docsmart.features.pdftools.presentation.PdfTool

data class PdfToolItem(
    val tool: PdfTool,
    val icon: ImageVector,
    val title: String,
    val description: String,
    val color: Color
)

private val toolItems = listOf(
    PdfToolItem(
        tool = PdfTool.MERGE,
        icon = Icons.Rounded.MergeType,
        title = "Unir PDFs",
        description = "Combina varios PDFs en uno solo",
        color = DocuBlue
    ),
    PdfToolItem(
        tool = PdfTool.SPLIT,
        icon = Icons.Rounded.CallSplit,
        title = "Dividir PDF",
        description = "Extrae páginas de un PDF",
        color = InfoCyan
    ),
    PdfToolItem(
        tool = PdfTool.COMPRESS,
        icon = Icons.Rounded.Compress,
        title = "Comprimir PDF",
        description = "Reduce el tamaño del archivo",
        color = SuccessGreen
    ),
    PdfToolItem(
        tool = PdfTool.ROTATE,
        icon = Icons.Rounded.RotateRight,
        title = "Rotar PDF",
        description = "Rota las páginas del documento",
        color = WarningAmber
    )
)

@Composable
fun PdfToolsMenu(
    onToolSelected: (PdfTool) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        toolItems.forEach { item ->
            DocuSmartToolCard(
                icon = item.icon,
                title = item.title,
                description = item.description,
                onClick = { onToolSelected(item.tool) },
                iconTint = item.color
            )
        }
    }
}