package com.example.kmmbase.android.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.kmmbase.android.common.AppLanguage
import com.example.kmmbase.android.common.LocalAppLanguage
import com.example.kmmbase.android.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    private val container get() = (application as KmmBaseApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val language = remember { AppLanguage(applicationContext) }
            CompositionLocalProvider(LocalAppLanguage provides language) {
                MaterialTheme { AppNavHost(container) }
            }
        }
    }
}
