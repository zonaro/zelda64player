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
import android.net.ConnectivityManager
import android.util.Log
import br.com.redclaw.zelda64player.utils.CorePrefs
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

/**
 * Embedded Ktor server that powers the self-hosted dashboard.
 *
 * The server runs on a background thread (CIO engine) and provides:
 * - REST API for collection management (ROMs, saves, covers)
 * - WebSocket endpoint for real-time SRAM sync with EmulatorJS
 * - WebRTC signaling for peer-to-peer streaming
 * - Static file serving for the dashboard SPA + EmulatorJS bundle
 *
 * Lifecycle is managed externally: call [start] to launch and [stop] to tear down. The server does
 * NOT auto-start; it is toggled by the user in Settings.
 */
class DashboardServer(private val context: Context) {

    companion object {
        private const val TAG = "DashboardServer"
    }

    private val lock = Any()

    @Volatile private var engine: EmbeddedServer<*, *>? = null

    @Volatile private var state: State = State.STOPPED

    /** Current lifecycle state of the embedded server. */
    enum class State {
        STOPPED,
        STARTING,
        RUNNING
    }

    /** True while the server is accepting connections. */
    val isRunning: Boolean
        get() = state == State.RUNNING

    /** True after startup was requested but before Ktor finishes binding the listening socket. */
    val isStarting: Boolean
        get() = state == State.STARTING

    /** The local IP address and port the server is bound to, or null when stopped. */
    @Volatile
    var address: String? = null
        private set

    /** Number of connected WebSocket clients (SRAM sync + signaling). */
    var connectedClients: Int = 0
        internal set

    /**
     * Start the embedded Ktor CIO server on the configured port.
     *
     * The server binds to `0.0.0.0` (all interfaces) so browsers on the same LAN can connect. If
     * the port is already in use, startup fails and [isRunning] remains false.
     */
    fun start() {
        synchronized(lock) {
            if (state != State.STOPPED) {
                Log.w(TAG, "Server already $state on $address")
                return
            }

            val port = CorePrefs.getDashboardPort(context)
            val password = CorePrefs.getDashboardPassword(context)
            state = State.STARTING
            try {
                val ctx = context.applicationContext
                val server =
                        embeddedServer(CIO, port = port, host = "0.0.0.0") {
                            DashboardManager.installContext(this, ctx)
                            configureJson()
                            configureCors()
                            configureStatusPages()
                            configureWebSocket()
                            configureRouting(password)
                        }
                engine = server
                server.start(wait = false)

                // Ktor has accepted the bind request. Keep the visible address separate from the
                // wildcard bind address so the user can open it from another device on the LAN.
                address = resolveLanAddress() + ":$port"
                state = State.RUNNING
                Log.i(TAG, "Dashboard server started on $address")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start dashboard server", e)
                engine = null
                address = null
                state = State.STOPPED
            }
        }
    }

    /** Gracefully shut down the embedded server and release resources. */
    fun stop() {
        val server = synchronized(lock) {
            val current = engine ?: return
            engine = null
            address = null
            state = State.STOPPED
            current
        }
        try {
            server.stop(Duration.ofSeconds(2).toMillis(), Duration.ofSeconds(5).toMillis())
            Log.i(TAG, "Dashboard server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping dashboard server", e)
        } finally {
            connectedClients = 0
        }
    }

    /** Release the server and all of its resources. */
    fun destroy() {
        stop()
    }

    // ---- Ktor configuration helpers ----

    private fun Application.configureJson() {
        install(ContentNegotiation) {
            json(
                    Json {
                        prettyPrint = false
                        isLenient = true
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    }
            )
        }
    }

    private fun Application.configureCors() {
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowCredentials = true
            maxAgeInSeconds = 3600L
        }
    }

    private fun Application.configureStatusPages() {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                Log.e(TAG, "Unhandled exception in route", cause)
                call.respondText(
                        text = """{"error":"Internal server error"}""",
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.InternalServerError
                )
            }
        }
    }

    private fun Application.configureWebSocket() {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 60.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
    }

    private fun Application.configureRouting(password: String) {
        routing {
            // Health check (no auth).
            get("/api/health") {
                call.respond(
                        mapOf(
                                "status" to "running",
                                "connectedClients" to connectedClients,
                                "version" to "1.0.0"
                        )
                )
            }

            // All other API routes require password if configured.
            route("/api") {
                if (password.isNotBlank()) {
                    install(PasswordAuthPlugin) { this.password = password }
                }
                collectionRoutes()
                backupRoutes()
                settingsRoutes()
            }

            // WebSocket endpoints (auth handled per-socket).
            webSocketSramSync()
            webSocketSignaling()

            // Static dashboard SPA + EmulatorJS assets.
            staticFiles()
        }
    }

    /**
     * Resolve the active network's IPv4 address for display in Settings. The server remains bound
     * to every interface, but this prefers the address actually usable by another device on the
     * current LAN. Falls back to a suitable network interface, then localhost when offline.
     */
    private fun resolveLanAddress(): String {
        return try {
            val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivityManager?.activeNetwork?.let { network ->
                connectivityManager.getLinkProperties(network)?.linkAddresses
                        ?.asSequence()
                        ?.map { it.address }
                        ?.filterIsInstance<Inet4Address>()
                        ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        ?.hostAddress
                        ?.let { return it }
            }
            NetworkInterface.getNetworkInterfaces()
                    .asSequence()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.asSequence() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && it.isLinkLocalAddress.not() }
                    ?.hostAddress
                    ?: "localhost"
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve LAN address", e)
            "localhost"
        }
    }
}

/**
 * Simple password authentication plugin for Ktor routes. Checks for `Authorization: Bearer
 * <password>` header.
 */
internal class PasswordAuthPluginConfig {
    var password: String = ""
}

internal val PasswordAuthPlugin =
        createRouteScopedPlugin(
                name = "PasswordAuth",
                createConfiguration = { PasswordAuthPluginConfig() }
        ) {
            val config = pluginConfig
            onCall { call ->
                val authHeader = call.request.headers["Authorization"]
                val token = authHeader?.removePrefix("Bearer ")?.trim()
                if (token != config.password) {
                    call.respond(HttpStatusCode.Unauthorized, """{"error":"Unauthorized"}""")
                    return@onCall
                }
            }
        }
