# AWARE Barometer

[![jitpack-badge](https://jitpack.io/v/awareframework/com.aware.android.sensor.barometer.svg)](https://jitpack.io/#awareframework/com.aware.android.sensor.barometer)

The barometer sensor measures the ambient air pressure. Barometer can be leveraged to detect and predict short team changes in weather, for example drops in pressure indicate rain, while raises indicate good weather ahead.

## Public functions

### BarometerSensor

+ `startService(context: Context, config: BarometerConfig?)`: Starts the barometer sensor with the optional configuration.
+ `stopService(context: Context)`: Stops the service.
+ `currentInterval`: Data collection rate per second. (e.g. 5 samples per second)

### BarometerConfig

Class to hold the configuration of the sensor.

#### Fields

+ `debug: Boolean`: enable/disable logging to `Logcat`. (default = false)
+ `host: String`: Host for syncing the database. (default = null)
+ `key: String`: Encryption key for the database. (default = no encryption)
+ `host: String`: Host for syncing the database. (default = null)
+ `type: EngineDatabaseType`: Which db engine to use for saving data. (default = NONE)
+ `path: String`: Path of the database.
+ `deviceId: String`: Id of the device that will be associated with the events and the sensor. (default = "")
+ `sensorObserver: BarometerObserver`: Callback for live data updates.
+ `interval: Int`: Data samples to collect per second. (default = 5)
+ `period: Float`: Period to save data in minutes. (default = 1)
+ `threshold: Double`: If set, do not record consecutive points if change in value is less than the set value.

## Broadcasts

+ `BarometerSensor.ACTION_AWARE_BAROMETER` fired when barometer saved data to db after the period ends.

## Data Representations

### Barometer Sensor

Contains the hardware sensor capabilities in the mobile device.

| Field      | Type   | Description                                  |
| ---------- | ------ | -------------------------------------------- |
| maxRange   | Float  | Maximum sensor value possible                |
| minDelay   | Float  | Minimum sampling delay in microseconds       |
| name       | String | Sensor’s name                               |
| power      | Float  | Sensor’s power drain in mA                  |
| resolution | Float  | Sensor’s resolution in sensor’s units      |
| type       | String | Sensor’s type                               |
| vendor     | String | Sensor’s vendor                             |
| version    | String | Sensor’s version                            |
| deviceId   | String | AWARE device UUID                            |
| timestamp  | Long   | unixtime milliseconds since 1970             |
| timezone   | Int    | [Raw timezone offset][1] of the device       |
| os         | String | Operating system of the device (ex. android) |

[1]: https://developer.android.com/reference/java/util/TimeZone#getRawOffset()

### Barometer Data

Contains the raw sensor data.

| Field     | Type   | Description                                                      |
| --------- | ------ | ---------------------------------------------------------------- |
| pressure  | Float  | the ambient air pressure in mbar/hPa units (depends on hardware) |
| accuracy  | Int    | Sensor’s accuracy level (see [SensorManager][2])                |
| label     | String | Customizable label. Useful for data calibration or traceability  |
| deviceId  | String | AWARE device UUID                                                |
| timestamp | Long   | unixtime milliseconds since 1970                                 |
| timezone  | Int    | [Raw timezone offset][1] of the device                           |
| os        | String | Operating system of the device (ex. android)                     |

[1]: https://developer.android.com/reference/java/util/TimeZone#getRawOffset()
[2]: http://developer.android.com/reference/android/hardware/SensorManager.html

## Example usage

```kotlin
// To start the service.
BarometerSensor.startService(appContext, BarometerSensor.BarometerConfig().apply {
    sensorObserver = object : BarometerSensor.BarometerObserver {
        override fun onDataChanged(data: BarometerData) {
            // your code here...
        }
    }
    dbType = Engine.DatabaseType.ROOM
    debug = true
    // more configuration...
})

// To stop the service
BarometerSensor.stopService(appContext)
```

## License

Copyright (c) 2018 AWARE Mobile Context Instrumentation Middleware/Framework (http://www.awareframework.com)

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
