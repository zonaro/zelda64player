/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.server

import android.content.Context
import android.util.Log
import br.com.redclaw.zelda64player.utils.CorePrefs
import io.ktor.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Singleton manager for the self-hosted dashboard server.
 *
 * Provides a centralized entry point for starting, stopping, and querying
 * the Ktor embedded server. Also holds shared state (connected client count)
 * accessible from route handlers via Ktor attributes.
 *
 * Usage:
 * ```kotlin
 * DashboardManager.start(context)
 * DashboardManager.stop()
 * val isRunning = DashboardManager.isRunning
 * val address = DashboardManager.address
 * ```
 */
object DashboardManager {

    private const val TAG = "DashboardManager"

    /** Ktor ApplicationAttribute key for the Android Context. */
    val CONTEXT_KEY = AttributeKey<Context>("DashboardContext")

    /** Ktor ApplicationAttribute key for the DashboardServer instance. */
    val SERVER_KEY = AttributeKey<DashboardServer>("DashboardServer")

    // Start/stop/restart must be serialized: Ktor releases a port only after stop() completes.
    private val lifecycleScope =
            CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val serverLock = Any()

    @Volatile private var server: DashboardServer? = null
    @Volatile private var startPending = false
    private val clientCount = AtomicInteger(0)

    /** True while the embedded server is running and accepting connections. */
    val isRunning: Boolean get() = server?.isRunning == true

    /** True while an enabled server is still binding its listening socket. */
    val isStarting: Boolean get() = startPending || server?.isStarting == true

    /** The LAN address and port (e.g. "192.168.1.100:8080"), or null when stopped. */
    val address: String? get() = server?.address

    /** Number of connected WebSocket clients (SRAM sync + signaling). */
    val connectedClients: Int get() = clientCount.get()

    /**
     * Start the dashboard server if it is enabled in preferences.
     *
     * This is safe to call multiple times; subsequent calls are no-ops if
     * the server is already running.
     */
    fun start(context: Context) {
        if (!CorePrefs.getDashboardEnabled(context)) {
            Log.d(TAG, "Dashboard is disabled in settings, skipping start")
            return
        }
        synchronized(serverLock) {
            if (startPending || server?.isRunning == true || server?.isStarting == true) {
                Log.d(TAG, "Dashboard server already active")
                return
            }
            startPending = true
        }
        lifecycleScope.launch {
            try {
                // The preference can change while this work waits behind a previous stop/restart.
                if (!CorePrefs.getDashboardEnabled(context)) return@launch
                val dashboardServer = synchronized(serverLock) {
                    DashboardServer(context.applicationContext).also { server = it }
                }
                dashboardServer.start()
                Log.i(TAG, "Dashboard server start requested on port ${CorePrefs.getDashboardPort(context)}")
            } finally {
                startPending = false
            }
        }
    }

    /**
     * Stop the dashboard server and release all resources.
     */
    fun stop() {
        startPending = false
        lifecycleScope.launch {
            val dashboardServer = synchronized(serverLock) {
                server?.also { server = null }
            }
            dashboardServer?.stop()
            clientCount.set(0)
            Log.i(TAG, "Dashboard server stopped")
        }
    }

    /** Restart an enabled dashboard after changing a setting that affects its socket or auth. */
    fun restart(context: Context) {
        startPending = true
        lifecycleScope.launch {
            try {
                val previous = synchronized(serverLock) { server?.also { server = null } }
                previous?.stop()
                clientCount.set(0)
                if (!CorePrefs.getDashboardEnabled(context)) return@launch

                val dashboardServer = DashboardServer(context.applicationContext)
                synchronized(serverLock) { server = dashboardServer }
                dashboardServer.start()
                Log.i(TAG, "Dashboard server restarted on port ${CorePrefs.getDashboardPort(context)}")
            } finally {
                startPending = false
            }
        }
    }

    /**
     * Called by route handlers to inject the Android Context into Ktor's
     * Application attributes so route files can access Storage and repositories.
     */
    fun installContext(application: io.ktor.server.application.Application, context: Context) {
        application.attributes.put(CONTEXT_KEY, context.applicationContext)
        application.attributes.put(SERVER_KEY, server!!)
    }

    /**
     * Increment the connected client counter (called from WebSocket handlers).
     */
    fun incrementClients() {
        val count = clientCount.incrementAndGet()
        server?.connectedClients = count
    }

    /**
     * Decrement the connected client counter (called from WebSocket handlers).
     */
    fun decrementClients() {
        val count = clientCount.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        server?.connectedClients = count
    }

    /**
     * Notify the dashboard that SRAM has been modified for a given hackId.
     * This can be used to push updates to subscribed WebSocket clients.
     */
    fun notifySramChanged(hackId: String, data: ByteArray) {
        // TODO: Broadcast to subscribed WebSocket clients via SramSyncWebSocket
        Log.d(TAG, "SRAM changed for $hackId (${data.size} bytes)")
    }
}
