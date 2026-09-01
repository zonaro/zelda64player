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
import br.com.redclaw.zelda64player.repositories.Storage
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WebSocket endpoint for real-time SRAM synchronization between the Android app and browser-based
 * EmulatorJS sessions.
 *
 * Protocol (JSON messages over WebSocket text frames):
 *
 * Browser -> App: { "type": "subscribe", "hackId": "..." } — Subscribe to SRAM updates for a game {
 * "type": "unsubscribe", "hackId": "..." } — Unsubscribe from a game { "type": "sram_update",
 * "hackId": "...", — Send modified SRAM from browser
 * ```
 *     "data": "<base64>", "timestamp": 123456 }
 * ```
 * { "type": "snapshot_request", "hackId": "..." } — Request full SRAM snapshot
 *
 * App -> Browser: { "type": "snapshot", "hackId": "...", — Full SRAM snapshot response
 * ```
 *     "data": "<base64>", "timestamp": 123456 }
 * ```
 * { "type": "sram_update", "hackId": "...", — SRAM changed on Android side
 * ```
 *     "data": "<base64>", "timestamp": 123456 }
 * ```
 * { "type": "error", "message": "..." }
 */
internal fun Route.webSocketSramSync() {

    val context = application.attributes[DashboardManager.CONTEXT_KEY]

    // Track subscribed sessions: hackId -> set of WebSocket sessions
    val subscriptions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    // Track per-session subscriptions for cleanup
    val sessionSubscriptions = ConcurrentHashMap<WebSocketSession, MutableSet<String>>()

    webSocket("/ws/sram") {
        Log.d(TAG, "SRAM sync client connected")
        DashboardManager.incrementClients()

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    handleSramMessage(
                            text = text,
                            session = this,
                            context = context,
                            subscriptions = subscriptions,
                            sessionSubscriptions = sessionSubscriptions
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SRAM sync error", e)
        } finally {
            // Clean up subscriptions for this session.
            sessionSubscriptions.remove(this)?.forEach { hackId ->
                subscriptions[hackId]?.remove(this)
                if (subscriptions[hackId].isNullOrEmpty()) {
                    subscriptions.remove(hackId)
                }
            }
            DashboardManager.decrementClients()
            Log.d(TAG, "SRAM sync client disconnected")
        }
    }
}

private suspend fun handleSramMessage(
        text: String,
        session: DefaultWebSocketSession,
        context: android.content.Context,
        subscriptions: ConcurrentHashMap<String, MutableSet<WebSocketSession>>,
        sessionSubscriptions: ConcurrentHashMap<WebSocketSession, MutableSet<String>>
) {
    val message =
            try {
                Json.decodeFromString<SramMessage>(text)
            } catch (e: Exception) {
                session.outgoing.send(
                        Frame.Text(Json.encodeToString(SramError(message = "Invalid JSON message")))
                )
                return
            }

    when (message.type) {
        "subscribe" -> {
            val hackId = message.hackId ?: return
            subscriptions.getOrPut(hackId) { ConcurrentHashMap.newKeySet() }.add(session)
            sessionSubscriptions.getOrPut(session) { mutableSetOf() }.add(hackId)
            Log.d(TAG, "Client subscribed to SRAM for $hackId")
        }
        "unsubscribe" -> {
            val hackId = message.hackId ?: return
            subscriptions[hackId]?.remove(session)
            sessionSubscriptions[session]?.remove(hackId)
            Log.d(TAG, "Client unsubscribed from SRAM for $hackId")
        }
        "sram_update" -> {
            val hackId = message.hackId ?: return
            val data = message.data ?: return
            val timestamp = message.timestamp

            // Write SRAM to disk.
            try {
                val sramBytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                val sramFile = Storage.getInstance(context).sram(hackId)
                sramFile.outputStream().use { it.write(sramBytes) }
                Log.d(TAG, "SRAM updated for $hackId (${sramBytes.size} bytes)")
            } catch (e: Exception) {
                session.outgoing.send(
                        Frame.Text(
                                Json.encodeToString(
                                        SramError(message = "Failed to write SRAM: ${e.message}")
                                )
                        )
                )
                return
            }

            // Broadcast to other subscribed clients.
            val broadcast =
                    Json.encodeToString(
                            SramUpdate(
                                    type = "sram_update",
                                    hackId = hackId,
                                    data = data,
                                    timestamp = timestamp
                            )
                    )
            subscriptions[hackId]?.forEach { otherSession ->
                if (otherSession != session) {
                    try {
                        otherSession.outgoing.send(Frame.Text(broadcast))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send SRAM update to client", e)
                    }
                }
            }
        }
        "snapshot_request" -> {
            val hackId = message.hackId ?: return
            val sramFile = Storage.getInstance(context).sram(hackId)
            if (sramFile.exists()) {
                val bytes = sramFile.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                session.outgoing.send(
                        Frame.Text(
                                Json.encodeToString(
                                        SramSnapshot(
                                                type = "snapshot",
                                                hackId = hackId,
                                                data = base64,
                                                timestamp = sramFile.lastModified()
                                        )
                                )
                        )
                )
            } else {
                session.outgoing.send(
                        Frame.Text(
                                Json.encodeToString(
                                        SramSnapshot(
                                                type = "snapshot",
                                                hackId = hackId,
                                                data = null,
                                                timestamp = 0L
                                        )
                                )
                        )
                )
            }
        }
    }
}

private const val TAG = "SramSync"

@Serializable
internal data class SramMessage(
        val type: String,
        val hackId: String? = null,
        val data: String? = null,
        val timestamp: Long = System.currentTimeMillis()
)

@Serializable
internal data class SramUpdate(
        val type: String,
        val hackId: String,
        val data: String,
        val timestamp: Long
)

@Serializable
internal data class SramSnapshot(
        val type: String,
        val hackId: String,
        val data: String?,
        val timestamp: Long
)

@Serializable internal data class SramError(val type: String = "error", val message: String)
