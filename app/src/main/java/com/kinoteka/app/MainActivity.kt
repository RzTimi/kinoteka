package com.kinoteka.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kinoteka.app.data.share.ShareLink
import com.kinoteka.app.ui.KinotekaRoot
import com.kinoteka.app.ui.theme.KinotekaTheme
import com.kinoteka.app.ui.theme.isDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KinotekaApp).container
        // Открыли ссылкой «Открыть в Кінотеці» (при повороте экрана не повторяем).
        if (savedInstanceState == null) handleLink(intent)
        setContent {
            val theme by container.ui.theme.collectAsStateWithLifecycle()
            val dark = theme.isDark()
            // Значки строки состояния: светлые на тёмной теме, тёмные на светлой.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            KinotekaTheme(theme) {
                KinotekaRoot(container)
            }
        }
    }

    /** Приложение уже открыто, а пришла новая ссылка. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    private fun handleLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = ShareLink.fromUri(intent.data) ?: return
        (application as KinotekaApp).container.pendingLink.value = data
    }
}
