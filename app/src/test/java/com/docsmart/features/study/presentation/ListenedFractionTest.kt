package com.docsmart.features.study.presentation

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ListenedFractionTest {
    @Test
    fun `sin paginas no hay avance`() {
        listenedFraction(currentPage = 1, totalPages = 0, readingFinished = false) shouldBe 0f
    }

    @Test
    fun `en la primera pagina aun no se ha escuchado nada`() {
        listenedFraction(currentPage = 1, totalPages = 1, readingFinished = false) shouldBe 0f
        listenedFraction(currentPage = 1, totalPages = 531, readingFinished = false) shouldBe 0f
    }

    @Test
    fun `cuenta solo las paginas anteriores a la actual`() {
        listenedFraction(currentPage = 3, totalPages = 4, readingFinished = false) shouldBe 0.5f
    }

    @Test
    fun `al terminar la lectura es completo`() {
        listenedFraction(currentPage = 1, totalPages = 1, readingFinished = true) shouldBe 1f
    }

    @Test
    fun `una pagina fuera de rango se acota`() {
        listenedFraction(currentPage = 0, totalPages = 5, readingFinished = false) shouldBe 0f
        listenedFraction(currentPage = 9, totalPages = 5, readingFinished = false) shouldBe 1f
    }
}
