package com.docsmart.features.study.presentation

import androidx.lifecycle.ViewModel
import com.docsmart.core.ads.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * `StudyScreen` no tenía ningún ViewModel (todo su estado es `remember`/
 * objetos singleton como `PomodoroEngine`) -- se agrega este, mínimo, solo
 * para exponer `adManager` y poder mostrar el banner de anuncios con el
 * mismo patrón que el resto de pantallas de contenido (backlog UX
 * 2026-08-30, HU-UX-07).
 */
@HiltViewModel
class StudyViewModel @Inject constructor(
    val adManager: AdManager
) : ViewModel()
