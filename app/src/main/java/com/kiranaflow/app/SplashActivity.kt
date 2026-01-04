package com.kiranaflow.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kiranaflow.app.ui.theme.KiranaTheme

class SplashActivity : ComponentActivity() {
    private val SPLASH_DELAY: Long = 2000 // 2 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            KiranaTheme {
                SplashScreen()
            }
        }

        // Navigate to MainActivity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DELAY)
    }
}

@Composable
fun SplashScreen() {
    val context = LocalContext.current
    val logoPainter: Painter = painterResource(id = com.kiranaflow.app.R.drawable.this_iz_biz_logo)
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1E983B) // Background color #1e983b
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = logoPainter,
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(200.dp) // Large size for full screen impact
                    .padding(32.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    KiranaTheme {
        SplashScreen()
    }
}
