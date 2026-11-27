package com.example.tidrapportering.service

import android.content.Context
import com.example.tidrapportering.model.ActiveShift

object ShiftTimerService {
    private var currentShift: ActiveShift? = null

    fun syncShift(context: Context, shift: ActiveShift?) {
        currentShift = shift
    }

    fun getCurrentShift(): ActiveShift? = currentShift
}
