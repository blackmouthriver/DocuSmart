package com.docsmart.testutil

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

/**
 * Mapa en memoria que respalda un `SharedPreferences` mockeado -- se
 * devuelve por separado de [fakeContextWithPrefs] para poder pre-poblarlo o
 * inspeccionarlo directamente en un test (por ejemplo, para simular un JSON
 * corrupto ya guardado antes de cargarlo).
 */
fun fakePrefsStore(): MutableMap<String, Any> = mutableMapOf()

/**
 * Context mockeado cuyo `SharedPreferences` queda respaldado por [store] en
 * vez de un `SharedPreferences` real -- extraído de 3 clases de test que
 * repetían el mismo boilerplate de mockear getString/putString/getLong/
 * putLong/edit/apply (`StudyNotesStorageTest`, `StudyStatsStorageTest`,
 * `ThemeManagerTest`), señalado por SonarCloud como duplicación de código.
 */
fun fakeContextWithPrefs(store: MutableMap<String, Any>): Context {
    val editor = mockk<SharedPreferences.Editor>()

    val strKey = slot<String>()
    val strValue = slot<String>()
    every { editor.putString(capture(strKey), capture(strValue)) } answers {
        store[strKey.captured] = strValue.captured
        editor
    }
    val longKey = slot<String>()
    val longValue = slot<Long>()
    every { editor.putLong(capture(longKey), capture(longValue)) } answers {
        store[longKey.captured] = longValue.captured
        editor
    }
    every { editor.apply() } answers { }

    val prefs = mockk<SharedPreferences>()
    val getStrKey = slot<String>()
    val getStrDefault = slot<String>()
    every { prefs.getString(capture(getStrKey), capture(getStrDefault)) } answers {
        (store[getStrKey.captured] as? String) ?: getStrDefault.captured
    }
    val getLongKey = slot<String>()
    val getLongDefault = slot<Long>()
    every { prefs.getLong(capture(getLongKey), capture(getLongDefault)) } answers {
        (store[getLongKey.captured] as? Long) ?: getLongDefault.captured
    }
    every { prefs.edit() } returns editor

    val context = mockk<Context>()
    every { context.getSharedPreferences(any(), any()) } returns prefs
    return context
}
