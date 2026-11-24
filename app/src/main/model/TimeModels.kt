package com.example.tidrapportering.model

import java.time.LocalDate
import java.time.LocalTime

data class BreakPeriod(
    val start: LocalTime? = null,
    val end: LocalTime? = null
) {
    fun durationMinutes(): Long {
        val s = start ?: return 0L
        val e = end ?: return 0L
        val startMins = s.hour * 60L + s.minute
        var endMins = e.hour * 60L + e.minute
        if (endMins < startMins) endMins += 24 * 60L
        return (endMins - startMins).coerceAtLeast(0L)
    }
}

enum class RateType {
    MULTIPLIER,
    ADDON,
    FIXED_RATE
}

data class Customer(
    val id: String,
    val name: String,
    val hourlyRate: Double,
    val nightWorkExempt: Boolean = false,

    val obEnabled: Boolean = false,
    val obStartTime: LocalTime = LocalTime.of(18, 0),
    val obEndTime: LocalTime = LocalTime.of(6, 0),
    val obRateType: RateType = RateType.MULTIPLIER,
    val obRateValue: Double = 1.5,

    val obWeekendEnabled: Boolean = false,
    val obWeekendAllDay: Boolean = true,
    val obWeekendFridayStart: LocalTime = LocalTime.of(21, 0),
    val obWeekendSundayEnd: LocalTime = LocalTime.MIDNIGHT,
    val obWeekendStartTime: LocalTime = LocalTime.MIDNIGHT,
    val obWeekendEndTime: LocalTime = LocalTime.of(23, 59),
    val obWeekendRateType: RateType = RateType.MULTIPLIER,
    val obWeekendRateValue: Double = 2.0,

    val otEnabled: Boolean = false,
    val otMonthlyCap: Double = 160.0,
    val otRateType: RateType = RateType.MULTIPLIER,
    val otSimpleValue: Double = 1.5,
    val otQualValue: Double = 2.0,

    val notes: String = ""
)

data class TimeEntry(
    val id: String,
    val customerId: String,
    val project: String = "",
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val hours: Double,
    val breakMin: Int = 0,
    val breaks: List<BreakPeriod> = emptyList(),
    val desc: String = ""
)

data class ReportRow(
    val entryId: String,
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val breakMin: Int,
    val normalHours: Double,
    val obHours: Double,
    val otHours: Double,
    val hours: Double,
    val rate: Double,
    val cost: Double,
    val project: String,
    val customerName: String,
    val desc: String,
    val isWeekendOb: Boolean = false
)

data class EntryCostSummary(
    val totalHours: Double,
    val normalHours: Double,
    val obHours: Double,
    val weekdayObHours: Double,
    val weekendObHours: Double,
    val weekdayObCost: Double,
    val weekendObCost: Double,
    val rate: Double,
    val cost: Double,
    val breakMin: Int
)

data class EntryOvertimeSummary(
    val otHours: Double,
    val otSimpleHours: Double,
    val otQualHours: Double,
    val otCost: Double,
    val adjustedTotalCost: Double
)
