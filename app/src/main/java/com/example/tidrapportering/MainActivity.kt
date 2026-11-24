package com.example.tidrapportering

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidrapportering.domain.PricingCalculator
import com.example.tidrapportering.domain.RestCalculator
import com.example.tidrapportering.model.Customer
import com.example.tidrapportering.model.TimeEntry
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
    val pricingCalculator = remember { PricingCalculator() }
    val restCalculator = remember { RestCalculator() }

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

    var activeStartDateTime by remember { mutableStateOf<LocalDateTime?>(null) }
    val entries = remember { mutableStateListOf<TimeEntry>() }

    val isRunning = activeStartDateTime != null
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }

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

        Button(
            onClick = {
                val now = LocalDateTime.now()
                val start = activeStartDateTime
                if (start == null) {
                    activeStartDateTime = now
                } else {
                    val minutes = Duration.between(start, now).toMinutes().coerceAtLeast(0L)
                    val newEntry = TimeEntry(
                        id = UUID.randomUUID().toString(),
                        customerId = defaultCustomer.id,
                        project = "Körning",
                        date = start.toLocalDate(),
                        startTime = start.toLocalTime(),
                        endTime = now.toLocalTime(),
                        hours = minutes / 60.0,
                        breakMin = 0,
                        breaks = emptyList(),
                        desc = "Pass registrerat i appen"
                    )
                    entries.add(0, newEntry)
                    activeStartDateTime = null
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = if (isRunning) "Stoppa tid" else "Starta tid",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isRunning) {
                "Status: Arbetar (startade ${activeStartDateTime?.format(timeFormatter)})"
            } else {
                "Status: Inaktiv"
            },
            fontSize = 15.sp
        )

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
