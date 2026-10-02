package io.github.igormy.vocora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import io.github.igormy.vocora.shizuku.ShizukuStatusScreen
import io.github.igormy.vocora.ui.theme.VocoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VocoraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ShizukuStatusScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
