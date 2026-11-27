package com.example.tidrapportering

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.tidrapportering.data.LocalStorageRepository
import com.example.tidrapportering.service.ShiftTimerService
import com.example.tidrapportering.ui.TimeTrackerApp
import com.example.tidrapportering.ui.TimeTrackerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val storage = remember { LocalStorageRepository(context.applicationContext) }
            val viewModel = remember {
                TimeTrackerViewModel(storage = storage)
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                TimeTrackerApp(
                    viewModel = viewModel,
                    onShiftStateChanged = { vm ->
                        ShiftTimerService.syncShift(context.applicationContext, vm.uiState.activeShift)
                    }
                )
            }
        }
    }
}
