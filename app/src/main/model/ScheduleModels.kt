package com.example.tidrapportering.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ShiftDay(
    val date: LocalDate,
    val start: LocalTime? = LocalTime.of(7, 0),
    val end: LocalTime? = LocalTime.of(16, 0),
    val breaks: List<BreakPeriod> = emptyList(),
    val breakMin: Int = 0,
    val note: String = "",
    val off: Boolean = false,
    val busy: Boolean = false
)

enum class WeeklyRestType {
    REGULAR,
    REDUCED
}

data class ReducedWeeklyRestInfo(
    val date: LocalDate,
    val restHours: Long,
    val shortageHours: Long,
    val compensationDeadline: LocalDate
)

data class RestAnalysisResult(
    val reducedDailyUsed: Int,
    val reducedDailyRemaining: Int,
    val mustTakeRegularDailyNext: Boolean,
    val lastWeeklyRestType: WeeklyRestType?,
    val mustTakeRegularWeeklyNext: Boolean,
    val latestWeeklyRestDeadline: LocalDateTime?,
    val pendingReducedWeekly: ReducedWeeklyRestInfo?,
    val lastShiftDate: LocalDate?,
    val earliestStart11h: LocalDateTime?,
    val earliestStart9h: LocalDateTime?,
    val earliestStart45h: LocalDateTime?,
    val earliestStart24h: LocalDateTime?,
    val currentWeekWorkedHours: Double
)
