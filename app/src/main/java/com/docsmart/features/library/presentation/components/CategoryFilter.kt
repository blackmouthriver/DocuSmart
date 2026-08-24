package com.docsmart.features.library.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.DocuSmartFilterChip
import com.docsmart.core.ui.components.DocumentType
import com.docsmart.core.ui.theme.*

data class CategoryOption(
    val type    : DocumentType?,
    val label   : String,
    val icon    : ImageVector,
    val iconTint: Color
)

private val categories = listOf(
    CategoryOption(null,                    "Todos",       Icons.Rounded.GridView,      Color(0xFF64748B)),
    CategoryOption(DocumentType.PDF,        "PDF",         Icons.Rounded.PictureAsPdf, ColorPdf),
    CategoryOption(DocumentType.WORD,       "Word",        Icons.Rounded.Description,  ColorWord),
    CategoryOption(DocumentType.EXCEL,      "Excel",       Icons.Rounded.TableChart,   ColorExcel),
    CategoryOption(DocumentType.POWERPOINT, "PowerPoint",  Icons.Rounded.Slideshow,    ColorPowerPoint),
    CategoryOption(DocumentType.IMAGE,      "Imágenes",    Icons.Rounded.Image,        ColorImage),
    CategoryOption(DocumentType.TEXT,       "Texto",       Icons.Rounded.TextSnippet,  ColorText),
    CategoryOption(DocumentType.ZIP,        "ZIP",         Icons.Rounded.FolderZip,    ColorZip),
    CategoryOption(DocumentType.OCR,        "Escaneados",  Icons.Rounded.DocumentScanner, ColorOcr)
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CategoryFilter(
    selectedCategory  : DocumentType?,
    onCategorySelected: (DocumentType?) -> Unit,
    modifier          : Modifier = Modifier
) {
    FlowRow(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            DocuSmartFilterChip(
                label      = category.label,
                selected   = selectedCategory == category.type,
                onSelected = { onCategorySelected(category.type) },
                leadingIcon = category.icon,
                iconTint   = category.iconTint
            )
        }
    }
}