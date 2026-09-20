package com.docsmart.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.docsmart.R
import com.docsmart.features.security.presentation.esText
import com.docsmart.features.security.presentation.setContentEsScaled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DailyLimitDialogTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var watchAd = 0
    private var dismissed = 0
    private var premium = 0

    private fun show(rewardedReady: Boolean) {
        composeRule.setContentEsScaled {
            DailyLimitDialog(
                usedCount = 3,
                limit = 3,
                itemLabelPlural = "conversiones",
                isRewardedReady = rewardedReady,
                onWatchAd = { watchAd++ },
                onDismiss = { dismissed++ },
                onGetPremium = { premium++ },
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun conAnuncioListo_verAnuncioDisparaElCallback() {
        show(rewardedReady = true)
        composeRule.onNodeWithText(esText(R.string.daily_limit_title)).assertExists()
        composeRule.onNodeWithText(esText(R.string.daily_limit_body, 3, 3, "conversiones")).assertExists()
        composeRule.onNodeWithText(esText(R.string.daily_limit_resets_tomorrow)).assertExists()
        composeRule.onNodeWithText(esText(R.string.daily_limit_watch_ad)).assertExists().assertIsEnabled()
        composeRule.onNodeWithText(esText(R.string.daily_limit_watch_ad)).performClick()
        assertEquals(1, watchAd)
    }

    @Test
    fun sinAnuncioListo_elBotonEstaDeshabilitado() {
        show(rewardedReady = false)
        composeRule.onNodeWithText(esText(R.string.daily_limit_ad_not_ready)).assertExists()
        composeRule.onNodeWithText(esText(R.string.daily_limit_ad_not_ready)).assertIsNotEnabled()
        assertEquals(0, watchAd)
    }

    @Test
    fun premiumYCancelar_disparanSusCallbacks() {
        show(rewardedReady = true)
        composeRule.onNodeWithText(esText(R.string.daily_limit_get_premium)).performClick()
        assertEquals(1, premium)
        composeRule.onNodeWithText(esText(R.string.general_cancel)).performClick()
        assertTrue(dismissed >= 1)
    }
}
