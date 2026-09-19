package com.docsmart.features.converter.domain.usecase

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Ronda 17: geometría pura de ConvertImageToPdfUseCase (A4 en puntos, margen de
 * 20): dónde se dibuja la imagen y a cuántos píxeles se incrusta.
 */
class ImagePageLayoutTest {
    private val pageW = 595
    private val pageH = 842
    private val margin = 20

    private fun rect(
        w: Int,
        h: Int,
    ) = computePageDrawRect(w, h, pageW, pageH, margin)

    private fun close(
        a: Float,
        b: Float,
    ) = abs(a - b) < 0.01f

    @Test
    fun `una imagen chica no se agranda y queda centrada`() {
        val r = rect(100, 200)

        r.width shouldBe 100f
        r.height shouldBe 200f
        r.left shouldBe (595 - 100) / 2f
        r.top shouldBe (842 - 200) / 2f
    }

    @Test
    fun `una imagen ancha se limita por el ancho util de la pagina`() {
        val r = rect(2000, 1000)

        // ancho util = 595 - 40 = 555
        close(r.width, 555f) shouldBe true
        close(r.height, 277.5f) shouldBe true
    }

    @Test
    fun `una imagen alta se limita por el alto util de la pagina`() {
        val r = rect(1000, 4000)

        // alto util = 842 - 40 = 802
        close(r.height, 802f) shouldBe true
        close(r.width, 200.5f) shouldBe true
    }

    @Test
    fun `el recuadro conserva la proporcion de la imagen`() {
        val r = rect(3000, 2000)

        close(r.width / r.height, 1.5f) shouldBe true
    }

    @Test
    fun `el recuadro siempre queda dentro de los margenes de la pagina`() {
        listOf(1 to 1, 5000 to 5000, 4000 to 100, 100 to 4000, 555 to 802).forEach { (w, h) ->
            val r = rect(w, h)
            (r.left >= margin - 0.01f) shouldBe true
            (r.top >= margin - 0.01f) shouldBe true
            (r.right <= pageW - margin + 0.01f) shouldBe true
            (r.bottom <= pageH - margin + 0.01f) shouldBe true
        }
    }

    @Test
    fun `una imagen justo del tamano util no se reescala`() {
        val r = rect(555, 802)

        close(r.width, 555f) shouldBe true
        close(r.height, 802f) shouldBe true
    }

    @Test
    fun `embedTargetSize multiplica los puntos por los pixeles por punto`() {
        embedTargetSize(100f, 200f, 3) shouldBe (300 to 600)
        embedTargetSize(100f, 200f, 4) shouldBe (400 to 800)
    }

    @Test
    fun `embedTargetSize redondea y nunca baja de un pixel`() {
        embedTargetSize(100.4f, 50.6f, 1) shouldBe (100 to 51)
        embedTargetSize(0f, 0f, 3) shouldBe (1 to 1)
    }

    @Test
    fun `alta resolucion incrusta mas pixeles que la estandar`() {
        val r = rect(4000, 3000)
        val estandar = embedTargetSize(r.width, r.height, 3)
        val alta = embedTargetSize(r.width, r.height, 4)

        (alta.first > estandar.first) shouldBe true
        (alta.second > estandar.second) shouldBe true
    }

    @Test
    fun `needsDownscale solo reescala cuando la imagen excede el objetivo`() {
        needsDownscale(1000, 1000, 500, 500) shouldBe true
        needsDownscale(1000, 400, 500, 500) shouldBe true
        needsDownscale(400, 1000, 500, 500) shouldBe true
        needsDownscale(500, 500, 500, 500) shouldBe false
        needsDownscale(100, 100, 500, 500) shouldBe false
    }
}
