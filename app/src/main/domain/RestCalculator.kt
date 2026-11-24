package com.example.tidrapportering.domain

import com.example.tidrapportering.model.ReducedWeeklyRestInfo
import com.example.tidrapportering.model.RestAnalysisResult
import com.example.tidrapportering.model.TimeEntry
import com.example.tidrapportering.model.WeeklyRestType
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

class RestCalculator {

    fun calculateRequiredRest(
        entries: List<TimeEntry>,
        now: LocalDateTime = LocalDateTime.now()
    ): RestAnalysisResult? {
        if (entries.isEmpty()) return null

        val sorted = entries.sortedWith(
            compareBy({ it.date }, { it.startTime ?: LocalTime.MIDNIGHT })
        )

        var reducedDailyCount = 0
        var lastReducedWeekly: ReducedWeeklyRestInfo? = null
        var lastWeeklyRestType: WeeklyRestType? = null
        var lastWeeklyRestEndTime: LocalDateTime? = null

        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]

            val cStart = current.startTime ?: continue
            val cEnd = current.endTime ?: continue
            val nStart = next.startTime ?: continue

            var currentEnd = LocalDateTime.of(current.date, cEnd)
            if (cEnd.isBefore(cStart)) {
                currentEnd = currentEnd.plusDays(1)
            }

            var nextStart = LocalDateTime.of(next.date, nStart)
            if (nextStart.isBefore(currentEnd)) {
                nextStart = nextStart.plusDays(1)
            }

            val restHours = Duration.between(currentEnd, nextStart).toMinutes() / 60.0

            when {
                restHours >= 45.0 -> {
                    reducedDailyCount = 0
                    lastWeeklyRestType = WeeklyRestType.REGULAR
                    lastWeeklyRestEndTime = nextStart
                }
                restHours >= 24.0 && restHours < 45.0 -> {
                    reducedDailyCount = 0
                    lastWeeklyRestType = WeeklyRestType.REDUCED
                    lastWeeklyRestEndTime = nextStart
                    val roundedRest = restHours.roundToInt().toLong()
                    lastReducedWeekly = ReducedWeeklyRestInfo(
                        date = current.date,
                        restHours = roundedRest,
                        shortageHours = 45L - roundedRest,
                        compensationDeadline = nextStart.plusDays(21).toLocalDate()
                    )
                }
                restHours >= 9.0 && restHours < 11.0 -> {
                    reducedDailyCount++
                }
            }
        }

        val lastEntry = sorted.last()
        var earliest11: LocalDateTime? = null
        var earliest9: LocalDateTime? = null
        var earliest45: LocalDateTime? = null
        var earliest24: LocalDateTime? = null

        if (lastEntry.startTime != null && lastEntry.endTime != null) {
            var lastEnd = LocalDateTime.of(lastEntry.date, lastEntry.endTime)
            if (lastEntry.endTime.isBefore(lastEntry.startTime)) {
                lastEnd = lastEnd.plusDays(1)
            }
            earliest11 = lastEnd.plusHours(11)
            earliest9 = lastEnd.plusHours(9)
            earliest45 = lastEnd.plusHours(45)
            earliest24 = lastEnd.plusHours(24)
        }

        val startOfWeek: LocalDate = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val weeklyHoursRaw = sorted
            .filter { !it.date.isBefore(startOfWeek) }
            .sumOf { it.hours }
        val weeklyHours = (weeklyHoursRaw * 100.0).roundToInt() / 100.0

        return RestAnalysisResult(
            reducedDailyUsed = reducedDailyCount,
            reducedDailyRemaining = (3 - reducedDailyCount).coerceAtLeast(0),
            mustTakeRegularDailyNext = reducedDailyCount >= 3,
            lastWeeklyRestType = lastWeeklyRestType,
            mustTakeRegularWeeklyNext = lastWeeklyRestType == WeeklyRestType.REDUCED,
            latestWeeklyRestDeadline = lastWeeklyRestEndTime?.plusHours(144),
            pendingReducedWeekly = lastReducedWeekly,
            lastShiftDate = lastEntry.date,
            earliestStart11h = earliest11,
            earliestStart9h = earliest9,
            earliestStart45h = earliest45,
            earliestStart24h = earliest24,
            currentWeekWorkedHours = weeklyHours
        )
    }
}
