package com.docsmart.core.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DailyLimitDialogTest {
    @Test
    fun `dailyLimitProgress es la fraccion usada`() {
        assertEquals(0.6f, dailyLimitProgress(3, 5), 0.0001f)
    }

    @Test
    fun `dailyLimitProgress no pasa de 1 si el contador supera el limite`() {
        assertEquals(1f, dailyLimitProgress(9, 5), 0f)
    }

    @Test
    fun `dailyLimitProgress no baja de 0`() {
        assertEquals(0f, dailyLimitProgress(-2, 5), 0f)
    }

    @Test
    fun `dailyLimitProgress con limite cero no produce NaN`() {
        assertEquals(1f, dailyLimitProgress(0, 0), 0f)
    }
}
