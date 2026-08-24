package br.com.redclaw.zelda64player.randomizer

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Minimal localhost HTTP server bound to `127.0.0.1` that receives the patched
 * ROM bytes (POST `/patch`) streamed from the injected JS ([RandomizerJs]).
 *
 * The request body is written directly to a temp file in [tempDir] (no full
 * buffering in memory) and handed to [onCaptured]. This is the primary capture
 * path; [WebViewJsBridge] is the fallback when this server is unreachable.
 *
 * localhost is a secure context, so the HTTPS page can POST to
 * `http://127.0.0.1:<port>` without a mixed-content block.
 */
class LocalRomServer(
    private val tempDir: File,
    private val onCaptured: (File, String?) -> Unit
) {
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var thread: Thread? = null

    /** The bound localhost port, or `-1` if the server is not running. */
    val port: Int get() = serverSocket?.localPort ?: -1

    fun start(): Boolean {
        return try {
            val ss = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
            serverSocket = ss
            thread = Thread({ runLoop() }, "LocalRomServer").also { it.start() }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun runLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val ss = serverSocket ?: break
            val socket: Socket = try {
                ss.accept()
            } catch (_: SocketException) {
                break
            } catch (_: Exception) {
                break
            }
            try {
                handle(socket)
            } catch (_: Exception) {
                // Ignore malformed requests; never crash the acceptor thread.
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        val input = socket.getInputStream()
        val requestLine = readLine(input) ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2 || parts[0] != "POST" || parts[1] != "/patch") {
            sendResponse(socket, 404, "Not Found")
            return
        }
        var contentLength = -1
        var fileName: String? = null
        var line: String?
        while (readLine(input).also { line = it } != null) {
            val hdr = line!!
            if (hdr.isEmpty()) break
            val idx = hdr.indexOf(':')
            if (idx > 0) {
                val key = hdr.substring(0, idx).trim().lowercase()
                val value = hdr.substring(idx + 1).trim()
                when (key) {
                    "content-length" -> contentLength = value.toIntOrNull() ?: -1
                    "x-patch-filename" -> fileName = value
                }
            }
        }
        if (contentLength < 0) {
            sendResponse(socket, 400, "Bad Request")
            return
        }
        val outFile = File(tempDir, "randomizer_capture_${System.currentTimeMillis()}.z64")
        val buffer = ByteArray(32 * 1024)
        var remaining = contentLength
        FileOutputStream(outFile).use { out ->
            while (remaining > 0) {
                val toRead = minOf(buffer.size, remaining)
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        sendResponse(socket, 200, "OK")
        onCaptured(outFile, fileName?.takeIf { it.isNotBlank() })
    }

    /** Read a single CRLF/LF-terminated line from the raw stream (no buffering). */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c: Int
        while (true) {
            c = input.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) {
                if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') sb.setLength(sb.length - 1)
                return sb.toString()
            }
            sb.append(c.toChar())
        }
    }

    private fun sendResponse(socket: Socket, code: Int, message: String) {
        try {
            val out = socket.getOutputStream()
            val body = message.toByteArray()
            val header = "HTTP/1.1 $code $message\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
            out.write(header.toByteArray())
            out.write(body)
            out.flush()
        } catch (_: Exception) {
        }
    }

    fun stop() {
        thread?.interrupt()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        thread = null
    }
}
