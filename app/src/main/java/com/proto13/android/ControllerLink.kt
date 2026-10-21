package com.proto13.android

import android.os.Handler
import android.os.Looper
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * MQTT connection to the Proto13 controller. Callbacks are delivered on the main thread.
 * Reconnects automatically; the last-will marks this device offline if the connection drops.
 */
class ControllerLink(
    private val onMessage: (IncomingMessage) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val currentStatus: () -> String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client: Mqtt3AsyncClient = Mqtt3Client.builder()
        .identifier("proto13-${ControllerConfig.DEVICE_ID}-${UUID.randomUUID().toString().take(8)}")
        .serverHost(ControllerConfig.BROKER_HOST)
        .serverPort(ControllerConfig.BROKER_PORT)
        .automaticReconnectWithDefaultConfig()
        .addConnectedListener {
            subscribeAll()
            publishStatus()
            mainHandler.post { onConnectionChanged(true) }
        }
        .addDisconnectedListener { mainHandler.post { onConnectionChanged(false) } }
        .buildAsync()

    fun start() {
        client.publishes(MqttGlobalPublishFilter.ALL) { publish: Mqtt3Publish ->
            val text = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
            parseIncoming(text)?.let { mainHandler.post { onMessage(it) } }
        }
        client.connectWith()
            .keepAlive(30)
            .willPublish()
            .topic(ControllerTopics.status)
            .payload(statusPayload(DeviceState.DEGRADED, online = false).toByteArray())
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(true)
            .applyWillPublish()
            .send()
    }

    fun stop() {
        if (client.state.isConnected) {
            publish(
                ControllerTopics.status,
                statusPayload(DeviceState.DEGRADED, online = false),
                retain = true,
            )
        }
        client.disconnect()
    }

    fun publishStatus(payload: String = currentStatus()) =
        publish(ControllerTopics.status, payload, retain = true)

    fun publish(topic: String, payload: String, retain: Boolean = false) {
        client.publishWith()
            .topic(topic)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(retain)
            .send()
    }

    private fun subscribeAll() {
        listOf(ControllerTopics.request, ControllerTopics.statusRequest).forEach { topic ->
            client.subscribeWith().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).send()
        }
    }
}
