package com.awareframework.android.sensor.barometer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.awareframework.android.core.db.model.DbSyncConfig
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
class BarometerSensor : AwareSensor(), SensorEventListener {

    companion object {
        const val TAG = "AWAREBarometerSensor"

        const val ACTION_AWARE_BAROMETER = "ACTION_AWARE_BAROMETER"

        const val ACTION_AWARE_BAROMETER_START = "com.awareframework.android.sensor.barometer.SENSOR_START"
        const val ACTION_AWARE_BAROMETER_STOP = "com.awareframework.android.sensor.barometer.SENSOR_STOP"

        const val ACTION_AWARE_BAROMETER_SET_LABEL = "com.awareframework.android.sensor.barometer.ACTION_AWARE_BAROMETER_SET_LABEL"
        const val EXTRA_LABEL = "label"

        const val ACTION_AWARE_BAROMETER_SYNC = "com.awareframework.android.sensor.barometer.SENSOR_SYNC"

        val CONFIG = BarometerConfig()

        var currentInterval: Int = 0
            private set

        fun startService(context: Context, config: BarometerConfig? = null) {
            if (config != null)
                CONFIG.replaceWith(config)
            context.startService(Intent(context, BarometerSensor::class.java))
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, BarometerSensor::class.java))
        }
    }

    private lateinit var mSensorManager: SensorManager
    private var mPressure: Sensor? = null
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler

    private var lastSave = 0L

    private var lastValue: Float = 0f
    private var lastTimestamp: Long = 0
    private var lastSavedAt: Long = 0

    private val dataBuffer = ArrayList<BarometerData>()

    private var dataCount: Int = 0
    private var lastDataCountTimestamp: Long = 0

    private val barometerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_AWARE_BAROMETER_SET_LABEL -> {
                    intent.getStringExtra(EXTRA_LABEL)?.let {
                        CONFIG.label = it
                    }
                }

                ACTION_AWARE_BAROMETER_SYNC -> onSync(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        initializeDbEngine(CONFIG)

        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        mPressure = mSensorManager.getDefaultSensor(TYPE_PRESSURE)

        sensorThread = HandlerThread(TAG)
        sensorThread.start()

        sensorHandler = Handler(sensorThread.looper)

        registerReceiver(barometerReceiver, IntentFilter().apply {
            addAction(ACTION_AWARE_BAROMETER_SET_LABEL)
            addAction(ACTION_AWARE_BAROMETER_SYNC)
        })

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

        unregisterReceiver(barometerReceiver)

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

        dbEngine?.save(device, BarometerDevice.TABLE_NAME, 0)

        logd("Barometer sensor info: $device")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //We log current accuracy on the sensor changed event
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        val currentTime = System.currentTimeMillis()

        if (currentTime - lastDataCountTimestamp >= 1000) {
            currentInterval = dataCount
            dataCount = 0
            lastDataCountTimestamp = currentTime
        }

        if (currentTime - lastTimestamp < (900.0 / CONFIG.interval)) {
            // skip this event
            return
        }
        lastTimestamp = currentTime

        if (CONFIG.threshold > 0
                && Math.abs(event.values[0] - lastValue) < CONFIG.threshold) {
            return
        }
        lastValue = event.values[0]

        val data = BarometerData().apply {
            timestamp = currentTime
            eventTimestamp = event.timestamp
            deviceId = CONFIG.deviceId
            pressure = event.values[0]
            accuracy = event.accuracy
            label = CONFIG.label
        }

        CONFIG.sensorObserver?.onDataChanged(data)

        dataBuffer.add(data)
        dataCount++

        if (currentTime - lastSavedAt < CONFIG.period * 60000) { // convert minute to ms
            // not ready to save yet
            return
        }
        lastSavedAt = currentTime

        val dataBuffer = this.dataBuffer.toTypedArray()
        this.dataBuffer.clear()

        try {
            logd("Saving buffer to database.")
            dbEngine?.save(dataBuffer, BarometerData.TABLE_NAME)

            sendBroadcast(Intent(ACTION_AWARE_BAROMETER))
        } catch (e: Exception) {
            e.message ?: logw(e.message!!)
            e.printStackTrace()
        }
    }

    override fun onSync(intent: Intent?) {
        dbEngine?.startSync(BarometerData.TABLE_NAME)
        dbEngine?.startSync(BarometerDevice.TABLE_NAME, DbSyncConfig(removeAfterSync = false))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    interface SensorObserver {
        fun onDataChanged(data: BarometerData)
    }

    data class BarometerConfig(
            /**
             * For real-time observation of the sensor data collection.
             */
            var sensorObserver: SensorObserver? = null,

            /**
             * Barometer interval in hertz per second: e.g.
             *
             * 0 - fastest
             * 1 - sample per second
             * 5 - sample per second
             * 20 - sample per second
             */
            var interval: Int = 5,

            /**
             * Period to save data in minutes. (optional)
             */
            var period: Float = 1f,

            /**
             * Barometer threshold (float).  Do not record consecutive points if
             * change in value is less than the set value.
             */
            var threshold: Double = 0.0

            // TODO wakelock?

    ) : SensorConfig(dbPath = "aware_barometer") {

        override fun <T : SensorConfig> replaceWith(config: T) {
            super.replaceWith(config)

            if (config is BarometerConfig) {
                sensorObserver = config.sensorObserver
                interval = config.interval
                period = config.period
                threshold = config.threshold
            }
        }
    }

    class BarometerSensorBroadcastReceiver : AwareSensor.SensorBroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            context ?: return

            logd("Sensor broadcast received. action: " + intent?.action)

            when (intent?.action) {
                SENSOR_START_ENABLED -> {
                    logd("Sensor enabled: " + CONFIG.enabled)

                    if (CONFIG.enabled) {
                        startService(context)
                    }
                }

                ACTION_AWARE_BAROMETER_STOP,
                SENSOR_STOP_ALL -> {
                    logd("Stopping sensor.")
                    stopService(context)
                }

                ACTION_AWARE_BAROMETER_START -> {
                    startService(context)
                }
            }
        }
    }
}

private fun logd(text: String) {
    if (BarometerSensor.CONFIG.debug) Log.d(BarometerSensor.TAG, text)
}

private fun logw(text: String) {
    Log.w(BarometerSensor.TAG, text)
}