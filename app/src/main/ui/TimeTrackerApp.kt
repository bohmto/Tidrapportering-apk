package com.example.tidrapportering.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tidrapportering.model.Customer
import java.util.UUID

@Composable
fun CustomersTabContent(
    modifier: Modifier,
    viewModel: TimeTrackerViewModel,
    onStateUpdated: () -> Unit
) {
    val state = viewModel.uiState
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

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
                Column {
                    Text(
                        text = "Kundregister & Avtal",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.customers.size} kunder sparade",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        editingCustomer = Customer(
                            id = "k_${UUID.randomUUID()}",
                            name = "",
                            hourlyRate = 350.0,
                            obEnabled = true,
                            obWeekendEnabled = true,
                            otEnabled = false,
                            otMonthlyCap = 160.0
                        )
                        isCreatingNew = true
                    }
                ) {
                    Text("+ Ny kund")
                }
            }
        }

        items(state.customers, key = { it.id }) { customer ->
            val isActiveCustomer = state.selectedCustomerId == customer.id
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActiveCustomer) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = customer.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isActiveCustomer) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Aktiv kund") }
                            )
                        }
                    }

                    Text(
                        text = "Grundpris: ${"%.0f".format(customer.hourlyRate)} kr/h",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val details = buildList {
                        if (customer.obEnabled) {
                            add("OB ${customer.obStartTime}–${customer.obEndTime} (${customer.obRateValue}x)")
                        }
                        if (customer.obWeekendEnabled) {
                            add("Helg-OB (${customer.obWeekendRateValue}x)")
                        }
                        if (customer.otEnabled) {
                            add("Övertid > ${customer.otMonthlyCap.toInt()} h/mån")
                        }
                        if (customer.notes.isNotBlank()) {
                            add(customer.notes)
                        }
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            text = details.joinToString(" • "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isActiveCustomer) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.selectCustomer(customer.id)
                                    onStateUpdated()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Välj")
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                editingCustomer = customer
                                isCreatingNew = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Redigera")
                        }
                        TextButton(
                            onClick = {
                                viewModel.deleteCustomer(customer.id)
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

    editingCustomer?.let { draft ->
        CustomerEditorDialog(
            initial = draft,
            isNew = isCreatingNew,
            onDismiss = {
                editingCustomer = null
                isCreatingNew = false
            },
            onSave = { updatedCustomer ->
                viewModel.addOrUpdateCustomer(updatedCustomer)
                if (isCreatingNew || state.selectedCustomerId.isBlank()) {
                    viewModel.selectCustomer(updatedCustomer.id)
                }
                editingCustomer = null
                isCreatingNew = false
                onStateUpdated()
            }
        )
    }
}

@Composable
private fun CustomerEditorDialog(
    initial: Customer,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var rateText by remember(initial.id) { mutableStateOf(initial.hourlyRate.toString()) }
    var obEnabled by remember(initial.id) { mutableStateOf(initial.obEnabled) }
    var obRateText by remember(initial.id) { mutableStateOf(initial.obRateValue.toString()) }
    var obWeekendEnabled by remember(initial.id) { mutableStateOf(initial.obWeekendEnabled) }
    var otEnabled by remember(initial.id) { mutableStateOf(initial.otEnabled) }
    var otCapText by remember(initial.id) { mutableStateOf(initial.otMonthlyCap.toString()) }
    var notes by remember(initial.id) { mutableStateOf(initial.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Ny kund" else "Redigera kund") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Kundnamn") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Timpris (kr/h)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vardags-OB", fontSize = 14.sp)
                    Switch(checked = obEnabled, onCheckedChange = { obEnabled = it })
                }
                if (obEnabled) {
                    OutlinedTextField(
                        value = obRateText,
                        onValueChange = { obRateText = it },
                        label = { Text("OB-värde (t.ex. 1.5)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Helg-OB", fontSize = 14.sp)
                    Switch(checked = obWeekendEnabled, onCheckedChange = { obWeekendEnabled = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Månadsövertid", fontSize = 14.sp)
                    Switch(checked = otEnabled, onCheckedChange = { otEnabled = it })
                }
                if (otEnabled) {
                    OutlinedTextField(
                        value = otCapText,
                        onValueChange = { otCapText = it },
                        label = { Text("Övertidsgräns (timmar/månad)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Anteckning / org.nr / referens") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanName = name.trim()
                    if (cleanName.isNotEmpty()) {
                        onSave(
                            initial.copy(
                                name = cleanName,
                                hourlyRate = rateText.replace(',', '.').toDoubleOrNull() ?: initial.hourlyRate,
                                obEnabled = obEnabled,
                                obRateValue = obRateText.replace(',', '.').toDoubleOrNull() ?: initial.obRateValue,
                                obWeekendEnabled = obWeekendEnabled,
                                otEnabled = otEnabled,
                                otMonthlyCap = otCapText.replace(',', '.').toDoubleOrNull() ?: initial.otMonthlyCap,
                                notes = notes.trim()
                            )
                        )
                    }
                }
            ) {
                Text("Spara")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Avbryt")
            }
        }
    )
}
