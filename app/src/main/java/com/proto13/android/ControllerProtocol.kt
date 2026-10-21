package com.proto13.android

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object ControllerConfig {
    const val BROKER_HOST = "192.168.0.55"
    const val BROKER_PORT = 1883
    const val DEVICE_ID = "android-01"
    const val CONTROLLER_ID = "controller-01"
    const val MAX_CAPTURE_ATTEMPTS = 3
    const val PROGRESS_INTERVAL_MS = 5_000L
    const val MIN_MATCH_CONFIDENCE = 0.55
}

object ControllerTopics {
    val request = "proto13/android/${ControllerConfig.DEVICE_ID}/request"
    val status = "proto13/android/${ControllerConfig.DEVICE_ID}/status"
    val statusRequest = "proto13/android/${ControllerConfig.DEVICE_ID}/status/request"
    val captureResult = "proto13/controller/${ControllerConfig.CONTROLLER_ID}/capture/result"
}

enum class DeviceState(val wire: String) {
    STARTING("starting"),
    READY("ready"),
    BUSY("busy"),
    DEGRADED("degraded"),
}

enum class CaptureStatus(val wire: String) {
    ACCEPTED("accepted"),
    IN_PROGRESS("in_progress"),
    SUCCESS("success"),
    RETRY("retry"),
    FAILURE("failure"),
}

sealed interface IncomingMessage {
    data class Capture(val requestId: String, val mode: String?) : IncomingMessage
    data class StatusCheck(val requestId: String?, val diagnostics: Boolean) : IncomingMessage
}

fun parseIncoming(payload: String): IncomingMessage? {
    val json = try {
        JSONObject(payload)
    } catch (_: Exception) {
        return null
    }
    val requestId = json.optString("request_id").ifBlank { null }
    return when (json.optString("type")) {
        "capture_request" ->
            requestId?.let { IncomingMessage.Capture(it, json.optString("mode").ifBlank { null }) }
        "run_status_check" -> IncomingMessage.StatusCheck(requestId, diagnostics = false)
        "run_diagnostics" -> IncomingMessage.StatusCheck(requestId, diagnostics = true)
        else -> null
    }
}

private fun now(): String = Instant.now().toString()

fun statusPayload(
    state: DeviceState,
    online: Boolean = true,
    requestId: String? = null,
    mode: String? = null,
): String = JSONObject().apply {
    put("type", "system_status")
    put("request_id", requestId ?: JSONObject.NULL)
    put("device_id", ControllerConfig.DEVICE_ID)
    put("online", online)
    put("state", state.wire)
    put("mode", mode ?: JSONObject.NULL)
    put("timestamp", now())
}.toString()

fun captureResultPayload(
    requestId: String,
    status: CaptureStatus,
    mode: String? = null,
    cardName: String? = null,
    confidence: Double? = null,
    error: String? = null,
): String = JSONObject().apply {
    put("type", "capture_result")
    put("request_id", requestId)
    put("device_id", ControllerConfig.DEVICE_ID)
    put("status", status.wire)
    put("mode", mode ?: JSONObject.NULL)
    put("card_name", cardName ?: JSONObject.NULL)
    put("match_confidence", confidence ?: JSONObject.NULL)
    put("error", error ?: JSONObject.NULL)
    put("processed_at", now())
}.toString()

data class DiagnosticCheck(val name: String, val ok: Boolean, val detail: String = "")

fun diagnosticsPayload(requestId: String?, checks: List<DiagnosticCheck>): String =
    JSONObject().apply {
        put("type", "diagnostics_result")
        put("request_id", requestId ?: JSONObject.NULL)
        put("device_id", ControllerConfig.DEVICE_ID)
        put("status", if (checks.all { it.ok }) "success" else "failure")
        put("checks", JSONArray().apply {
            checks.forEach {
                put(JSONObject().put("name", it.name).put("ok", it.ok).put("detail", it.detail))
            }
        })
        put("timestamp", now())
    }.toString()
