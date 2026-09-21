package com.docsmart.features.pdftools.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.docsmart.R
import com.docsmart.core.ui.components.cards.DocuSmartToolTile
import com.docsmart.core.ui.theme.*
import com.docsmart.features.pdftools.presentation.PdfTool

data class PdfToolItem(
    val tool: PdfTool,
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
    val color: Color,
)

private val toolItems =
    listOf(
        PdfToolItem(
            tool = PdfTool.MERGE,
            icon = Icons.Rounded.MergeType,
            titleRes = R.string.pdf_merge,
            descriptionRes = R.string.pdf_merge_desc,
            color = DocuBlue,
        ),
        PdfToolItem(
            tool = PdfTool.SPLIT,
            icon = Icons.Rounded.CallSplit,
            titleRes = R.string.pdf_split,
            descriptionRes = R.string.pdf_split_desc,
            color = InfoCyan,
        ),
        PdfToolItem(
            tool = PdfTool.COMPRESS,
            icon = Icons.Rounded.Compress,
            titleRes = R.string.pdf_compress,
            descriptionRes = R.string.pdf_compress_desc,
            color = SuccessGreen,
        ),
        PdfToolItem(
            tool = PdfTool.ROTATE,
            icon = Icons.Rounded.RotateRight,
            titleRes = R.string.pdf_rotate,
            descriptionRes = R.string.pdf_rotate_desc,
            color = WarningAmber,
        ),
        PdfToolItem(
            tool = PdfTool.NUMBER_PAGES,
            icon = Icons.Rounded.FormatListNumbered,
            titleRes = R.string.pdf_number_pages,
            descriptionRes = R.string.pdf_number_pages_desc,
            color = IndigoAccent,
        ),
        PdfToolItem(
            tool = PdfTool.WATERMARK,
            icon = Icons.Rounded.BrandingWatermark,
            titleRes = R.string.pdf_watermark,
            descriptionRes = R.string.pdf_watermark_desc,
            color = ColorImage,
        ),
        PdfToolItem(
            tool = PdfTool.REORDER_PAGES,
            icon = Icons.Rounded.Reorder,
            titleRes = R.string.pdf_reorder_pages,
            descriptionRes = R.string.pdf_reorder_pages_desc,
            color = SlateGray,
        ),
        PdfToolItem(
            tool = PdfTool.COMPARE,
            icon = Icons.Rounded.CompareArrows,
            titleRes = R.string.pdf_compare,
            descriptionRes = R.string.pdf_compare_desc,
            color = ColorPowerPoint,
        ),
        PdfToolItem(
            tool = PdfTool.REDACT,
            icon = Icons.Rounded.VisibilityOff,
            titleRes = R.string.pdf_redact,
            descriptionRes = R.string.pdf_redact_desc,
            color = ErrorRed,
        ),
        PdfToolItem(
            tool = PdfTool.CROP,
            icon = Icons.Rounded.Crop,
            titleRes = R.string.pdf_crop,
            descriptionRes = R.string.pdf_crop_desc,
            color = PremiumGold,
        ),
        PdfToolItem(
            tool = PdfTool.EDIT_TEXT,
            icon = Icons.Rounded.Edit,
            titleRes = R.string.pdf_edit_text,
            descriptionRes = R.string.pdf_edit_text_desc,
            color = SmartBlue,
        ),
        PdfToolItem(
            tool = PdfTool.SIGN,
            icon = Icons.Rounded.Draw,
            titleRes = R.string.pdf_sign,
            descriptionRes = R.string.pdf_sign_desc,
            color = NavyDark,
        ),
        PdfToolItem(
            tool = PdfTool.FILL_FORM,
            icon = Icons.Rounded.Checklist,
            titleRes = R.string.pdf_fill_form,
            descriptionRes = R.string.pdf_fill_form_desc,
            color = ColorZip,
        ),
        PdfToolItem(
            tool = PdfTool.OCR,
            icon = Icons.Rounded.FindInPage,
            titleRes = R.string.pdf_ocr,
            descriptionRes = R.string.pdf_ocr_desc,
            color = ColorOcr,
        ),
        PdfToolItem(
            tool = PdfTool.EXTRACT_IMAGES,
            icon = Icons.Rounded.Image,
            titleRes = R.string.pdf_extract_images,
            descriptionRes = R.string.pdf_extract_images_desc,
            color = ColorImage,
        ),
    )

// Rediseño 2026-09-21: las herramientas se agrupan por tarea en secciones con
// título, en vez de una lista plana de 15 tarjetas.
private val toolSections =
    listOf(
        R.string.pdf_tools_section_organize to
            listOf(
                PdfTool.MERGE,
                PdfTool.SPLIT,
                PdfTool.REORDER_PAGES,
                PdfTool.ROTATE,
                PdfTool.CROP,
                PdfTool.NUMBER_PAGES,
            ),
        R.string.pdf_tools_section_edit to
            listOf(PdfTool.EDIT_TEXT, PdfTool.WATERMARK, PdfTool.FILL_FORM),
        R.string.pdf_tools_section_sign_protect to
            listOf(PdfTool.SIGN, PdfTool.REDACT),
        R.string.pdf_tools_section_extract to
            listOf(PdfTool.OCR, PdfTool.EXTRACT_IMAGES, PdfTool.COMPARE, PdfTool.COMPRESS),
    )

private const val TOOL_COLUMNS = 2

@Composable
fun PdfToolsMenu(
    onToolSelected: (PdfTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        toolSections.forEach { (titleRes, tools) ->
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            tools.mapNotNull { tool -> toolItems.firstOrNull { it.tool == tool } }
                .chunked(TOOL_COLUMNS)
                .forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowItems.forEach { item ->
                            DocuSmartToolTile(
                                icon = item.icon,
                                title = stringResource(item.titleRes),
                                description = stringResource(item.descriptionRes),
                                onClick = { onToolSelected(item.tool) },
                                iconTint = item.color,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        repeat(TOOL_COLUMNS - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
        }
    }
}
