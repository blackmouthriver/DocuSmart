package com.docsmart.features.security.presentation

import androidx.lifecycle.ViewModel
import com.docsmart.core.ads.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * `SecurityMenuScreen` no tenía ViewModel propio (todo su estado es
 * estático/de navegación) -- se agrega este, mínimo, solo para exponer
 * `adManager` y poder mostrar el banner de anuncios con el mismo patrón
 * que el resto de pantallas de contenido (backlog UX 2026-08-30, HU-UX-07).
 */
@HiltViewModel
class SecurityMenuViewModel @Inject constructor(
    val adManager: AdManager
) : ViewModel()
