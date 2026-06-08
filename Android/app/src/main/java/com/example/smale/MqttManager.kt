package com.example.smale

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.eclipse.paho.client.mqttv3.*

class MqttManager {

    companion object {
        private const val SERVER_URI = "tcp://103.172.204.106:1883"
    }

    private var client: MqttClient? = null

    var onMessage: ((topic: String, message: String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // ================= CONNECT (FIX THREAD) =================
    fun connect() {

        Thread {

            try {

                if (client != null && client!!.isConnected) {
                    Handler(Looper.getMainLooper()).post {
                        onConnected?.invoke()
                    }
                    return@Thread
                }

                client = MqttClient(
                    SERVER_URI,
                    MqttClient.generateClientId(),
                    null
                )

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 10
                    keepAliveInterval = 20
                    isAutomaticReconnect = true
                }

                client?.setCallback(object : MqttCallback {

                    override fun connectionLost(cause: Throwable?) {
                        Log.e("MQTT", "Connection Lost: ${cause?.message}")
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        if (topic == null || message == null) return

                        Handler(Looper.getMainLooper()).post {
                            onMessage?.invoke(topic, message.toString())
                        }
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d("MQTT", "Delivered")
                    }
                })

                client?.connect(options)

                Handler(Looper.getMainLooper()).post {
                    onConnected?.invoke()
                }

                Log.d("MQTT", "CONNECTED SUCCESS")

            } catch (e: Exception) {

                Log.e("MQTT", "CONNECT ERROR: ${e.message}")

                Handler(Looper.getMainLooper()).post {
                    onError?.invoke(e.message ?: "unknown error")
                }
            }

        }.start()
    }

    // ================= SUBSCRIBE =================
    fun subscribe(topic: String) {
        try {
            if (client?.isConnected == true) {
                client?.subscribe(topic)
                Log.d("MQTT", "SUB: $topic")
            } else {
                Log.e("MQTT", "SUB FAILED - NOT CONNECTED")
            }
        } catch (e: Exception) {
            Log.e("MQTT", "SUB ERROR: ${e.message}")
        }
    }

    // ================= PUBLISH =================
    fun publish(topic: String, message: String): Boolean {
        return try {

            if (client?.isConnected != true) {
                Log.e("MQTT", "CLIENT NOT CONNECTED")
                return false
            }

            client?.publish(
                topic,
                MqttMessage(message.toByteArray())
            )

            Log.d("MQTT", "PUB SUCCESS: $message")
            true

        } catch (e: Exception) {
            Log.e("MQTT", "PUB ERROR: ${e.message}")
            false
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
            Log.d("MQTT", "DISCONNECTED")
        } catch (e: Exception) {
            Log.e("MQTT", "DISCONNECT ERROR: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return client?.isConnected == true
    }
}