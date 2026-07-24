package com.jeager22.nonton.proxy

import android.content.Context

/** Compatibility facade for future expansion and UI naming. */
class ProxyManager(context: Context) {
    val engine = SmartProxyEngine(context.applicationContext)
}
