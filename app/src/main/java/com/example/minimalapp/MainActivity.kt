package com.example.minimalapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.minimalapp.conchat.ConchatScreen
import com.example.minimalapp.ui.theme.MinimalAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MinimalAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConchatScreen()
                }
            }
        }
    }
}

@Composable
private fun MinimalScreen() {
    ConchatScreen()
}

@Preview(showBackground = true)
@Composable
private fun MinimalScreenPreview() {
    MinimalAppTheme {
        MinimalScreen()
    }
}
