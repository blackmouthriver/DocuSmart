package com.docsmart.features.pdftools.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.cards.DocuSmartToolCard
import com.docsmart.core.ui.theme.*
import com.docsmart.features.pdftools.presentation.PdfTool

data class PdfToolItem(
    val tool: PdfTool,
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
    val color: Color
)

private val toolItems = listOf(
    PdfToolItem(
        tool = PdfTool.MERGE,
        icon = Icons.Rounded.MergeType,
        titleRes = R.string.pdf_merge,
        descriptionRes = R.string.pdf_merge_desc,
        color = DocuBlue
    ),
    PdfToolItem(
        tool = PdfTool.SPLIT,
        icon = Icons.Rounded.CallSplit,
        titleRes = R.string.pdf_split,
        descriptionRes = R.string.pdf_split_desc,
        color = InfoCyan
    ),
    PdfToolItem(
        tool = PdfTool.COMPRESS,
        icon = Icons.Rounded.Compress,
        titleRes = R.string.pdf_compress,
        descriptionRes = R.string.pdf_compress_desc,
        color = SuccessGreen
    ),
    PdfToolItem(
        tool = PdfTool.ROTATE,
        icon = Icons.Rounded.RotateRight,
        titleRes = R.string.pdf_rotate,
        descriptionRes = R.string.pdf_rotate_desc,
        color = WarningAmber
    ),
    PdfToolItem(
        tool = PdfTool.NUMBER_PAGES,
        icon = Icons.Rounded.FormatListNumbered,
        titleRes = R.string.pdf_number_pages,
        descriptionRes = R.string.pdf_number_pages_desc,
        color = IndigoAccent
    ),
    PdfToolItem(
        tool = PdfTool.WATERMARK,
        icon = Icons.Rounded.BrandingWatermark,
        titleRes = R.string.pdf_watermark,
        descriptionRes = R.string.pdf_watermark_desc,
        color = ColorImage
    ),
    PdfToolItem(
        tool = PdfTool.REORDER_PAGES,
        icon = Icons.Rounded.Reorder,
        titleRes = R.string.pdf_reorder_pages,
        descriptionRes = R.string.pdf_reorder_pages_desc,
        color = SlateGray
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
                title = stringResource(item.titleRes),
                description = stringResource(item.descriptionRes),
                onClick = { onToolSelected(item.tool) },
                iconTint = item.color
            )
        }
    }
}