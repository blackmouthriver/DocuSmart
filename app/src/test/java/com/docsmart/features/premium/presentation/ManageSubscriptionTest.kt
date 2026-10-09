package com.docsmart.features.premium.presentation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManageSubscriptionTest {
    @Test
    fun `el enlace apunta al centro de suscripciones de Google Play por https`() {
        assertEquals("https://play.google.com/store/account/subscriptions", PLAY_SUBSCRIPTIONS_URL)
    }
}
