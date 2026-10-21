package com.proto13.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerProtocolTest {
    @Test
    fun topicsMatchControllerLayout() {
        assertEquals("proto13/android/android-01/request", ControllerTopics.request)
        assertEquals("proto13/android/android-01/status", ControllerTopics.status)
        assertEquals("proto13/android/android-01/status/request", ControllerTopics.statusRequest)
        assertEquals("proto13/controller/controller-01/capture/result", ControllerTopics.captureResult)
    }

    @Test
    fun parsesCaptureRequest() {
        val message = parseIncoming(
            """{"type":"capture_request","request_id":"r1","mode":"sort_only"}"""
        )
        assertEquals(IncomingMessage.Capture("r1", "sort_only"), message)
    }

    @Test
    fun captureRequestWithoutIdIsIgnored() {
        assertNull(parseIncoming("""{"type":"capture_request"}"""))
    }

    @Test
    fun parsesStatusRequests() {
        assertEquals(
            IncomingMessage.StatusCheck("s1", diagnostics = true),
            parseIncoming("""{"type":"run_diagnostics","request_id":"s1"}""")
        )
        assertEquals(
            IncomingMessage.StatusCheck(null, diagnostics = false),
            parseIncoming("""{"type":"run_status_check"}""")
        )
    }

    @Test
    fun garbageAndUnknownTypesAreIgnored() {
        assertNull(parseIncoming("not json"))
        assertNull(parseIncoming("""{"type":"something_else","request_id":"x"}"""))
    }

    @Test
    fun captureResultCarriesRequestIdAndStatus() {
        val json = JSONObject(
            captureResultPayload("r1", CaptureStatus.SUCCESS, "sort_only", "Lightning Bolt", 0.92)
        )
        assertEquals("capture_result", json.getString("type"))
        assertEquals("r1", json.getString("request_id"))
        assertEquals("android-01", json.getString("device_id"))
        assertEquals("success", json.getString("status"))
        assertEquals("Lightning Bolt", json.getString("card_name"))
        assertEquals(0.92, json.getDouble("match_confidence"), 0.0001)
    }

    @Test
    fun failureResultCarriesError() {
        val json = JSONObject(
            captureResultPayload("r2", CaptureStatus.FAILURE, error = "camera not ready")
        )
        assertEquals("failure", json.getString("status"))
        assertEquals("camera not ready", json.getString("error"))
        assertTrue(json.isNull("card_name"))
    }

    @Test
    fun offlineStatusIsMarkedOffline() {
        val json = JSONObject(statusPayload(DeviceState.DEGRADED, online = false))
        assertEquals(false, json.getBoolean("online"))
        assertEquals("degraded", json.getString("state"))
    }

    @Test
    fun diagnosticsFailWhenAnyCheckFails() {
        val json = JSONObject(
            diagnosticsPayload(
                "d1",
                listOf(DiagnosticCheck("camera_ready", true), DiagnosticCheck("reference_cards", false))
            )
        )
        assertEquals("failure", json.getString("status"))
        assertEquals(2, json.getJSONArray("checks").length())
    }

    @Test
    fun bestReferenceMatchScoresSimilarTitles() {
        val match = bestReferenceMatch("Lightning Bolt", listOf("Counterspell", "Lightning Bolt"))
        assertEquals("Lightning Bolt", match?.title)
        assertEquals(1.0, match!!.similarity, 0.0001)
        assertNull(bestReferenceMatch("", listOf("Counterspell")))
    }
}
