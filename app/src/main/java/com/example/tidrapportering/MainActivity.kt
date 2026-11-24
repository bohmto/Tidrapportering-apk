package com.example.tidrapportering

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidrapportering.domain.PricingCalculator
import com.example.tidrapportering.domain.RestCalculator
import com.example.tidrapportering.domain.ShiftTimerManager
import com.example.tidrapportering.model.ActiveShift
import com.example.tidrapportering.model.Customer
import com.example.tidrapportering.model.ShiftStatus
import com.example.tidrapportering.model.TimeEntry
import com.example.tidrapportering.model.TimerSnapshot
import com.example.tidrapportering.service.ShiftTimerService
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

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
    val context = LocalContext.current
    val pricingCalculator = remember { PricingCalculator() }
    val restCalculator = remember { RestCalculator() }
    val timerManager = remember { ShiftTimerManager() }

    val defaultCustomer = remember {
        Customer(
            id = "kund-1",
            name = "Standardkund",
            hourlyRate = 250.0,
            obEnabled = true,
            obStartTime = LocalTime.of(18, 0),
            obEndTime = LocalTime.of(6, 0),
            obWeekendEnabled = true,
            obWeekendFridayStart = LocalTime.of(21, 0),
            obWeekendSundayEnd = LocalTime.of(6, 0),
            otEnabled = true,
            otMonthlyCap = 160.0
        )
    }

    var activeShift by remember { mutableStateOf<ActiveShift?>(null) }
    var snapshot by remember { mutableStateOf(TimerSnapshot()) }
    val entries = remember { mutableStateListOf<TimeEntry>() }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }

    LaunchedEffect(activeShift) {
        while (activeShift != null) {
            snapshot = timerManager.snapshot(activeShift)
            delay(1000L)
        }
        snapshot = TimerSnapshot()
    }

    val overtimeMap = remember(entries.size, entries.toList()) {
        pricingCalculator.calculateMonthlyOvertimeMap(entries, listOf(defaultCustomer))
    }
    val restAnalysis = remember(entries.size, entries.toList()) {
        restCalculator.calculateRequiredRest(entries)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tidrapportering",
            fontSize = 28.sp,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (activeShift == null) {
            Button(
                onClick = {
                    val started = timerManager.startShift(
                        customerId = defaultCustomer.id,
                        customerName = defaultCustomer.name
                    )
                    activeShift = started
                    snapshot = timerManager.snapshot(started)
                    ShiftTimerService.syncShift(context, started)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "Starta tid", fontSize = 18.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val current = activeShift ?: return@Button
                        val toggled = timerManager.toggleBreak(current)
                        activeShift = toggled
                        snapshot = timerManager.snapshot(toggled)
                        ShiftTimerService.syncShift(context, toggled)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(
                        text = if (snapshot.status == ShiftStatus.BREAK) "Avsluta rast" else "Starta rast",
                        fontSize = 16.sp
                    )
                }

                Button(
                    onClick = {
                        val current = activeShift ?: return@Button
                        val finished = timerManager.finishShift(
                            entryId = UUID.randomUUID().toString(),
                            shift = current,
                            project = "Körning",
                            desc = "Pass registrerat i appen"
                        )
                        entries.add(0, finished)
                        activeShift = null
                        snapshot = TimerSnapshot()
                        ShiftTimerService.syncShift(context, null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(text = "Stoppa & spara", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (snapshot.status) {
                ShiftStatus.WORKING ->
                    "Status: Arbetar (${snapshot.workedFormatted}) • Startade ${snapshot.startTimeFormatted}"
                ShiftStatus.BREAK ->
                    "Status: Rast (${snapshot.breakFormatted})"
                ShiftStatus.IDLE ->
                    "Status: Inaktiv"
            },
            fontSize = 15.sp
        )

        if (activeShift != null) {
            if (snapshot.isCritical14h) {
                Text(
                    text = "🚨 KRITISKT: Passet har nått 14h+ – avsluta snarast!",
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (snapshot.isWarning12h) {
                Text(
                    text = "⚠️ VARNING: Passet har pågått över 12h",
                    color = Color(0xFFB45309),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (snapshot.isBreakWarning55h) {
                Text(
                    text = "⚠️ Rastvarning: Över 5,5h i perioden – rast senast kl ${snapshot.latestBreakTimeFormatted}",
                    color = Color(0xFFB45309),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Vilotid & veckoöversikt",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (restAnalysis == null) {
                    Text("Ingen vilotidsdata ännu – registrera minst ett pass.")
                } else {
                    Text("Veckans arbetstid: ${restAnalysis.currentWeekWorkedHours} h")
                    Text(
                        "Reducerad dygnsvila: ${restAnalysis.reducedDailyUsed}/3 " +
                            "(kvar: ${restAnalysis.reducedDailyRemaining})"
                    )
                    val earliest11 = restAnalysis.earliestStart11h?.format(dateTimeFormatter) ?: "-"
                    val earliest9 = restAnalysis.earliestStart9h?.format(dateTimeFormatter) ?: "-"
                    Text("Tidigaste start (11h / 9h): $earliest11  /  $earliest9")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Registrerade pass (${entries.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                val costSummary = pricingCalculator.calculateEntryCost(entry, defaultCustomer)
                val otSummary = overtimeMap[entry.id]
                val totalCost = otSummary?.adjustedTotalCost ?: costSummary.cost
                val startText = entry.startTime?.format(timeFormatter) ?: "--:--"
                val endText = entry.endTime?.format(timeFormatter) ?: "--:--"

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${entry.date} • $startText–$endText",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "Totalt: ${costSummary.totalHours} h (OB: ${costSummary.obHours} h, " +
                                    "ÖT: ${otSummary?.otHours ?: 0.0} h)"
                            )
                        }
                        Text(
                            text = "${"%.2f".format(totalCost)} kr",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}
