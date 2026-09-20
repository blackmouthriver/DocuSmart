package com.docsmart.features.pdftools.domain.usecase

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import java.io.File

/** PDF con una línea de texto real (Helvetica) por página, generado con iText. */
internal fun writeTextPdf(
    file: File,
    pageTexts: List<String>,
): File {
    file.parentFile?.mkdirs()
    PdfDocument(PdfWriter(file)).use { pdf ->
        val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        pageTexts.forEach { text ->
            val page = pdf.addNewPage()
            PdfCanvas(page)
                .beginText()
                .setFontAndSize(font, 18f)
                .moveText(50.0, 700.0)
                .showText(text)
                .endText()
        }
    }
    return file
}

/** Texto de la página [pageNumber] (1-based) de un PDF con texto real. */
internal fun pageText(
    file: File,
    pageNumber: Int = 1,
): String =
    PdfDocument(PdfReader(file)).use { pdf ->
        PdfTextExtractor.getTextFromPage(pdf.getPage(pageNumber))
    }
