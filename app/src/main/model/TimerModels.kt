package com.example.tidrapportering.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ShiftStatus {
    IDLE,
    WORKING,
    BREAK
}

data class ActiveShift(
    val customerId: String,
    val customerName: String,
    val startEpochMs: Long,
    val status: ShiftStatus = ShiftStatus.WORKING,
    val accumulatedWorkMs: Long = 0L,
    val accumulatedBreakMs: Long = 0L,
    val currentSegmentStartMs: Long = startEpochMs,
    val breakTargetMins: Int = 30
)

data class TimerSnapshot(
    val status: ShiftStatus = ShiftStatus.IDLE,
    val customerName: String = "",
    val workedMs: Long = 0L,
    val breakMs: Long = 0L,
    val workedHours: Double = 0.0,
    val hoursLeftTo6h: Double = 6.0,
    val workedFormatted: String = "00:00:00",
    val breakFormatted: String = "00:00:00",
    val startTimeFormatted: String = "--:--",
    val latestBreakTimeFormatted: String = "--:--",
    val isBreakWarning55h: Boolean = false,
    val isWarning12h: Boolean = false,
    val isCritical14h: Boolean = false
)

fun formatDurationHms(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return "%02d:%02d:%02d".format(h, m, s)
}

fun formatEpochHm(epochMs: Long): String {
    if (epochMs <= 0L) return "--:--"
    val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.format(DateTimeFormatter.ofPattern("HH:mm"))
}
