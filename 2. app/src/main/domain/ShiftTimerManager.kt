package com.example.tidrapportering.domain

import com.example.tidrapportering.model.ActiveShift
import com.example.tidrapportering.model.ShiftStatus
import com.example.tidrapportering.model.TimeEntry
import com.example.tidrapportering.model.TimerSnapshot
import com.example.tidrapportering.model.formatDurationHms
import com.example.tidrapportering.model.formatEpochHm
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

class ShiftTimerManager {

    fun startShift(
        customerId: String,
        customerName: String,
        breakTargetMins: Int = 30,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ActiveShift {
        return ActiveShift(
            customerId = customerId,
            customerName = customerName,
            startEpochMs = nowEpochMs,
            status = ShiftStatus.WORKING,
            accumulatedWorkMs = 0L,
            accumulatedBreakMs = 0L,
            currentSegmentStartMs = nowEpochMs,
            breakTargetMins = breakTargetMins
        )
    }

    fun toggleBreak(
        shift: ActiveShift,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ActiveShift {
        val elapsed = (nowEpochMs - shift.currentSegmentStartMs).coerceAtLeast(0L)
        return when (shift.status) {
            ShiftStatus.WORKING -> shift.copy(
                status = ShiftStatus.BREAK,
                accumulatedWorkMs = shift.accumulatedWorkMs + elapsed,
                currentSegmentStartMs = nowEpochMs
            )
            ShiftStatus.BREAK -> shift.copy(
                status = ShiftStatus.WORKING,
                accumulatedBreakMs = shift.accumulatedBreakMs + elapsed,
                currentSegmentStartMs = nowEpochMs
            )
            ShiftStatus.IDLE -> shift
        }
    }

    fun finishShift(
        entryId: String,
        shift: ActiveShift,
        project: String = "",
        desc: String = "",
        nowEpochMs: Long = System.currentTimeMillis()
    ): TimeEntry {
        val snap = snapshot(shift, nowEpochMs)
        val zone = ZoneId.systemDefault()
        val startZdt = Instant.ofEpochMilli(shift.startEpochMs).atZone(zone)
        val endZdt = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val workedHoursRounded = (snap.workedHours * 100.0).roundToInt() / 100.0
        val breakMinutesRounded = (snap.breakMs / 60_000L).toInt().coerceAtLeast(0)

        return TimeEntry(
            id = entryId,
            customerId = shift.customerId,
            date = startZdt.toLocalDate(),
            startTime = startZdt.toLocalTime(),
            endTime = endZdt.toLocalTime(),
            hours = workedHoursRounded,
            breakMin = breakMinutesRounded,
            project = project,
            desc = desc
        )
    }

    fun snapshot(
        shift: ActiveShift?,
        nowEpochMs: Long = System.currentTimeMillis()
    ): TimerSnapshot {
        if (shift == null) return TimerSnapshot()

        val elapsed = (nowEpochMs - shift.currentSegmentStartMs).coerceAtLeast(0L)
        val totalWorkMs = when (shift.status) {
            ShiftStatus.WORKING -> shift.accumulatedWorkMs + elapsed
            else -> shift.accumulatedWorkMs
        }
        val totalBreakMs = when (shift.status) {
            ShiftStatus.BREAK -> shift.accumulatedBreakMs + elapsed
            else -> shift.accumulatedBreakMs
        }

        val workedHours = totalWorkMs / 3_600_000.0
        val hoursLeftTo6h = (6.0 - workedHours).coerceAtLeast(0.0)
        val latestBreakEpochMs = shift.startEpochMs + (5.5 * 3_600_000.0).toLong()

        return TimerSnapshot(
            status = shift.status,
            customerName = shift.customerName,
            workedMs = totalWorkMs,
            breakMs = totalBreakMs,
            workedHours = workedHours,
            hoursLeftTo6h = hoursLeftTo6h,
            workedFormatted = formatDurationHms(totalWorkMs),
            breakFormatted = formatDurationHms(totalBreakMs),
            startTimeFormatted = formatEpochHm(shift.startEpochMs),
            latestBreakTimeFormatted = formatEpochHm(latestBreakEpochMs),
            isBreakWarning55h = workedHours >= 5.5,
            isWarning12h = workedHours >= 12.0,
            isCritical14h = workedHours >= 14.0
        )
    }
}
