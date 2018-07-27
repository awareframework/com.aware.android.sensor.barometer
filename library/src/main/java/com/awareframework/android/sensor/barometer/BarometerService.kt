package com.awareframework.android.sensor.barometer

import android.content.Intent
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_PRESSURE
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import com.awareframework.android.core.AwareSensor
import com.awareframework.android.core.model.SensorConfig
import com.awareframework.android.sensor.barometer.model.BarometerData
import com.awareframework.android.sensor.barometer.model.BarometerDevice

/**
 * AWARE Barometer module
 * - Ambient pressure raw data, in mbar
 * - Ambient pressure sensor information
 *
 * @author  sercant
 * @date 27/07/2018
 */
class BarometerService : AwareSensor(), SensorEventListener {

    companion object {
        const val TAG = "AWAREBarometerService"

        const val ACTION_AWARE_BAROMETER = "ACTION_AWARE_BAROMETER"

        val CONFIG = BarometerConfig()
    }

    lateinit var mSensorManager: SensorManager
    var mPressure: Sensor? = null
    lateinit var sensorThread: HandlerThread
    lateinit var sensorHandler: Handler

    var lastSave = 0L

    override fun onCreate() {
        super.onCreate()

        initializeDbEngine(CONFIG)

        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mPressure = mSensorManager.getDefaultSensor(TYPE_PRESSURE)

        sensorThread = HandlerThread(TAG)
        sensorThread.start()

        sensorHandler = Handler(sensorThread.looper)

        logd("Barometer service created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        return if (mPressure != null) {
            saveSensorDevice(mPressure)

            val samplingFreqUs = if (CONFIG.interval > 0) 1000000 / CONFIG.interval else 0
            mSensorManager.registerListener(
                    this,
                    mPressure,
                    samplingFreqUs,
                    sensorHandler)

            lastSave = System.currentTimeMillis()

            logd("Barometer service active: ${CONFIG.interval} samples per second.")

            START_STICKY
        } else {
            logw("This device doesn't have a barometer!")

            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        sensorHandler.removeCallbacksAndMessages(null)
        mSensorManager.unregisterListener(this, mPressure)
        sensorThread.quit()

        dbEngine?.close()

        logd("Barometer service terminated...")
    }

    private fun saveSensorDevice(sensor: Sensor?) {
        sensor ?: return

        val device = BarometerDevice().apply {
            deviceId = CONFIG.deviceId
            timestamp = System.currentTimeMillis()
            maxRange = sensor.maximumRange
            minDelay = sensor.minDelay.toFloat()
            name = sensor.name
            power = sensor.power
            resolution = sensor.resolution
            type = sensor.type.toString()
            vendor = sensor.vendor
            version = sensor.version.toString()
        }

        dbEngine?.save(device, BarometerDevice.TABLE_NAME)

        logd("Barometer sensor info: $device")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //We log current accuracy on the sensor changed event
    }

    override fun onSensorChanged(event: SensorEvent?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSync(intent: Intent?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBind(intent: Intent?): IBinder? = null

    interface BarometerObserver {
        fun onBarometerChanged(data: BarometerData)
    }

    data class BarometerConfig(
            var sensorObserver: BarometerObserver? = null,

            /**
             * Barometer interval in hertz per second: e.g.
             *
             * 0 - fastest
             * 1 - sample per second
             * 5 - sample per second
             * 20 - sample per second
             */
            var interval: Int = 5,

            var threshold: Double = 0.0
    ) : SensorConfig(dbPath = "aware_barometer") {

        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is BarometerConfig) {
                sensorObserver = config.sensorObserver
                interval = config.interval
            }
        }
    }
}

private fun logd(text: String) {
    if (BarometerService.CONFIG.debug) Log.d(BarometerService.TAG, text)
}

private fun logw(text: String) {
    Log.w(BarometerService.TAG, text)
}