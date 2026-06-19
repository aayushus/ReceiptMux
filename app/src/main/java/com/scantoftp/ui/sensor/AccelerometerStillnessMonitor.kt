package com.scantoftp.ui.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.sqrt

@Composable
fun AccelerometerStillnessMonitor(
    context: Context,
    onStillnessChanged: (Boolean) -> Unit,
) {
    val callback = rememberUpdatedState(onStillnessChanged)
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            callback.value(true)
            onDispose { }
        } else {
            val gravity = FloatArray(3)
            val samples = ArrayDeque<Float>()

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val alpha = 0.8f
                    for (index in 0..2) {
                        gravity[index] = alpha * gravity[index] + (1f - alpha) * event.values[index]
                    }
                    val linearX = event.values[0] - gravity[0]
                    val linearY = event.values[1] - gravity[1]
                    val linearZ = event.values[2] - gravity[2]
                    val magnitude = sqrt((linearX * linearX) + (linearY * linearY) + (linearZ * linearZ))

                    samples.addLast(magnitude)
                    if (samples.size > 24) samples.removeFirst()
                    if (samples.size < 8) return

                    val mean = samples.average().toFloat()
                    val variance = samples.map { delta -> (delta - mean) * (delta - mean) }.average().toFloat()
                    callback.value(variance < 0.12f && mean < 0.65f)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
}
