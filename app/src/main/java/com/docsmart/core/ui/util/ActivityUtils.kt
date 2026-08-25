package com.docsmart.core.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

// LocalContext.current en Compose puede venir envuelto (ContextWrapper) en
// vez de ser el Activity directamente — este es el único desenvuelto seguro,
// en vez de castear LocalContext a Activity sin verificar (hallazgo ya
// señalado en sentinel_report.json sobre ConverterScreen.kt).
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
