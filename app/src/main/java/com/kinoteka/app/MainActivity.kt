package com.kinoteka.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kinoteka.app.ui.KinotekaRoot
import com.kinoteka.app.ui.theme.KinotekaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as KinotekaApp).container
        setContent {
            KinotekaTheme {
                KinotekaRoot(container)
            }
        }
    }
}
