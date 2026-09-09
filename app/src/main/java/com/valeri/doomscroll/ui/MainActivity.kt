package com.valeri.doomscroll.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valeri.doomscroll.ui.theme.DoomscrollTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DoomscrollTheme(darkTheme = true) {
                Surface { RootScreen() }
            }
        }
    }
}

enum class Screen { SETUP, LEARN }

@Composable
private fun RootScreen() {
    var screen by remember { mutableStateOf(Screen.SETUP) }

    Scaffold { inner ->
        when (screen) {
            Screen.SETUP -> SetupScreen(
                modifier = Modifier.padding(inner),
                onOpenLearnMode = { screen = Screen.LEARN },
            )

            Screen.LEARN -> LearnModeScreen(
                modifier = Modifier.padding(inner),
                onBack = { screen = Screen.SETUP },
            )
        }
    }
}
