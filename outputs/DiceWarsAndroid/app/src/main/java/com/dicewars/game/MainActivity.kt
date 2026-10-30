package com.dicewars.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.dicewars.game.ui.DiceWarsApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = android.graphics.Color.rgb(17, 25, 21)
        window.navigationBarColor = android.graphics.Color.rgb(17, 25, 21)
        setContent {
            val colors = darkColorScheme(
                primary = Color(0xFF71E2B0),
                onPrimary = Color(0xFF092218),
                secondary = Color(0xFFFFC857),
                background = Color(0xFF111915),
                surface = Color(0xFF1A2720),
                onBackground = Color(0xFFF4F2E9),
                onSurface = Color(0xFFF4F2E9)
            )
            MaterialTheme(colorScheme = colors) { DiceWarsApp() }
        }
    }

}

