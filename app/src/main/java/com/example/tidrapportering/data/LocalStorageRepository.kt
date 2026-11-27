package com.example.tidrapportering.data

import android.content.Context
import com.example.tidrapportering.model.ActiveShift
import com.example.tidrapportering.model.BreakPeriod
import com.example.tidrapportering.model.Customer
import com.example.tidrapportering.model.RateType
import com.example.tidrapportering.model.ShiftStatus
import com.example.tidrapportering.model.TimeEntry
import com.example.tidrapportering.ui.UserProfileInfo
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

class LocalStorageRepository(context: Context) {

    private val prefs = context.getSharedPreferences("tidrapportering_prefs", Context.MODE_PRIVATE)

    fun saveCustomers(customers: List<Customer>) {
        val arr = JSONArray()
        for (c in customers) {
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("hourlyRate", c.hourlyRate)
                    put("nightWorkExempt", c.nightWorkExempt)
                    put("obEnabled", c.obEnabled)
                    put("obStartTime", c.obStartTime.toString())
                    put("obEndTime", c.obEndTime.toString())
                    put("obRateType", c.obRateType.name)
                    put("obRateValue", c.obRateValue)
                    put("obWeekendEnabled", c.obWeekendEnabled)
                    put("obWeekendAllDay", c.obWeekendAllDay)
                    put("obWeekendFridayStart", c.obWeekendFridayStart.toString())
                    put("obWeekendSundayEnd", c.obWeekendSundayEnd.toString())
                    put("obWeekendStartTime", c.obWeekendStartTime.toString())
                    put("obWeekendEndTime", c.obWeekendEndTime.toString())
                    put("obWeekendRateType", c.obWeekendRateType.name)
                    put("obWeekendRateValue", c.obWeekendRateValue)
                    put("otEnabled", c.otEnabled)
                    put("otMonthlyCap", c.otMonthlyCap)
                    put("otRateType", c.otRateType.name)
                    put("otSimpleValue", c.otSimpleValue)
                    put("otQualValue", c.otQualValue)
                    put("notes", c.notes)
                }
            )
        }
        prefs.edit().putString("customers_json", arr.toString()).apply()
    }

    fun loadCustomers(fallback: List<Customer>): List<Customer> {
        val raw = prefs.getString("customers_json", null) ?: return fallback
        return runCatching {
            val arr = JSONArray(raw)
            if (arr.length() == 0) return fallback
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Customer(
                            id = o.optString("id", ""),
                            name = o.optString("name", ""),
                            hourlyRate = o.optDouble("hourlyRate", 0.0),
                            nightWorkExempt = o.optBoolean("nightWorkExempt", false),
                            obEnabled = o.optBoolean("obEnabled", false),
                            obStartTime = parseLocalTimeOr(o.optNullableString("obStartTime"), LocalTime.of(18, 0)),
                            obEndTime = parseLocalTimeOr(o.optNullableString("obEndTime"), LocalTime.of(6, 0)),
                            obRateType = parseRateTypeOr(o.optNullableString("obRateType"), RateType.MULTIPLIER),
                            obRateValue = o.optDouble("obRateValue", 1.5),
                            obWeekendEnabled = o.optBoolean("obWeekendEnabled", false),
                            obWeekendAllDay = o.optBoolean("obWeekendAllDay", true),
                            obWeekendFridayStart = parseLocalTimeOr(o.optNullableString("obWeekendFridayStart"), LocalTime.of(21, 0)),
                            obWeekendSundayEnd = parseLocalTimeOr(o.optNullableString("obWeekendSundayEnd"), LocalTime.MIDNIGHT),
                            obWeekendStartTime = parseLocalTimeOr(o.optNullableString("obWeekendStartTime"), LocalTime.MIDNIGHT),
                            obWeekendEndTime = parseLocalTimeOr(o.optNullableString("obWeekendEndTime"), LocalTime.of(23, 59)),
                            obWeekendRateType = parseRateTypeOr(o.optNullableString("obWeekendRateType"), RateType.MULTIPLIER),
                            obWeekendRateValue = o.optDouble("obWeekendRateValue", 2.0),
                            otEnabled = o.optBoolean("otEnabled", false),
                            otMonthlyCap = o.optDouble("otMonthlyCap", 160.0),
                            otRateType = parseRateTypeOr(o.optNullableString("otRateType"), RateType.MULTIPLIER),
                            otSimpleValue = o.optDouble("otSimpleValue", 1.5),
                            otQualValue = o.optDouble("otQualValue", 2.0),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }
        }.getOrDefault(fallback)
    }

    fun saveEntries(entries: List<TimeEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            val breaksArr = JSONArray()
            for (b in e.breaks) {
                breaksArr.put(
                    JSONObject().apply {
                        put("start", b.start?.toString() ?: JSONObject.NULL)
                        put("end", b.end?.toString() ?: JSONObject.NULL)
                    }
                )
            }
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("customerId", e.customerId)
                    put("project", e.project)
                    put("date", e.date.toString())
                    put("startTime", e.startTime?.toString() ?: JSONObject.NULL)
                    put("endTime", e.endTime?.toString() ?: JSONObject.NULL)
                    put("hours", e.hours)
                    put("breakMin", e.breakMin)
                    put("breaks", breaksArr)
                    put("desc", e.desc)
                }
            )
        }
        prefs.edit().putString("entries_json", arr.toString()).apply()
    }

    fun loadEntries(): List<TimeEntry> {
        val raw = prefs.getString("entries_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val dateStr = o.optNullableString("date") ?: continue
                    val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
                    val breaksList = buildList {
                        val bArr = o.optJSONArray("breaks")
                        if (bArr != null) {
                            for (j in 0 until bArr.length()) {
                                val bo = bArr.getJSONObject(j)
                                val s = bo.optNullableString("start")?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                                val endT = bo.optNullableString("end")?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                                add(BreakPeriod(s, endT))
                            }
                        }
                    }
                    add(
                        TimeEntry(
                            id = o.optString("id", ""),
                            customerId = o.optString("customerId", ""),
                            project = o.optString("project", ""),
                            date = date,
                            startTime = o.optNullableString("startTime")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                            endTime = o.optNullableString("endTime")?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                            hours = o.optDouble("hours", 0.0),
                            breakMin = o.optInt("breakMin", 0),
                            breaks = breaksList,
                            desc = o.optString("desc", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveUserProfile(profile: UserProfileInfo) {
        prefs.edit().putString(
            "user_profile_json",
            JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("email", profile.email)
                put("phone", profile.phone)
                put("address", profile.address)
            }.toString()
        ).apply()
    }

    fun loadUserProfile(fallback: UserProfileInfo): UserProfileInfo {
        val raw = prefs.getString("user_profile_json", null) ?: return fallback
        return runCatching {
            val o = JSONObject(raw)
            UserProfileInfo(
                id = o.optNullableString("id") ?: fallback.id,
                name = o.optNullableString("name") ?: fallback.name,
                email = o.optNullableString("email") ?: fallback.email,
                phone = o.optNullableString("phone") ?: fallback.phone,
                address = o.optNullableString("address") ?: fallback.address
            )
        }.getOrDefault(fallback)
    }

    fun saveActiveShift(shift: ActiveShift?) {
        if (shift == null) {
            prefs.edit().remove("active_shift_json").apply()
            return
        }
        prefs.edit().putString(
            "active_shift_json",
            JSONObject().apply {
                put("customerId", shift.customerId)
                put("customerName", shift.customerName)
                put("startEpochMs", shift.startEpochMs)
                put("status", shift.status.name)
                put("accumulatedWorkMs", shift.accumulatedWorkMs)
                put("accumulatedBreakMs", shift.accumulatedBreakMs)
                put("currentSegmentStartMs", shift.currentSegmentStartMs)
                put("breakTargetMins", shift.breakTargetMins)
            }.toString()
        ).apply()
    }

    fun loadActiveShift(): ActiveShift? {
        val raw = prefs.getString("active_shift_json", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val status = parseShiftStatusOr(o.optNullableString("status"), ShiftStatus.WORKING)
            val startMs = o.getLong("startEpochMs")
            ActiveShift(
                customerId = o.getString("customerId"),
                customerName = o.optString("customerName", ""),
                startEpochMs = startMs,
                status = status,
                accumulatedWorkMs = o.optLong("accumulatedWorkMs", 0L),
                accumulatedBreakMs = o.optLong("accumulatedBreakMs", 0L),
                currentSegmentStartMs = o.optLong("currentSegmentStartMs", startMs),
                breakTargetMins = o.optInt("breakTargetMins", 30)
            )
        }.getOrNull()
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return if (v.isBlank() || v.equals("null", ignoreCase = true)) null else v
    }

    private fun parseLocalTimeOr(value: String?, default: LocalTime): LocalTime {
        if (value.isNullOrBlank()) return default
        return runCatching { LocalTime.parse(value) }.getOrDefault(default)
    }

    private fun parseRateTypeOr(value: String?, default: RateType): RateType {
        if (value.isNullOrBlank()) return default
        return runCatching { RateType.valueOf(value) }.getOrDefault(default)
    }

    private fun parseShiftStatusOr(value: String?, default: ShiftStatus): ShiftStatus {
        if (value.isNullOrBlank()) return default
        return runCatching { ShiftStatus.valueOf(value) }.getOrDefault(default)
    }
}
