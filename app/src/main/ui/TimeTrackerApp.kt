package com.example.tidrapportering.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidrapportering.model.Customer
import com.example.tidrapportering.model.ShiftStatus
import com.example.tidrapportering.model.TimeEntry
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
fun TimeTrackerApp(
    viewModel: TimeTrackerViewModel,
    onShiftStateChanged: (TimeTrackerViewModel) -> Unit = {}
) {
    var state by remember { mutableStateOf(viewModel.uiState) }

    LaunchedEffect(state.activeShift) {
        while (state.activeShift != null) {
            viewModel.refreshTimerTick()
            state = viewModel.uiState
            delay(1000L)
        }
    }

    val topBarColor = when (state.timerSnapshot.status) {
        ShiftStatus.WORKING -> Color(0xFF16A34A)
        ShiftStatus.BREAK -> Color(0xFFD97706)
        ShiftStatus.IDLE -> Color(0xFF334155)
    }

    val topBarText = when (state.timerSnapshot.status) {
        ShiftStatus.WORKING ->
            "⏱️ ARBETAR: ${state.timerSnapshot.customerName} (${state.timerSnapshot.workedFormatted})"
        ShiftStatus.BREAK ->
            "☕ RAST: ${state.timerSnapshot.customerName} (${state.timerSnapshot.breakFormatted})"
        ShiftStatus.IDLE ->
            "⏹ Ingen tidtagning igång"
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topBarColor)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = topBarText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = state.currentTab.label.uppercase(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.currentTab == tab,
                        onClick = {
                            viewModel.selectTab(tab)
                            state = viewModel.uiState
                        },
                        icon = {
                            Text(
                                text = when (tab) {
                                    AppTab.TIME -> "⏱️"
                                    AppTab.CUSTOMERS -> "👥"
                                    AppTab.CLIENT -> "👤"
                                },
                                fontSize = 18.sp
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (state.currentTab) {
            AppTab.TIME -> TimeTabContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                viewModel = viewModel,
                onStateUpdated = {
                    state = viewModel.uiState
                    onShiftStateChanged(viewModel)
                }
            )
            AppTab.CUSTOMERS -> CustomersTabContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                viewModel = viewModel,
                onStateUpdated = { state = viewModel.uiState }
            )
            AppTab.CLIENT -> ClientAndRestTabContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                viewModel = viewModel,
                onStateUpdated = { state = viewModel.uiState }
            )
        }
    }
}

@Composable
private fun TimeTabContent(
    modifier: Modifier,
    viewModel: TimeTrackerViewModel,
    onStateUpdated: () -> Unit
) {
    val state = viewModel.uiState
    var selectedCustomerId by remember(state.customers) {
        mutableStateOf(state.customers.firstOrNull()?.id.orEmpty())
    }
    var customerMenuExpanded by remember { mutableStateOf(false) }
    var manualHoursText by remember { mutableStateOf("7.5") }
    var manualDescText by remember { mutableStateOf("") }

    val selectedCustomerName = state.customers.find { it.id == selectedCustomerId }?.name ?: "Välj kund"
    val overtimeMap = remember(state.entries, state.customers) { viewModel.overtimeMap() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (state.timerSnapshot.status) {
                            ShiftStatus.IDLE -> "EJ INLOGGAD"
                            ShiftStatus.WORKING -> "ARBETAR (${state.timerSnapshot.customerName})"
                            ShiftStatus.BREAK -> "RAST (${state.timerSnapshot.customerName})"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = state.timerSnapshot.workedFormatted,
                        color = Color(0xFF38BDF8),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )

                    if (state.activeShift == null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box {
                            OutlinedButton(
                                onClick = { customerMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Kund: $selectedCustomerName", color = Color.White)
                            }
                            DropdownMenu(
                                expanded = customerMenuExpanded,
                                onDismissRequest = { customerMenuExpanded = false }
                            ) {
                                state.customers.forEach { customer ->
                                    DropdownMenuItem(
                                        text = { Text(customer.name) },
                                        onClick = {
                                            selectedCustomerId = customer.id
                                            customerMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Startade kl ${state.timerSnapshot.startTimeFormatted} | Nästa rast senast kl ${state.timerSnapshot.latestBreakTimeFormatted}",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.monitorTransportRules,
                            onCheckedChange = {
                                viewModel.setMonitorTransportRules(it)
                                onStateUpdated()
                            }
                        )
                        Text(
                            text = "Bevaka Vägarbetstid & Larm (5,5h)",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }

                    if (state.monitorTransportRules && state.activeShift != null) {
                        val snap = state.timerSnapshot
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF334155), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Total arbetstid: ${"%.1f".format(snap.workedHours)} h | Kvar till 6h: ${"%.1f".format(snap.hoursLeftTo6h)} h",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                            if (snap.isCritical14h) {
                                Text(
                                    text = "🚨 KRITISKT: Passet har nått 14h+ – avsluta snarast!",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (snap.isWarning12h) {
                                Text(
                                    text = "⚠️ VARNING: Passet har pågått över 12h",
                                    color = Color(0xFFFDE047),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (snap.isBreakWarning55h) {
                                Text(
                                    text = "⚠️ Rastvarning: Över 5,5h i perioden – ta rast senast kl ${snap.latestBreakTimeFormatted}!",
                                    color = Color(0xFFFDE047),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.activeShift == null) {
                            Button(
                                onClick = {
                                    if (selectedCustomerId.isNotBlank()) {
                                        viewModel.startShift(selectedCustomerId)
                                        onStateUpdated()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("▶ Starta pass")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.toggleBreak()
                                    onStateUpdated()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (state.activeShift.status == ShiftStatus.BREAK) "▶ Avsluta rast"
                                    else "☕ Starta rast"
                                )
                            }
                            Button(
                                onClick = {
                                    viewModel.finishShift()
                                    onStateUpdated()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⏹ Spara")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Snabbregistrera pass",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualHoursText,
                        onValueChange = { manualHoursText = it },
                        label = { Text("Timmar exkl. rast (t.ex. 7.5)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualDescText,
                        onValueChange = { manualDescText = it },
                        label = { Text("Beskrivning / ordernummer") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val hours = manualHoursText.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (selectedCustomerId.isNotBlank() && hours > 0.0) {
                                viewModel.addOrUpdateEntry(
                                    TimeEntry(
                                        id = "entry_${UUID.randomUUID()}",
                                        customerId = selectedCustomerId,
                                        date = LocalDate.now(),
                                        startTime = LocalTime.of(8, 0),
                                        endTime = LocalTime.of(16, 0),
                                        hours = hours,
                                        breakMin = 30,
                                        breaks = viewModel.sampleBreakPeriods(),
                                        desc = manualDescText.trim()
                                    )
                                )
                                manualDescText = ""
                                onStateUpdated()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Spara tidssegment")
                    }
                }
            }
        }

        items(state.entries, key = { it.id }) { entry ->
            val customerName = state.customers.find { it.id == entry.customerId }?.name ?: "Okänd kund"
            val costSummary = viewModel.costSummaryFor(entry)
            val otSummary = overtimeMap[entry.id]
            val totalCost = otSummary?.adjustedTotalCost ?: costSummary.cost

            Card(shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$customerName • ${entry.date}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${costSummary.totalHours} h | ${"%.0f".format(totalCost)} kr" +
                                (if (otSummary != null) " (inkl. ${otSummary.otHours}h övertid)" else ""),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.desc.isNotBlank()) {
                            Text(
                                text = entry.desc,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteEntry(entry.id)
                            onStateUpdated()
                        }
                    ) {
                        Text("Ta bort", color = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomersTabContent(
    modifier: Modifier,
    viewModel: TimeTrackerViewModel,
    onStateUpdated: () -> Unit
) {
    val state = viewModel.uiState
    var showNewCustomerDialog by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var rateInput by remember { mutableStateOf("481") }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kunder & Prislistor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { showNewCustomerDialog = true }) {
                    Text("+ Ny kund")
                }
            }
        }

        items(state.customers, key = { it.id }) { customer ->
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = customer.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${customer.hourlyRate} kr/h" +
                            (if (customer.obEnabled) " • OB aktiv" else "") +
                            (if (customer.otEnabled) " • Månadsövertid (${customer.otMonthlyCap.toInt()}h)" else ""),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showNewCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showNewCustomerDialog = false },
            title = { Text("Ny kund") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Kundnamn") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rateInput,
                        onValueChange = { rateInput = it },
                        label = { Text("Ordinarie timpris (kr/h)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val rate = rateInput.replace(',', '.').toDoubleOrNull() ?: 0.0
                    if (nameInput.isNotBlank()) {
                        viewModel.addOrUpdateCustomer(
                            Customer(
                                id = "k_${UUID.randomUUID()}",
                                name = nameInput.trim(),
                                hourlyRate = rate
                            )
                        )
                        nameInput = ""
                        showNewCustomerDialog = false
                        onStateUpdated()
                    }
                }) {
                    Text("Spara")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCustomerDialog = false }) {
                    Text("Avbryt")
                }
            }
        )
    }
}

@Composable
private fun ClientAndRestTabContent(
    modifier: Modifier,
    viewModel: TimeTrackerViewModel,
    onStateUpdated: () -> Unit
) {
    val state = viewModel.uiState
    var userName by remember(state.userProfile.name) { mutableStateOf(state.userProfile.name) }
    var userEmail by remember(state.userProfile.email) { mutableStateOf(state.userProfile.email) }
    var userPhone by remember(state.userProfile.phone) { mutableStateOf(state.userProfile.phone) }

    val rest = state.restAnalysis

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Användarens uppgifter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = userName,
                        onValueChange = { userName = it },
                        label = { Text("Namn") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = { userEmail = it },
                        label = { Text("E-post") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = userPhone,
                        onValueChange = { userPhone = it },
                        label = { Text("Telefon") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            viewModel.updateUserProfile(
                                state.userProfile.copy(
                                    name = userName.trim(),
                                    email = userEmail.trim(),
                                    phone = userPhone.trim()
                                )
                            )
                            onStateUpdated()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Spara användare")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Arbetsschema & kalender",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = state.scheduleEnabled,
                            onCheckedChange = {
                                viewModel.toggleScheduleEnabled(it)
                                onStateUpdated()
                            }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleShowWorkedTime(!state.showWorkedTimeInCalendar)
                                onStateUpdated()
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Visa sparad tid i kalendern", fontSize = 14.sp)
                        Switch(
                            checked = state.showWorkedTimeInCalendar,
                            onCheckedChange = {
                                viewModel.toggleShowWorkedTime(it)
                                onStateUpdated()
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "📊 Beräknat vilotidsbehov",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (rest == null) {
                        Text(
                            text = "Inga registrerade pass ännu. Spara ett pass för att se vilotider och vägarbetstid.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Reducerade dygnsvilor (9–11h): ${rest.reducedDailyUsed} av 3 förbrukade (${rest.reducedDailyRemaining} kvar)",
                            fontSize = 13.sp
                        )
                        if (rest.mustTakeRegularDailyNext) {
                            Text(
                                text = "⚠️ Nästa dygnsvila MÅSTE vara minst 11 timmar!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                        rest.earliestStart11h?.let {
                            Text("Tidigaste start efter 11h dygnsvila: $it", fontSize = 12.sp)
                        }
                        rest.earliestStart45h?.let {
                            Text("Tidigaste start efter 45h veckovila: $it", fontSize = 12.sp)
                        }
                        Text(
                            text = "Arbetad tid denna vecka: ${rest.currentWeekWorkedHours} h (max 60h)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
