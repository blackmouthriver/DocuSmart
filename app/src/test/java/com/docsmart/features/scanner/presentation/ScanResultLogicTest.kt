package com.docsmart.features.scanner.presentation

import com.docsmart.features.converter.domain.model.ConversionType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScanResultLogicTest {
    @Test
    fun `mimeTypeForExtension reconoce las extensiones de exportacion`() {
        mimeTypeForExtension("pdf") shouldBe "application/pdf"
        mimeTypeForExtension("jpg") shouldBe "image/jpeg"
        mimeTypeForExtension("jpeg") shouldBe "image/jpeg"
        mimeTypeForExtension("webp") shouldBe "image/webp"
        mimeTypeForExtension("png") shouldBe "image/png"
    }

    @Test
    fun `mimeTypeForExtension ignora mayusculas`() {
        mimeTypeForExtension("PDF") shouldBe "application/pdf"
        mimeTypeForExtension("JpG") shouldBe "image/jpeg"
    }

    @Test
    fun `mimeTypeForExtension desconocida usa octet-stream`() {
        mimeTypeForExtension("xyz") shouldBe "application/octet-stream"
        mimeTypeForExtension("") shouldBe "application/octet-stream"
    }

    @Test
    fun `cada formato de exportacion es consistente con su mime`() {
        ScanExportFormat.entries.forEach { format ->
            mimeTypeForExtension(format.extension) shouldBe format.mimeType
        }
    }

    @Test
    fun `toImageConversionType solo es nulo para PDF`() {
        ScanExportFormat.PDF.toImageConversionType() shouldBe null
        ScanExportFormat.JPG.toImageConversionType() shouldBe ConversionType.IMAGE_TO_JPG
        ScanExportFormat.WEBP.toImageConversionType() shouldBe ConversionType.IMAGE_TO_WEBP
    }

    @Test
    fun `displayToInternal mapea 0-100 a -100-100 con 50 neutro`() {
        displayToInternal(0f) shouldBe -100
        displayToInternal(50f) shouldBe 0
        displayToInternal(100f) shouldBe 100
        displayToInternal(75f) shouldBe 50
    }

    @Test
    fun `displayToInternal redondea los valores intermedios`() {
        displayToInternal(50.4f) shouldBe 1
        displayToInternal(49.6f) shouldBe -1
    }

    @Test
    fun `resolveScanOutputName usa el nombre saneado si no esta vacio`() {
        resolveScanOutputName("Contrato", "Escaneo_%s", "20260919_101500") shouldBe "Contrato"
    }

    @Test
    fun `resolveScanOutputName cae a la plantilla con timestamp si el nombre queda vacio`() {
        resolveScanOutputName("", "Escaneo_%s", "20260919_101500") shouldBe "Escaneo_20260919_101500"
        resolveScanOutputName("   ", "Escaneo_%s", "20260919_101500") shouldBe "Escaneo_20260919_101500"
    }
}
