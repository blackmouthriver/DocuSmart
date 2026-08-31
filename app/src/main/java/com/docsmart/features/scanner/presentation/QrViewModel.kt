package com.docsmart.features.scanner.presentation

import androidx.lifecycle.ViewModel
import com.docsmart.core.ads.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * `QrReaderScreen`/`QrCreatorScreen` no tenían ningún ViewModel (todo su
 * estado es `remember`) -- se agrega este, mínimo, solo para exponer
 * `adManager` y poder mostrar el banner de anuncios con el mismo patrón
 * que el resto de pantallas de contenido (backlog UX 2026-08-30, HU-UX-07).
 * Compartido entre las dos pantallas porque ninguna necesita más que esto;
 * cada una recibe su propia instancia (scope por NavBackStackEntry).
 */
@HiltViewModel
class QrViewModel @Inject constructor(
    val adManager: AdManager
) : ViewModel()
