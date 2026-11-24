package com.example.tidrapportering.domain

import com.example.tidrapportering.model.Customer
import com.example.tidrapportering.model.EntryCostSummary
import com.example.tidrapportering.model.EntryOvertimeSummary
import com.example.tidrapportering.model.RateType
import com.example.tidrapportering.model.ReportRow
import com.example.tidrapportering.model.TimeEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt

class PricingCalculator(
    private val extraHolidayDates: Set<LocalDate> = emptySet()
) {

    fun isHolidayOrKlamdag(date: LocalDate): Boolean {
        if (date in extraHolidayDates) return true
        return date in getHolidaysForYear(date.year)
    }

    fun isWeekend(date: LocalDate): Boolean {
        if (isHolidayOrKlamdag(date)) return true
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }

    fun getHolidaysForYear(year: Int): Set<LocalDate> {
        val holidays = mutableSetOf(
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 1, 6),
            LocalDate.of(year, 5, 1),
            LocalDate.of(year, 6, 6),
            LocalDate.of(year, 12, 24),
            LocalDate.of(year, 12, 25),
            LocalDate.of(year, 12, 26),
            LocalDate.of(year, 12, 31)
        )

        val easter = easterSunday(year)
        holidays += easter.minusDays(2)
        holidays += easter.minusDays(1)
        holidays += easter.plusDays(1)
        holidays += easter.plusDays(39)
        holidays += easter.plusDays(49)
        holidays += easter.plusDays(50)

        for (day in 19..25) {
            val candidate = LocalDate.of(year, 6, day)
            if (candidate.dayOfWeek == DayOfWeek.SATURDAY) {
                holidays += candidate
                holidays += candidate.minusDays(1)
                break
            }
        }

        for (offset in 0..6) {
            val candidate = LocalDate.of(year, 10, 31).plusDays(offset.toLong())
            if (candidate.dayOfWeek == DayOfWeek.SATURDAY) {
                holidays += candidate
                break
            }
        }

        return holidays
    }

    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    private fun toMinuteOfDay(time: LocalTime): Int = time.hour * 60 + time.minute

    private fun isWeekendObMinute(date: LocalDate, minuteOfDay: Int, customer: Customer): Boolean {
        if (isHolidayOrKlamdag(date)) return true

        val fridayStartMins = toMinuteOfDay(customer.obWeekendFridayStart)
        val sundayEndMins = toMinuteOfDay(customer.obWeekendSundayEnd)

        return when (date.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> {
                if (!customer.obWeekendAllDay) {
                    val wStart = toMinuteOfDay(customer.obWeekendStartTime)
                    val wEnd = toMinuteOfDay(customer.obWeekendEndTime)
                    minuteOfDay in wStart..wEnd
                } else {
                    true
                }
            }
            DayOfWeek.FRIDAY -> fridayStartMins > 0 && minuteOfDay >= fridayStartMins
            DayOfWeek.MONDAY -> sundayEndMins > 0 && minuteOfDay < sundayEndMins
            else -> {
                val nextDayIsHoliday = isHolidayOrKlamdag(date.plusDays(1))
                val prevDayIsHoliday = isHolidayOrKlamdag(date.minusDays(1))
                (nextDayIsHoliday && fridayStartMins > 0 && minuteOfDay >= fridayStartMins) ||
                    (prevDayIsHoliday && sundayEndMins > 0 && minuteOfDay < sundayEndMins)
            }
        }
    }

    fun getOvertimeHourlyRate(baseRate: Double, customer: Customer, isQualified: Boolean): Double {
        val value = if (isQualified) customer.otQualValue else customer.otSimpleValue
        return when (customer.otRateType) {
            RateType.ADDON -> baseRate + value
            RateType.FIXED_RATE -> value
            RateType.MULTIPLIER -> baseRate * value
        }
    }

    private fun getBreakMinuteSet(entry: TimeEntry, startM: Int, endM: Int): Set<Int>? {
        if (entry.breaks.isEmpty()) return null
        val breakSet = mutableSetOf<Int>()
        var matchedAny = false

        for (b in entry.breaks) {
            val s = b.start ?: continue
            val e = b.end ?: continue
            var bStart = toMinuteOfDay(s)
            var bEnd = toMinuteOfDay(e)
            if (bEnd < bStart) bEnd += 24 * 60

            while (bStart < startM - 720) {
                bStart += 1440
                bEnd += 1440
            }
            if (bEnd <= startM && bStart + 1440 < endM) {
                bStart += 1440
                bEnd += 1440
            }

            val clampedStart = maxOf(startM, bStart)
            val clampedEnd = minOf(endM, bEnd)
            for (m in clampedStart until clampedEnd) {
                breakSet += m
                matchedAny = true
            }
        }
        return if (matchedAny) breakSet else null
    }

    private data class Bucket(
        val category: String,
        val date: LocalDate,
        var mins: Int = 0,
        var breakMins: Int = 0,
        var firstMin: Int = 0,
        var lastMin: Int = 0
    )

    fun splitEntryForReport(entry: TimeEntry, customer: Customer?): List<ReportRow> {
        val startTime = entry.startTime ?: LocalTime.MIDNIGHT
        val endTime = entry.endTime ?: LocalTime.MIDNIGHT
        val baseRate = customer?.hourlyRate ?: 0.0
        val custName = customer?.name ?: "Okänd kund"
        val proj = entry.project
        val desc = entry.desc
        val totalBreakMins = if (entry.breakMin > 0) entry.breakMin else entry.breaks.sumOf { it.durationMinutes().toInt() }

        val startM = toMinuteOfDay(startTime)
        var endM = toMinuteOfDay(endTime)
        if (endM < startM) endM += 24 * 60

        if (customer == null || !customer.obEnabled) {
            val hoursVal = round2(entry.hours)
            val costVal = round2(hoursVal * baseRate)
            return listOf(
                ReportRow(
                    entryId = entry.id,
                    date = entry.date,
                    startTime = formatTime(startTime),
                    endTime = formatTime(endTime),
                    breakMin = totalBreakMins,
                    normalHours = hoursVal,
                    obHours = 0.0,
                    otHours = 0.0,
                    hours = hoursVal,
                    rate = baseRate,
                    cost = costVal,
                    project = proj,
                    customerName = custName,
                    desc = desc,
                    isWeekendOb = false
                )
            )
        }

        val obStartM = toMinuteOfDay(customer.obStartTime)
        val obEndM = toMinuteOfDay(customer.obEndTime)

        val weekdayObRate = when (customer.obRateType) {
            RateType.MULTIPLIER -> baseRate * customer.obRateValue
            RateType.ADDON -> baseRate + customer.obRateValue
            RateType.FIXED_RATE -> customer.obRateValue
        }

        val weekendObEnabled = customer.obWeekendEnabled
        val weekendObRate = if (weekendObEnabled) {
            when (customer.obWeekendRateType) {
                RateType.MULTIPLIER -> baseRate * customer.obWeekendRateValue
                RateType.ADDON -> baseRate + customer.obWeekendRateValue
                RateType.FIXED_RATE -> customer.obWeekendRateValue
            }
        } else {
            weekdayObRate
        }

        val explicitBreakMinutes = getBreakMinuteSet(entry, startM, endM)
        var unplacedBreakMins = if (explicitBreakMinutes != null) 0 else totalBreakMins

        val buckets = LinkedHashMap<String, Bucket>()

        fun getOrCreateBucket(category: String, date: LocalDate, minuteOfDay: Int): Bucket {
            val key = "$category|$date"
            return buckets.getOrPut(key) {
                Bucket(category, date, firstMin = minuteOfDay, lastMin = minuteOfDay)
            }.apply {
                lastMin = minuteOfDay
            }
        }

        for (m in startM until endM) {
            val dayOffset = m / 1440
            val minuteOfDay = m % 1440
            val currentDate = entry.date.plusDays(dayOffset.toLong())

            val category = when {
                weekendObEnabled && isWeekendObMinute(currentDate, minuteOfDay, customer) -> "weekend_ob"
                minuteOfDay < obEndM -> "morning_ob"
                minuteOfDay >= obStartM -> "evening_ob"
                else -> "normal"
            }

            val bucket = getOrCreateBucket(category, currentDate, minuteOfDay)
            if (explicitBreakMinutes != null && m in explicitBreakMinutes) {
                bucket.breakMins += 1
            } else {
                bucket.mins += 1
            }
        }

        if (unplacedBreakMins > 0) {
            for (b in buckets.values) {
                if (unplacedBreakMins <= 0) break
                val deduct = minOf(b.mins, unplacedBreakMins)
                b.mins -= deduct
                b.breakMins += deduct
                unplacedBreakMins -= deduct
            }
        }

        return buckets.values.filter { it.mins > 0 }.map { b ->
            val hoursVal = round2(b.mins / 60.0)
            val segStart = formatMinuteOfDay(b.firstMin)
            val segEnd = formatMinuteOfDay(b.lastMin + 1)

            when (b.category) {
                "weekend_ob" -> ReportRow(
                    entryId = entry.id,
                    date = b.date,
                    startTime = segStart,
                    endTime = segEnd,
                    breakMin = b.breakMins,
                    normalHours = 0.0,
                    obHours = hoursVal,
                    otHours = 0.0,
                    hours = hoursVal,
                    rate = weekendObRate,
                    cost = round2(hoursVal * weekendObRate),
                    project = if (proj.isNotBlank()) "$proj (Helg-OB)" else "(Helg-OB)",
                    customerName = custName,
                    desc = buildDesc(desc, "Helg-OB (${b.date} $segStart–$segEnd)"),
                    isWeekendOb = true
                )
                "morning_ob" -> ReportRow(
                    entryId = entry.id,
                    date = b.date,
                    startTime = segStart,
                    endTime = segEnd,
                    breakMin = b.breakMins,
                    normalHours = 0.0,
                    obHours = hoursVal,
                    otHours = 0.0,
                    hours = hoursVal,
                    rate = weekdayObRate,
                    cost = round2(hoursVal * weekdayObRate),
                    project = if (proj.isNotBlank()) "$proj (Morgon-OB)" else "(Morgon-OB)",
                    customerName = custName,
                    desc = buildDesc(desc, "Morgon-OB (${b.date} $segStart–$segEnd)"),
                    isWeekendOb = false
                )
                "evening_ob" -> ReportRow(
                    entryId = entry.id,
                    date = b.date,
                    startTime = segStart,
                    endTime = segEnd,
                    breakMin = b.breakMins,
                    normalHours = 0.0,
                    obHours = hoursVal,
                    otHours = 0.0,
                    hours = hoursVal,
                    rate = weekdayObRate,
                    cost = round2(hoursVal * weekdayObRate),
                    project = if (proj.isNotBlank()) "$proj (Vardags-OB)" else "(Vardags-OB)",
                    customerName = custName,
                    desc = buildDesc(desc, "Kvälls-OB (${b.date} $segStart–$segEnd)"),
                    isWeekendOb = false
                )
                else -> ReportRow(
                    entryId = entry.id,
                    date = b.date,
                    startTime = segStart,
                    endTime = segEnd,
                    breakMin = b.breakMins,
                    normalHours = hoursVal,
                    obHours = 0.0,
                    otHours = 0.0,
                    hours = hoursVal,
                    rate = baseRate,
                    cost = round2(hoursVal * baseRate),
                    project = proj,
                    customerName = custName,
                    desc = desc,
                    isWeekendOb = false
                )
            }
        }
    }

    fun buildReportRowsWithOvertime(
        filteredEntries: List<TimeEntry>,
        allEntries: List<TimeEntry>,
        customers: List<Customer>
    ): List<ReportRow> {
        val customerMap = customers.associateBy { it.id }
        val groups = allEntries.groupBy { Pair(it.customerId, YearMonth.from(it.date)) }
        val entryRowsMap = mutableMapOf<String, List<ReportRow>>()

        for ((key, groupEntries) in groups) {
            val (customerId, _) = key
            val customer = customerMap[customerId]
            val baseRate = customer?.hourlyRate ?: 0.0
            val otEnabled = customer?.otEnabled == true
            val monthlyCap = if (otEnabled) customer!!.otMonthlyCap else Double.POSITIVE_INFINITY

            val sorted = groupEntries.sortedWith(
                compareBy({ it.date }, { it.startTime ?: LocalTime.MIDNIGHT })
            )

            var accumulatedHours = 0.0

            for (entry in sorted) {
                val parts = splitEntryForReport(entry, customer)
                val finalRows = mutableListOf<ReportRow>()

                for (p in parts) {
                    val segHours = p.hours
                    if (!otEnabled || segHours <= 0.0 || customer == null) {
                        finalRows += p
                        continue
                    }

                    val prevAcc = accumulatedHours
                    accumulatedHours = ((accumulatedHours + segHours) * 10000.0).roundToInt() / 10000.0

                    if (accumulatedHours <= monthlyCap) {
                        finalRows += p
                    } else {
                        val withinCapHours = maxOf(0.0, ((monthlyCap - prevAcc) * 10000.0).roundToInt() / 10000.0)
                        val overCapHours = ((segHours - withinCapHours) * 10000.0).roundToInt() / 10000.0

                        if (withinCapHours > 0.0) {
                            val wHours = round2(withinCapHours)
                            finalRows += p.copy(
                                normalHours = if (p.normalHours > 0.0) wHours else 0.0,
                                obHours = if (p.obHours > 0.0) wHours else 0.0,
                                hours = wHours,
                                cost = round2(withinCapHours * p.rate)
                            )
                        }

                        if (overCapHours > 0.0) {
                            val isQual = isWeekend(p.date) || p.isWeekendOb
                            val otRate = getOvertimeHourlyRate(baseRate, customer, isQual)
                            val otLabel = if (isQual) "Kval. övertid" else "Övertid"
                            val oHours = round2(overCapHours)
                            finalRows += ReportRow(
                                entryId = entry.id,
                                date = p.date,
                                startTime = p.startTime,
                                endTime = p.endTime,
                                breakMin = if (withinCapHours == 0.0) p.breakMin else 0,
                                normalHours = 0.0,
                                obHours = 0.0,
                                otHours = oHours,
                                hours = oHours,
                                rate = otRate,
                                cost = round2(overCapHours * otRate),
                                project = if (entry.project.isNotBlank()) "${entry.project} ($otLabel)" else "($otLabel)",
                                customerName = p.customerName,
                                desc = buildDesc(entry.desc, "$otLabel över ${monthlyCap.toInt()}h/mån"),
                                isWeekendOb = isQual
                            )
                        }
                    }
                }

                entryRowsMap[entry.id] = finalRows
            }
        }

        return filteredEntries.flatMap { entry ->
            entryRowsMap[entry.id] ?: splitEntryForReport(entry, customerMap[entry.customerId])
        }
    }

    fun calculateEntryCost(entry: TimeEntry, customer: Customer?): EntryCostSummary {
        val breakMin = if (entry.breakMin > 0) entry.breakMin else entry.breaks.sumOf { it.durationMinutes().toInt() }
        val rate = customer?.hourlyRate ?: 0.0

        if (customer != null && customer.obEnabled && entry.startTime != null && entry.endTime != null) {
            val parts = splitEntryForReport(entry, customer)
            var splitCost = 0.0
            var splitHours = 0.0
            var normalHours = 0.0
            var weekdayObHours = 0.0
            var weekendObHours = 0.0
            var weekdayObCost = 0.0
            var weekendObCost = 0.0

            for (p in parts) {
                splitCost += p.cost
                splitHours += p.hours
                normalHours += p.normalHours
                if (p.obHours > 0.0) {
                    if (p.isWeekendOb) {
                        weekendObHours += p.obHours
                        weekendObCost += p.cost
                    } else {
                        weekdayObHours += p.obHours
                        weekdayObCost += p.cost
                    }
                }
            }

            if (splitHours > 0.0) {
                return EntryCostSummary(
                    totalHours = round2(splitHours),
                    normalHours = round2(normalHours),
                    obHours = round2(weekdayObHours + weekendObHours),
                    weekdayObHours = round2(weekdayObHours),
                    weekendObHours = round2(weekendObHours),
                    weekdayObCost = round2(weekdayObCost),
                    weekendObCost = round2(weekendObCost),
                    rate = rate,
                    cost = round2(splitCost),
                    breakMin = breakMin
                )
            }
        }

        val totalHours = round2(entry.hours)
        return EntryCostSummary(
            totalHours = totalHours,
            normalHours = totalHours,
            obHours = 0.0,
            weekdayObHours = 0.0,
            weekendObHours = 0.0,
            weekdayObCost = 0.0,
            weekendObCost = 0.0,
            rate = rate,
            cost = round2(totalHours * rate),
            breakMin = breakMin
        )
    }

    fun calculateMonthlyOvertimeMap(
        allEntries: List<TimeEntry>,
        customers: List<Customer>
    ): Map<String, EntryOvertimeSummary> {
        val customerMap = customers.associateBy { it.id }
        val groups = allEntries.groupBy { Pair(it.customerId, YearMonth.from(it.date)) }
        val result = mutableMapOf<String, EntryOvertimeSummary>()

        for ((key, groupEntries) in groups) {
            val (customerId, _) = key
            val customer = customerMap[customerId] ?: continue
            if (!customer.otEnabled) continue

            val monthlyCap = customer.otMonthlyCap
            val baseRate = customer.hourlyRate

            val sorted = groupEntries.sortedWith(
                compareBy({ it.date }, { it.startTime ?: LocalTime.MIDNIGHT })
            )

            var accumulatedHours = 0.0

            for (entry in sorted) {
                val parts = splitEntryForReport(entry, customer)
                var entryOtSimpleHours = 0.0
                var entryOtQualHours = 0.0
                var entryOtCost = 0.0
                var entryAdjustedTotalCost = 0.0

                for (p in parts) {
                    val segHours = p.hours
                    if (segHours <= 0.0) continue

                    val prevAcc = accumulatedHours
                    accumulatedHours = ((accumulatedHours + segHours) * 10000.0).roundToInt() / 10000.0

                    if (accumulatedHours <= monthlyCap) {
                        entryAdjustedTotalCost += p.cost
                    } else {
                        val withinCapHours = maxOf(0.0, ((monthlyCap - prevAcc) * 10000.0).roundToInt() / 10000.0)
                        val overCapHours = ((segHours - withinCapHours) * 10000.0).roundToInt() / 10000.0

                        val isQual = isWeekend(p.date) || p.isWeekendOb
                        val otRate = getOvertimeHourlyRate(baseRate, customer, isQual)

                        val keptPartCost = withinCapHours * p.rate
                        val otPartCost = overCapHours * otRate

                        if (isQual) {
                            entryOtQualHours += overCapHours
                        } else {
                            entryOtSimpleHours += overCapHours
                        }

                        entryOtCost += otPartCost
                        entryAdjustedTotalCost += (keptPartCost + otPartCost)
                    }
                }

                val totalOtHours = round2(entryOtSimpleHours + entryOtQualHours)
                if (totalOtHours > 0.0) {
                    result[entry.id] = EntryOvertimeSummary(
                        otHours = totalOtHours,
                        otSimpleHours = round2(entryOtSimpleHours),
                        otQualHours = round2(entryOtQualHours),
                        otCost = round2(entryOtCost),
                        adjustedTotalCost = round2(entryAdjustedTotalCost)
                    )
                }
            }
        }

        return result
    }

    private fun formatTime(time: LocalTime): String =
        "%02d:%02d".format(time.hour, time.minute)

    private fun formatMinuteOfDay(minute: Int): String {
        val clamped = minute.coerceIn(0, 1440)
        if (clamped == 1440) return "24:00"
        return "%02d:%02d".format(clamped / 60, clamped % 60)
    }

    private fun buildDesc(baseDesc: String, extra: String): String =
        if (baseDesc.isNotBlank()) "$baseDesc - $extra" else extra

    private fun round2(value: Double): Double =
        (value * 100.0).roundToInt() / 100.0
}
