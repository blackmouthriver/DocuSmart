package com.docsmart.features.converter.presentation.components

import com.docsmart.features.converter.domain.model.BatchConversionItem
import com.docsmart.features.converter.domain.model.ConversionResult
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class ConversionResultLogicTest {
    private lateinit var dir: File

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("docsmart_convresult_").toFile()
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun success(
        name: String,
        extra: List<File> = emptyList(),
    ) = ConversionResult.Success(File(dir, name), pageCount = 1, fileSizeKb = 1, extraFiles = extra)

    // ── Familia de formato por extension ────────────────────────────────────

    @Test
    fun `outputFileKindForExtension reconoce cada familia`() {
        outputFileKindForExtension("pdf") shouldBe OutputFileKind.PDF
        listOf("jpg", "jpeg", "png", "webp", "bmp").forEach {
            outputFileKindForExtension(it) shouldBe OutputFileKind.IMAGE
        }
        listOf("doc", "docx").forEach { outputFileKindForExtension(it) shouldBe OutputFileKind.WORD }
        listOf("xls", "xlsx", "csv").forEach { outputFileKindForExtension(it) shouldBe OutputFileKind.EXCEL }
        listOf("ppt", "pptx").forEach { outputFileKindForExtension(it) shouldBe OutputFileKind.POWERPOINT }
        outputFileKindForExtension("txt") shouldBe OutputFileKind.TEXT
        outputFileKindForExtension("html") shouldBe OutputFileKind.HTML
    }

    @Test
    fun `outputFileKindForExtension ignora mayusculas`() {
        outputFileKindForExtension("PDF") shouldBe OutputFileKind.PDF
        outputFileKindForExtension("JpG") shouldBe OutputFileKind.IMAGE
        outputFileKindForExtension("HTML") shouldBe OutputFileKind.HTML
    }

    @Test
    fun `outputFileKindForExtension manda lo desconocido o vacio a OTHER`() {
        outputFileKindForExtension("") shouldBe OutputFileKind.OTHER
        outputFileKindForExtension("zip") shouldBe OutputFileKind.OTHER
        outputFileKindForExtension("htm") shouldBe OutputFileKind.OTHER
    }

    // ── Compartir ───────────────────────────────────────────────────────────

    @Test
    fun `filesToShare devuelve solo el principal cuando no hay extras`() {
        val result = success("a.pdf")

        filesToShare(result) shouldBe listOf(result.outputFile)
    }

    @Test
    fun `filesToShare pone el principal primero y despues las paginas extra`() {
        val extra = listOf(File(dir, "p2.jpg"), File(dir, "p3.jpg"))
        val result = success("p1.jpg", extra)

        filesToShare(result) shouldBe listOf(result.outputFile) + extra
    }

    @Test
    fun `shouldShareMultiple solo con mas de un archivo`() {
        shouldShareMultiple(listOf(File("a"))) shouldBe false
        shouldShareMultiple(listOf(File("a"), File("b"))) shouldBe true
        shouldShareMultiple(emptyList()) shouldBe false
    }

    @Test
    fun `existingFilesOnly descarta los archivos que ya no existen`() {
        val present = File(dir, "existe.txt").apply { writeText("x") }
        val missing = File(dir, "no_existe.txt")

        existingFilesOnly(listOf(missing, present)) shouldBe listOf(present)
        existingFilesOnly(listOf(missing)) shouldBe emptyList()
    }

    // ── Lote ────────────────────────────────────────────────────────────────

    @Test
    fun `batchSuccessCount cuenta solo los exitos`() {
        val items =
            listOf(
                BatchConversionItem("a.docx", success("a.pdf")),
                BatchConversionItem("b.docx", ConversionResult.Error("corrupto")),
                BatchConversionItem("c.docx", success("c.pdf")),
                BatchConversionItem("d.docx", ConversionResult.Loading),
            )

        batchSuccessCount(items) shouldBe 2
        batchSuccessCount(emptyList()) shouldBe 0
    }

    @Test
    fun `batchRowSubtitle muestra el archivo de salida el error o nada`() {
        batchRowSubtitle(success("salida.pdf")) shouldBe "salida.pdf"
        batchRowSubtitle(ConversionResult.Error("archivo corrupto")) shouldBe "archivo corrupto"
        batchRowSubtitle(ConversionResult.Loading) shouldBe ""
    }

    @Test
    fun `canSaveBatch exige que no este guardado y haya al menos un exito`() {
        canSaveBatch(savedToDownloads = false, successCount = 2) shouldBe true
        canSaveBatch(savedToDownloads = false, successCount = 0) shouldBe false
        canSaveBatch(savedToDownloads = true, successCount = 2) shouldBe false
    }
}
