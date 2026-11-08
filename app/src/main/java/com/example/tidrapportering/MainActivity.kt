package com.example.tidrapportering

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                TidrapporteringApp()
            }
        }
    }
}

@Composable
fun TidrapporteringApp() {
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Tidrapportering", fontSize = 28.sp, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { isRunning = !isRunning },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(text = if (isRunning) "Stoppa tid" else "Starta tid", fontSize = 18.sp)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = if (isRunning) "Status: Arbetar..." else "Status: Inaktiv", fontSize = 16.sp)
    }
}
