package com.example.tidrapportering.domain

import com.example.tidrapportering.model.BreakPeriod
import com.example.tidrapportering.model.ShiftDay
import java.time.LocalDate
import java.time.LocalTime

class IcsParser {

    fun parseIcs(icsText: String): Map<LocalDate, ShiftDay> {
        val result = mutableMapOf<LocalDate, ShiftDay>()
        val lines = icsText.split(Regex("\\r\\n|\\n|\\r"))

        var inEvent = false
        var dtStartRaw: String? = null
        var dtEndRaw: String? = null
        var summaryRaw: String? = null

        for (line in lines) {
            when {
                line.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    dtStartRaw = null
                    dtEndRaw = null
                    summaryRaw = null
                }
                line.startsWith("END:VEVENT") -> {
                    inEvent = false
                    val startParsed = parseIcsDateTime(dtStartRaw)
                    val endParsed = parseIcsDateTime(dtEndRaw)

                    if (startParsed != null) {
                        val (date, startTime) = startParsed
                        val endTime = endParsed?.second ?: LocalTime.of(17, 0)
                        result[date] = ShiftDay(
                            date = date,
                            start = startTime,
                            end = endTime,
                            breaks = listOf(BreakPeriod(LocalTime.of(12, 0), LocalTime.of(12, 30))),
                            breakMin = 30,
                            note = summaryRaw.orEmpty(),
                            off = false,
                            busy = false
                        )
                    }
                }
                inEvent -> {
                    when {
                        line.startsWith("DTSTART") -> dtStartRaw = line.substringAfter(':', "")
                        line.startsWith("DTEND") -> dtEndRaw = line.substringAfter(':', "")
                        line.startsWith("SUMMARY") -> summaryRaw = line.substringAfter(':', "").trim()
                    }
                }
            }
        }

        return result
    }

    private fun parseIcsDateTime(raw: String?): Pair<LocalDate, LocalTime>? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.replace(Regex("[^0-9T]"), "")
        if (clean.length < 8) return null

        val year = clean.substring(0, 4).toIntOrNull() ?: return null
        val month = clean.substring(4, 6).toIntOrNull() ?: return null
        val day = clean.substring(6, 8).toIntOrNull() ?: return null
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null

        var time = LocalTime.of(8, 0)
        val tIndex = clean.indexOf('T')
        if (tIndex != -1 && clean.length >= tIndex + 5) {
            val hh = clean.substring(tIndex + 1, tIndex + 3).toIntOrNull() ?: 8
            val mm = clean.substring(tIndex + 3, tIndex + 5).toIntOrNull() ?: 0
            time = runCatching { LocalTime.of(hh, mm) }.getOrDefault(LocalTime.of(8, 0))
        }

        return Pair(date, time)
    }
}
