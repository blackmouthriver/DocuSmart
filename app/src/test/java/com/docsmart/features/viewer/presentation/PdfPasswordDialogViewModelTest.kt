package com.docsmart.features.viewer.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Ronda 18: estado de la contraseña del diálogo del Visor (vive en el ViewModel, no en el Bundle). */
class PdfPasswordDialogViewModelTest {
    @Test
    fun `el estado inicial esta vacio y con la contrasena oculta`() {
        val viewModel = PdfPasswordDialogViewModel()

        assertEquals("", viewModel.password)
        assertFalse(viewModel.showPassword)
    }

    @Test
    fun `onPasswordChange reemplaza la contrasena tecleada`() {
        val viewModel = PdfPasswordDialogViewModel()

        viewModel.onPasswordChange("abc")
        viewModel.onPasswordChange("abcd")

        assertEquals("abcd", viewModel.password)
    }

    @Test
    fun `onToggleShowPassword alterna la visibilidad`() {
        val viewModel = PdfPasswordDialogViewModel()

        viewModel.onToggleShowPassword()
        assertTrue(viewModel.showPassword)

        viewModel.onToggleShowPassword()
        assertFalse(viewModel.showPassword)
    }

    @Test
    fun `clear borra la contrasena y vuelve a ocultarla`() {
        val viewModel = PdfPasswordDialogViewModel()
        viewModel.onPasswordChange("secreto")
        viewModel.onToggleShowPassword()

        viewModel.clear()

        assertEquals("", viewModel.password)
        assertFalse(viewModel.showPassword)
    }
}
