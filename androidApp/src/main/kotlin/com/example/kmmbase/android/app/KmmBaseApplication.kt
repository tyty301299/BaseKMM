package com.example.kmmbase.android.app

import android.app.Application
import com.example.kmmbase.di.AppContainer

// App scope: the container survives Activity recreation such as screen rotation.
class KmmBaseApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
