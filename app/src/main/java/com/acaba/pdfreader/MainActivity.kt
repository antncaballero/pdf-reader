package com.acaba.pdfreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.acaba.pdfreader.ui.PdfReaderApp
import com.acaba.pdfreader.ui.theme.PdfReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfReaderTheme {
                PdfReaderApp()
            }
        }
    }
}
