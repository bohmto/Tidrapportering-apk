package com.example.tidrapportering.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.tidrapportering.domain.PricingCalculator
import com.example.tidrapportering.domain.RestCalculator
import com.example.tidrapportering.domain.ShiftTimerManager
import com.example.tidrapportering.model.ActiveShift
import com.example.tidrapportering.model.BreakPeriod
import com.example.tidrapportering.model.Customer
import com.example.tidrapportering.model.EntryCostSummary
import com.example.tidrapportering.model.EntryOvertimeSummary
import com.example.tidrapportering.model.RateType
import com.example.tidrapportering.model.RestAnalysisResult
import com.example.tidrapportering.model.ShiftDay
import com.example.tidrapportering.model.TimeEntry
import com.example.tidrapportering.model.TimerSnapshot
import java.time.LocalTime
import java.util.UUID

data class UserProfileInfo(
    val id: String = "default_user",
    val name: String = "T Nyström Böhm",
    val email: String = "",
    val phone: String = "",
    val address: String = ""
)

enum class AppTab(val label: String) {
    TIME("Tid"),
    CUSTOMERS("Kunder"),
    CLIENT("Användare")
}

data class TrackerUiState(
    val currentTab: AppTab = AppTab.TIME,
    val customers: List<Customer> = emptyList(),
    val selectedCustomerId: String = "",
    val entries: List<TimeEntry> = emptyList(),
    val activeShift: ActiveShift? = null,
    val timerSnapshot: TimerSnapshot = TimerSnapshot(),
    val monitorTransportRules: Boolean = true,
    val userProfile: UserProfileInfo = UserProfileInfo(),
    val scheduleEnabled: Boolean = true,
    val showWorkedTimeInCalendar: Boolean = true,
    val calendarShifts: Map<java.time.LocalDate, ShiftDay> = emptyMap(),
    val restAnalysis: RestAnalysisResult? = null
)

class TimeTrackerViewModel(
    private val pricingCalculator: PricingCalculator = PricingCalculator(),
    private val restCalculator: RestCalculator = RestCalculator(),
    private val timerManager: ShiftTimerManager = ShiftTimerManager()
) : ViewModel() {

    private val defaultCustomer = Customer(
        id = "k_1",
        name = "Ica Hem till Dig",
        hourlyRate = 481.0,
        nightWorkExempt = false,
        obEnabled = true,
        obStartTime = LocalTime.of(18, 0),
        obEndTime = LocalTime.of(6, 0),
        obRateType = RateType.MULTIPLIER,
        obRateValue = 1.5,
        obWeekendEnabled = true,
        obWeekendAllDay = true,
        obWeekendFridayStart = LocalTime.of(21, 0),
        obWeekendSundayEnd = LocalTime.MIDNIGHT,
        obWeekendRateType = RateType.MULTIPLIER,
        obWeekendRateValue = 2.0,
        otEnabled = true,
        otMonthlyCap = 160.0,
        otRateType = RateType.MULTIPLIER,
        otSimpleValue = 1.5,
        otQualValue = 2.0,
        notes = "Chaufför Ica - Standardavtal"
    )

    var uiState by mutableStateOf(
        TrackerUiState(
            customers = listOf(defaultCustomer),
            selectedCustomerId = defaultCustomer.id
        )
    )
        private set

    fun selectTab(tab: AppTab) {
        uiState = uiState.copy(currentTab = tab)
    }

    fun selectCustomer(customerId: String) {
        if (uiState.customers.any { it.id == customerId }) {
            uiState = uiState.copy(selectedCustomerId = customerId)
        }
    }

    fun startShift(customerId: String, breakTargetMins: Int = 30) {
        val customer = uiState.customers.find { it.id == customerId } ?: return
        val started = timerManager.startShift(
            customerId = customer.id,
            customerName = customer.name,
            breakTargetMins = breakTargetMins
        )
        uiState = uiState.copy(
            selectedCustomerId = customer.id,
            activeShift = started,
            timerSnapshot = timerManager.snapshot(started)
        )
    }

    fun toggleBreak() {
        val current = uiState.activeShift ?: return
        val updated = timerManager.toggleBreak(current)
        uiState = uiState.copy(
            activeShift = updated,
            timerSnapshot = timerManager.snapshot(updated)
        )
    }

    fun finishShift(project: String = "", desc: String = "Stämplat pass") {
        val current = uiState.activeShift ?: return
        val finishedEntry = timerManager.finishShift(
            entryId = "entry_${UUID.randomUUID()}",
            shift = current,
            project = project,
            desc = desc
        )
        val updatedEntries = listOf(finishedEntry) + uiState.entries
        uiState = uiState.copy(
            activeShift = null,
            timerSnapshot = TimerSnapshot(),
            entries = updatedEntries,
            restAnalysis = restCalculator.calculateRequiredRest(updatedEntries)
        )
    }

    fun refreshTimerTick(nowEpochMs: Long = System.currentTimeMillis()) {
        if (uiState.activeShift == null) return
        val snap = timerManager.snapshot(uiState.activeShift, nowEpochMs)
        uiState = uiState.copy(timerSnapshot = snap)
    }

    fun setMonitorTransportRules(enabled: Boolean) {
        uiState = uiState.copy(monitorTransportRules = enabled)
    }

    fun addOrUpdateEntry(entry: TimeEntry) {
        val exists = uiState.entries.any { it.id == entry.id }
        val updated = if (exists) {
            uiState.entries.map { if (it.id == entry.id) entry else it }
        } else {
            listOf(entry) + uiState.entries
        }
        uiState = uiState.copy(
            entries = updated,
            restAnalysis = restCalculator.calculateRequiredRest(updated)
        )
    }

    fun deleteEntry(entryId: String) {
        val updated = uiState.entries.filterNot { it.id == entryId }
        uiState = uiState.copy(
            entries = updated,
            restAnalysis = restCalculator.calculateRequiredRest(updated)
        )
    }

    fun addOrUpdateCustomer(customer: Customer) {
        val exists = uiState.customers.any { it.id == customer.id }
        val updated = if (exists) {
            uiState.customers.map { if (it.id == customer.id) customer else it }
        } else {
            uiState.customers + customer
        }
        val nextSelected = uiState.selectedCustomerId.ifBlank { customer.id }
        uiState = uiState.copy(
            customers = updated,
            selectedCustomerId = nextSelected
        )
    }

    fun deleteCustomer(customerId: String) {
        val updated = uiState.customers.filterNot { it.id == customerId }
        val nextSelected = if (uiState.selectedCustomerId == customerId) {
            updated.firstOrNull()?.id.orEmpty()
        } else {
            uiState.selectedCustomerId
        }
        uiState = uiState.copy(
            customers = updated,
            selectedCustomerId = nextSelected
        )
    }

    fun updateUserProfile(profile: UserProfileInfo) {
        uiState = uiState.copy(userProfile = profile)
    }

    fun updateScheduleDay(shiftDay: ShiftDay) {
        val updatedMap = uiState.calendarShifts.toMutableMap().apply {
            put(shiftDay.date, shiftDay)
        }
        uiState = uiState.copy(calendarShifts = updatedMap)
    }

    fun toggleScheduleEnabled(enabled: Boolean) {
        uiState = uiState.copy(scheduleEnabled = enabled)
    }

    fun toggleShowWorkedTime(show: Boolean) {
        uiState = uiState.copy(showWorkedTimeInCalendar = show)
    }

    fun costSummaryFor(entry: TimeEntry): EntryCostSummary {
        val customer = uiState.customers.find { it.id == entry.customerId }
        return pricingCalculator.calculateEntryCost(entry, customer)
    }

    fun overtimeMap(): Map<String, EntryOvertimeSummary> =
        pricingCalculator.calculateMonthlyOvertimeMap(uiState.entries, uiState.customers)

    fun sampleBreakPeriods(): List<BreakPeriod> = listOf(BreakPeriod(LocalTime.of(12, 0), LocalTime.of(12, 30)))
}
