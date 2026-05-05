package com.docsmart.features.library.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.docsmart.core.ui.components.DocuSmartFilterChip
import com.docsmart.core.ui.components.DocumentType

data class CategoryOption(
    val type: DocumentType?,
    val label: String
)

private val categories = listOf(
    CategoryOption(null,                    "Todos"),
    CategoryOption(DocumentType.PDF,        "PDF"),
    CategoryOption(DocumentType.WORD,       "Word"),
    CategoryOption(DocumentType.EXCEL,      "Excel"),
    CategoryOption(DocumentType.POWERPOINT, "PowerPoint"),
    CategoryOption(DocumentType.IMAGE,      "Imágenes"),
    CategoryOption(DocumentType.TEXT,       "Texto"),
    CategoryOption(DocumentType.ZIP,        "ZIP"),
    CategoryOption(DocumentType.OCR,        "Escaneados")
)

@Composable
fun CategoryFilter(
    selectedCategory: DocumentType?,
    onCategorySelected: (DocumentType?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category.type

            DocuSmartFilterChip(
                label = category.label,
                selected = isSelected,
                onSelected = { onCategorySelected(category.type) }
            )
        }
    }
}