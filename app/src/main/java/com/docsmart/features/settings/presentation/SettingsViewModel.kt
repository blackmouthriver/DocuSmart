package com.docsmart.features.settings.presentation
import androidx.lifecycle.ViewModel
import com.docsmart.core.ads.AdManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
@HiltViewModel
class SettingsViewModel @Inject constructor(
    val adManager: AdManager
) : ViewModel()
