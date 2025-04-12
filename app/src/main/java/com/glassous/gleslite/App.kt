package com.glassous.gleslite

import android.app.Application
import java.lang.ref.WeakReference

class App : Application() {
    var mainActivity: WeakReference<MainActivity>? = null
}