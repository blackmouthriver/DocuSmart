package com.docsmart.features.viewer.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.docsmart.core.ads.AdManager
import com.docsmart.core.data.FavoritesRepository
import com.docsmart.core.data.db.AnnotationDao
import com.docsmart.core.data.db.AnnotationEntity
import com.docsmart.core.data.db.DocumentHistoryDao
import com.docsmart.core.data.db.LastViewedPageDao
import com.docsmart.core.data.db.LastViewedPageEntity
import com.docsmart.core.data.db.NoteDao
import com.docsmart.core.data.db.NoteWithImages
import com.docsmart.core.data.db.PageBookmarkDao
import com.docsmart.core.data.db.PageBookmarkEntity
import com.docsmart.core.ui.test.forceLocale
import com.docsmart.core.ui.test.testViewportDensity
import com.docsmart.core.ui.test.waitUntilOrDump
import com.docsmart.features.library.data.DocumentRepository
import com.docsmart.features.library.data.TrashRepository
import com.docsmart.features.viewer.domain.usecase.FlattenAnnotationsPdfUseCase
import com.docsmart.features.viewer.domain.usecase.SearchPdfTextUseCase
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.EncryptionConstants
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.WriterProperties
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Utilidades compartidas por las pruebas instrumentadas del Visor (ronda 20):
// idioma fijo en español, ViewModel armado a mano con fakes reactivos y
// documentos reales generados en cacheDir.

internal const val VIEWER_TEST_TIMEOUT_MS = 20_000L

/** Texto esperado, resuelto siempre en español (el emulador de CI arranca en inglés). */
internal fun vs(
    @StringRes id: Int,
    vararg args: Any,
): String = forceLocale(InstrumentationRegistry.getInstrumentation().targetContext, "es-ES").getString(id, *args)

/**
 * Compone [content] con español, densidad ajustada a pantallas chicas y el registro
 * de resultados / back dispatcher de la Activity. [overrideContext] (ya en español)
 * permite inyectar un contexto que graba los intents en vez de lanzarlos.
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.setViewerContent(
    overrideContext: Context? = null,
    content: @Composable () -> Unit,
) {
    setContent {
        val base = LocalContext.current
        val localized = remember(base) { overrideContext ?: forceLocale(base, "es-ES") }
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalResources provides localized.resources,
            LocalDensity provides testViewportDensity(),
            LocalActivityResultRegistryOwner provides activity,
            LocalOnBackPressedDispatcherOwner provides activity,
        ) { content() }
    }
}

internal fun ComposeTestRule.waitForViewerNode(
    matcher: SemanticsMatcher,
    timeoutMillis: Long = VIEWER_TEST_TIMEOUT_MS,
) {
    waitUntilOrDump("CI_HANG_Viewer", timeoutMillis) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
}

internal fun ComposeTestRule.waitForText(
    text: String,
    substring: Boolean = false,
    timeoutMillis: Long = VIEWER_TEST_TIMEOUT_MS,
) {
    waitForViewerNode(hasText(text, substring = substring), timeoutMillis)
}

internal fun ComposeTestRule.waitForState(
    timeoutMillis: Long = VIEWER_TEST_TIMEOUT_MS,
    condition: () -> Boolean,
) {
    waitUntilOrDump("CI_HANG_ViewerState", timeoutMillis, condition)
}

/**
 * ViewerViewModel armado a mano: DAOs con Flow reales (MutableStateFlow) para que la UI
 * reaccione a altas/bajas hechas por el propio ViewModel, sin Room ni Hilt.
 */
internal class ViewerHarness(
    premium: Boolean = true,
    val searchUseCase: SearchPdfTextUseCase = mockk(relaxed = true),
) {
    val favorites: FavoritesRepository = mockk(relaxed = true)
    val documentRepository: DocumentRepository = mockk(relaxed = true)
    val trashRepository: TrashRepository = mockk(relaxed = true)
    val historyDao: DocumentHistoryDao = mockk(relaxed = true)
    val annotationDao: AnnotationDao = mockk(relaxed = true)
    val pageBookmarkDao: PageBookmarkDao = mockk(relaxed = true)
    val lastViewedPageDao: LastViewedPageDao = mockk(relaxed = true)
    val noteDao: NoteDao = mockk(relaxed = true)
    val flatten: FlattenAnnotationsPdfUseCase = mockk(relaxed = true)
    val adManager: AdManager = mockk(relaxed = true)

    val annotations = MutableStateFlow<List<AnnotationEntity>>(emptyList())
    val bookmarks = MutableStateFlow<List<PageBookmarkEntity>>(emptyList())
    val notes = MutableStateFlow<List<NoteWithImages>>(emptyList())

    val viewModel: ViewerViewModel

    init {
        every { favorites.isFavorite(any()) } returns false
        every { favorites.getAlias(any()) } returns null
        coEvery { favorites.toggleFavorite(any()) } returns true
        every { adManager.isPremium } returns MutableStateFlow(premium)
        every { adManager.isInitialized } returns MutableStateFlow(false)

        every { annotationDao.observeByDocument(any()) } returns annotations
        coEvery { annotationDao.insert(any()) } answers {
            val entry = firstArg<AnnotationEntity>()
            annotations.update { it + entry }
        }
        coEvery { annotationDao.delete(any()) } answers {
            val id = firstArg<String>()
            annotations.update { list -> list.filter { it.id != id } }
        }

        every { pageBookmarkDao.observeByDocument(any()) } returns bookmarks
        coEvery { pageBookmarkDao.insert(any()) } answers {
            val entry = firstArg<PageBookmarkEntity>()
            bookmarks.update { it + entry }
        }
        coEvery { pageBookmarkDao.delete(any(), any()) } answers {
            val page = secondArg<Int>()
            bookmarks.update { list -> list.filter { it.page != page } }
        }

        every { noteDao.observeByDocument(any()) } returns notes
        coEvery { lastViewedPageDao.getByDocument(any()) } returns null

        viewModel =
            ViewerViewModel(
                favoritesRepository = favorites,
                searchPdfText = searchUseCase,
                documentHistoryDao = historyDao,
                documentRepository = documentRepository,
                trashRepository = trashRepository,
                adManager = adManager,
                annotationDao = annotationDao,
                flattenAnnotationsPdfUseCase = flatten,
                pageBookmarkDao = pageBookmarkDao,
                lastViewedPageDao = lastViewedPageDao,
                noteDao = noteDao,
            )
    }

    fun lastViewedPage(
        documentId: String,
        page: Int,
    ) {
        coEvery { lastViewedPageDao.getByDocument(any()) } returns LastViewedPageEntity(documentId, page, 0L)
    }
}

/** Documentos reales de prueba en cacheDir (nombres únicos); [deleteAll] los borra al terminar. */
internal class ViewerTestFiles {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val created = mutableListOf<File>()
    private var counter = 0

    /** Carpeta de cacheDir (o subcarpeta, p. ej. "scanner", que sí cubre FileProvider). */
    fun dir(sub: String? = null): File {
        val dir = if (sub == null) context.cacheDir else File(context.cacheDir, sub)
        dir.mkdirs()
        return dir
    }

    private fun newFile(
        suffix: String,
        sub: String? = null,
    ): File {
        counter++
        val file = File(dir(sub), "viewer_r20_${System.nanoTime()}_$counter$suffix")
        created += file
        return file
    }

    fun bytes(
        suffix: String,
        content: ByteArray,
        sub: String? = null,
    ): File = newFile(suffix, sub).also { it.writeBytes(content) }

    fun text(
        suffix: String,
        content: String,
        sub: String? = null,
    ): File = bytes(suffix, content.toByteArray(Charsets.UTF_8), sub)

    /** PDF real de [pages] páginas de 300x400 pt con texto; opcionalmente cifrado (RC4 128) o rotado. */
    fun pdf(
        pages: Int = 1,
        sub: String? = null,
        password: String? = null,
        rotation: Int = 0,
        text: String = "Contenido de prueba",
    ): File {
        val file = newFile(".pdf", sub)
        val props = WriterProperties()
        if (password != null) {
            props.setStandardEncryption(
                password.toByteArray(),
                "propietario-r20".toByteArray(),
                EncryptionConstants.ALLOW_PRINTING,
                EncryptionConstants.STANDARD_ENCRYPTION_128,
            )
        }
        val pdf = PdfDocument(PdfWriter(file.absolutePath, props))
        val document = Document(pdf, PageSize(300f, 400f))
        repeat(pages) { index ->
            if (index > 0) document.add(AreaBreak())
            document.add(Paragraph("$text pagina ${index + 1}"))
        }
        document.close()
        if (rotation != 0) applyRotation(file, rotation)
        return file
    }

    // Reescribe el PDF con /Rotate en cada página (misma ruta, archivo nuevo).
    private fun applyRotation(
        file: File,
        rotation: Int,
    ) {
        val rotated = File(file.parentFile, "rot_${file.name}")
        created += rotated
        PdfReader(file).use { reader ->
            PdfDocument(reader, PdfWriter(rotated)).use { doc ->
                for (i in 1..doc.numberOfPages) doc.getPage(i).setRotation(rotation)
            }
        }
        rotated.copyTo(file, overwrite = true)
    }

    /** Archivo dentro de filesDir/viewer_share (carpeta que FileProvider sí comparte), para el PDF "aplanado". */
    fun sharedFile(): File {
        val dir = File(context.filesDir, "viewer_share").apply { mkdirs() }
        counter++
        val file = File(dir, "viewer_r20_${System.nanoTime()}_$counter.pdf")
        file.writeBytes(byteArrayOf(1, 2, 3))
        created += file
        return file
    }

    fun png(sub: String? = null): File = bitmapFile(".png", Bitmap.CompressFormat.PNG, sub)

    fun jpeg(sub: String? = null): File = bitmapFile(".jpg", Bitmap.CompressFormat.JPEG, sub)

    private fun bitmapFile(
        suffix: String,
        format: Bitmap.CompressFormat,
        sub: String?,
    ): File {
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val out = ByteArrayOutputStream()
        bitmap.compress(format, 90, out)
        bitmap.recycle()
        return bytes(suffix, out.toByteArray(), sub)
    }

    /** .docx mínimo hecho a mano: un encabezado, un párrafo con formato y una tabla de 2x2. */
    fun docx(): File = bytes(".docx", zip(DOCX_PARTS))

    /** .xlsx mínimo hecho a mano con dos hojas (Resumen y Ventas). */
    fun xlsx(): File = bytes(".xlsx", zip(XLSX_PARTS))

    /** .pptx mínimo hecho a mano con una diapositiva (título + cuerpo). */
    fun pptx(): File = bytes(".pptx", zip(PPTX_PARTS))

    fun deleteAll() {
        created.forEach { runCatching { it.delete() } }
        created.clear()
    }

    private fun zip(parts: List<Pair<String, String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            parts.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}

private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
private const val NS_CT = "http://schemas.openxmlformats.org/package/2006/content-types"
private const val NS_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val REL_DOC = "$NS_R/officeDocument"

private fun contentTypes(overrides: List<Pair<String, String>>): String =
    XML_HEAD + "<Types xmlns=\"$NS_CT\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        overrides.joinToString("") { (part, type) -> "<Override PartName=\"$part\" ContentType=\"$type\"/>" } +
        "</Types>"

private fun rels(vararg items: Triple<String, String, String>): String =
    XML_HEAD + "<Relationships xmlns=\"$NS_REL\">" +
        items.joinToString("") { (id, type, target) ->
            "<Relationship Id=\"$id\" Type=\"$type\" Target=\"$target\"/>"
        } +
        "</Relationships>"

private const val WML = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val CT_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"

private fun wordCell(text: String) = "<w:tc><w:p><w:r><w:t>$text</w:t></w:r></w:p></w:tc>"

private val DOCX_PARTS =
    listOf(
        "[Content_Types].xml" to contentTypes(listOf("/word/document.xml" to CT_DOCX)),
        "_rels/.rels" to rels(Triple("rId1", REL_DOC, "word/document.xml")),
        "word/document.xml" to
            XML_HEAD + "<w:document xmlns:w=\"$WML\"><w:body>" +
            "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Titulo del informe</w:t></w:r></w:p>" +
            "<w:p><w:r><w:rPr><w:b/><w:i/><w:sz w:val=\"28\"/></w:rPr><w:t>Parrafo con clausula uno</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Segundo parrafo sin nada especial</w:t></w:r></w:p>" +
            "<w:tbl><w:tr>" + wordCell("Concepto") + wordCell("Monto") + "</w:tr>" +
            "<w:tr>" + wordCell("Servicio") + wordCell("1500") + "</w:tr></w:tbl>" +
            "<w:sectPr/></w:body></w:document>",
    )

private const val SML = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
private const val CT_XLSX_BOOK = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"
private const val CT_XLSX_SHEET = "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"

private fun excelCell(
    ref: String,
    text: String,
) = "<c r=\"$ref\" t=\"inlineStr\"><is><t>$text</t></is></c>"

private fun excelSheet(rows: List<List<String>>): String =
    XML_HEAD + "<worksheet xmlns=\"$SML\"><sheetData>" +
        rows.mapIndexed { r, cells ->
            "<row r=\"${r + 1}\">" +
                cells.mapIndexed { c, value -> excelCell("${'A' + c}${r + 1}", value) }.joinToString("") +
                "</row>"
        }.joinToString("") +
        "</sheetData></worksheet>"

private val XLSX_PARTS =
    listOf(
        "[Content_Types].xml" to
            contentTypes(
                listOf(
                    "/xl/workbook.xml" to CT_XLSX_BOOK,
                    "/xl/worksheets/sheet1.xml" to CT_XLSX_SHEET,
                    "/xl/worksheets/sheet2.xml" to CT_XLSX_SHEET,
                ),
            ),
        "_rels/.rels" to rels(Triple("rId1", REL_DOC, "xl/workbook.xml")),
        "xl/workbook.xml" to
            XML_HEAD + "<workbook xmlns=\"$SML\" xmlns:r=\"$NS_R\"><sheets>" +
            "<sheet name=\"Resumen\" sheetId=\"1\" r:id=\"rId1\"/>" +
            "<sheet name=\"Ventas\" sheetId=\"2\" r:id=\"rId2\"/></sheets></workbook>",
        "xl/_rels/workbook.xml.rels" to
            rels(
                Triple("rId1", "$NS_R/worksheet", "worksheets/sheet1.xml"),
                Triple("rId2", "$NS_R/worksheet", "worksheets/sheet2.xml"),
            ),
        "xl/worksheets/sheet1.xml" to
            excelSheet(listOf(listOf("Producto", "Detalle"), listOf("Lapiz", "Azul"), listOf("Cuaderno", "Rayado"))),
        "xl/worksheets/sheet2.xml" to
            excelSheet(listOf(listOf("Mes", "Total"), listOf("Enero", "TotalEnero"), listOf("Febrero", "Otro"))),
    )

private const val DML = "http://schemas.openxmlformats.org/drawingml/2006/main"
private const val PML = "http://schemas.openxmlformats.org/presentationml/2006/main"
private const val CT_PPT = "application/vnd.openxmlformats-officedocument.presentationml"
private const val PPT_NS = "xmlns:a=\"$DML\" xmlns:r=\"$NS_R\" xmlns:p=\"$PML\""
private const val PPT_GROUP =
    "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>"

private fun pptShape(
    id: Int,
    placeholder: String,
    text: String,
) = "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"Forma $id\"/><p:cNvSpPr/><p:nvPr>$placeholder</p:nvPr></p:nvSpPr>" +
    "<p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"es-ES\"/><a:t>$text</a:t></a:r></a:p>" +
    "</p:txBody></p:sp>"

private val PPTX_PARTS =
    listOf(
        "[Content_Types].xml" to
            contentTypes(
                listOf(
                    "/ppt/presentation.xml" to "$CT_PPT.presentation.main+xml",
                    "/ppt/slides/slide1.xml" to "$CT_PPT.slide+xml",
                    "/ppt/slideLayouts/slideLayout1.xml" to "$CT_PPT.slideLayout+xml",
                    "/ppt/slideMasters/slideMaster1.xml" to "$CT_PPT.slideMaster+xml",
                ),
            ),
        "_rels/.rels" to rels(Triple("rId1", REL_DOC, "ppt/presentation.xml")),
        "ppt/presentation.xml" to
            XML_HEAD + "<p:presentation $PPT_NS><p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/>" +
            "</p:sldMasterIdLst><p:sldIdLst><p:sldId id=\"256\" r:id=\"rId2\"/></p:sldIdLst>" +
            "<p:sldSz cx=\"9144000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>",
        "ppt/_rels/presentation.xml.rels" to
            rels(
                Triple("rId1", "$NS_R/slideMaster", "slideMasters/slideMaster1.xml"),
                Triple("rId2", "$NS_R/slide", "slides/slide1.xml"),
            ),
        "ppt/slides/slide1.xml" to
            XML_HEAD + "<p:sld $PPT_NS><p:cSld><p:spTree>$PPT_GROUP" +
            pptShape(2, "<p:ph type=\"title\"/>", "Plan trimestral") +
            pptShape(3, "", "Punto uno alfa") +
            "</p:spTree></p:cSld></p:sld>",
        "ppt/slides/_rels/slide1.xml.rels" to
            rels(Triple("rId1", "$NS_R/slideLayout", "../slideLayouts/slideLayout1.xml")),
        "ppt/slideLayouts/slideLayout1.xml" to
            XML_HEAD + "<p:sldLayout $PPT_NS><p:cSld name=\"Titulo\"><p:spTree>$PPT_GROUP</p:spTree>" +
            "</p:cSld></p:sldLayout>",
        "ppt/slideLayouts/_rels/slideLayout1.xml.rels" to
            rels(Triple("rId1", "$NS_R/slideMaster", "../slideMasters/slideMaster1.xml")),
        "ppt/slideMasters/slideMaster1.xml" to
            XML_HEAD + "<p:sldMaster $PPT_NS><p:cSld><p:spTree>$PPT_GROUP</p:spTree></p:cSld>" +
            "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" " +
            "accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" " +
            "folHlink=\"folHlink\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/>" +
            "</p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>",
        "ppt/slideMasters/_rels/slideMaster1.xml.rels" to
            rels(Triple("rId1", "$NS_R/slideLayout", "../slideLayouts/slideLayout1.xml")),
    )
