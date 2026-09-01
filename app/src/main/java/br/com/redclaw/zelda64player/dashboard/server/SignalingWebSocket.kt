/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.server

import android.util.Log
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WebSocket endpoint for WebRTC signaling between the Android device and browser clients.
 *
 * This endpoint facilitates the exchange of SDP offers/answers and ICE candidates needed to
 * establish a peer-to-peer WebRTC connection for real-time video/audio streaming of the emulator
 * framebuffer.
 *
 * Protocol (JSON messages over WebSocket text frames):
 *
 * Browser -> App: { "type": "join", "hackId": "..." } — Request to start streaming { "type":
 * "offer", "sdp": "..." } — SDP offer from browser { "type": "answer", "sdp": "..." } — SDP answer
 * from browser { "type": "ice_candidate", "candidate": {...} } — ICE candidate from browser
 *
 * App -> Browser: { "type": "ready", "resolution": "640x480" } — Stream is ready { "type": "offer",
 * "sdp": "..." } — SDP offer from Android { "type": "answer", "sdp": "..." } — SDP answer from
 * Android { "type": "ice_candidate", "candidate": {...} } — ICE candidate from Android { "type":
 * "error", "message": "..." }
 */
internal fun Route.webSocketSignaling() {

    webSocket("/ws/signaling") {
        Log.d(TAG, "Signaling client connected")
        DashboardManager.incrementClients()

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleSignalingMessage(text, this)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signaling error", e)
        } finally {
            DashboardManager.decrementClients()
            Log.d(TAG, "Signaling client disconnected")
        }
    }
}

private suspend fun handleSignalingMessage(text: String, session: DefaultWebSocketSession) {
    val message =
            try {
                Json.decodeFromString<SignalingMessage>(text)
            } catch (e: Exception) {
                session.outgoing.send(
                        Frame.Text(
                                Json.encodeToString(
                                        SignalingError(message = "Invalid JSON message")
                                )
                        )
                )
                return
            }

    when (message.type) {
        "join" -> {
            val hackId = message.hackId
            Log.d(TAG, "Client requested stream for hackId: $hackId")
            // The actual WebRTC streaming is initiated by WebRtcStreamer on the
            // Android side. This signaling message triggers the stream start.
            // The streamer will send its SDP offer back through this session.
            session.outgoing.send(
                    Frame.Text(
                            Json.encodeToString(
                                    SignalingReady(type = "ready", resolution = "640x480")
                            )
                    )
            )
        }
        "offer" -> {
            // Forward SDP offer to the WebRtcStreamer for processing.
            Log.d(TAG, "Received SDP offer from browser")
            // WebRtcStreamer.processOffer(message.sdp!!, session)
        }
        "answer" -> {
            // SDP answer from browser in response to Android's offer.
            Log.d(TAG, "Received SDP answer from browser")
            // WebRtcStreamer.processAnswer(message.sdp!!)
        }
        "ice_candidate" -> {
            // ICE candidate from browser.
            Log.d(TAG, "Received ICE candidate from browser")
            // WebRtcStreamer.addIceCandidate(message.candidate!!)
        }
    }
}

private const val TAG = "Signaling"

@Serializable
internal data class SignalingMessage(
        val type: String,
        val hackId: String? = null,
        val sdp: String? = null,
        val candidate: IceCandidateData? = null,
        val resolution: String? = null
)

@Serializable
internal data class IceCandidateData(
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null,
        val candidate: String? = null
)

@Serializable internal data class SignalingReady(val type: String, val resolution: String)

@Serializable internal data class SignalingError(val type: String = "error", val message: String)
