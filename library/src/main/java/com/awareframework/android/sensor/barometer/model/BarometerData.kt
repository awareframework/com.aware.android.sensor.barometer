package com.awareframework.android.sensor.barometer.model

import com.awareframework.android.core.model.AwareObject
import com.google.gson.Gson

/**
 * Contains the raw sensor data.
 *
 * @author  sercant
 * @date 27/07/2018
 */
data class BarometerData(
    var pressure: Double = 0.0,
    var accuracy: Int = 0
) : AwareObject(jsonVersion = 1) {

    companion object {
        const val TABLE_NAME = "barometerData"
    }

    override fun toString(): String = Gson().toJson(this)
}