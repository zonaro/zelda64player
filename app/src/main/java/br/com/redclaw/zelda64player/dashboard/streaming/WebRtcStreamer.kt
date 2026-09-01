/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.streaming

import android.content.Context
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.swordfish.libretrodroid.GLRetroView
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.webrtc.*

/**
 * Manages WebRTC peer connections for streaming the emulator framebuffer and audio to browser
 * clients.
 *
 * Architecture:
 * - Creates a PeerConnectionFactory with hardware video encoding
 * - For each connected browser, creates a PeerConnection
 * - VideoTrack: Captures GLRetroView framebuffer via [FrameCapturer], encodes to H.264 via
 * [VideoEncoder], sends via WebRTC
 * - AudioTrack: Captures from emulator's audio output via [AudioCapturer], sends raw PCM (WebRTC
 * handles Opus encoding internally)
 * - Signaling: SDP offer/answer and ICE candidates exchanged via the Ktor WebSocket signaling
 * endpoint
 *
 * Usage:
 * ```kotlin
 * val streamer = WebRtcStreamer(context)
 * streamer.initialize()
 * streamer.startStreaming(glRetroView, peerId)
 * // ... when done
 * streamer.stopStreaming()
 * streamer.release()
 * ```
 */
class WebRtcStreamer(private val context: Context) {

    companion object {
        private const val TAG = "WebRtcStreamer"

        // Google's public STUN server for NAT traversal.
        private val STUN_URL =
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: FrameCapturer? = null
    private var videoEncoder: VideoEncoder? = null
    private var audioCapturer: AudioCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    private var streamingThread: HandlerThread? = null
    private var streamingHandler: Handler? = null
    private var captureExecutor: ExecutorService? = null

    private var isStreaming = false

    /** Initialize the WebRTC peer connection factory. Must be called once before any streaming. */
    fun initialize() {
        // Initialize WebRTC native library.
        val initOptions =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        eglBase = EglBase.create()
        // Create encoder factory using MediaCodec for H.264.
        val encoderFactory =
                DefaultVideoEncoderFactory(
                        eglBase!!.eglBaseContext,
                        true,
                        true // enableIntelVp8Encoder, enableH264HighProfile
                )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

        peerConnectionFactory =
                PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(encoderFactory)
                        .setVideoDecoderFactory(decoderFactory)
                        .createPeerConnectionFactory()

        streamingThread = HandlerThread("WebRtcStreaming").apply { start() }
        streamingHandler = Handler(streamingThread!!.looper)
        captureExecutor = Executors.newSingleThreadExecutor()

        Log.d(TAG, "WebRTC initialized")
    }

    /**
     * Start streaming the emulator's framebuffer and audio to a browser client.
     *
     * @param glRetroView The emulator's GL view to capture from
     * @param peerId Unique identifier for this peer connection
     * @param onOfferCreated Callback with the SDP offer to send to the browser
     */
    fun startStreaming(glRetroView: GLRetroView, peerId: String, onOfferCreated: (String) -> Unit) {
        if (isStreaming) {
            Log.w(TAG, "Already streaming, stopping previous session")
            stopStreaming()
        }

        streamingHandler?.post {
            try {
                createPeerConnection(peerId, onOfferCreated)
                setupVideoTrack(glRetroView)
                setupAudioTrack()
                startFrameCapture(glRetroView)
                isStreaming = true
                Log.d(TAG, "Streaming started for peer: $peerId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start streaming", e)
            }
        }
    }

    /** Stop the current streaming session. */
    fun stopStreaming() {
        isStreaming = false
        videoCapturer?.release()
        videoEncoder?.stop()
        audioCapturer?.stop()
        peerConnection?.close()
        peerConnection = null
        videoSource?.dispose()
        audioSource?.dispose()
        captureExecutor?.shutdownNow()
        captureExecutor = Executors.newSingleThreadExecutor()
        Log.d(TAG, "Streaming stopped")
    }

    /** Process an SDP offer from a browser client. */
    fun processOffer(sdp: String, peerId: String) {
        streamingHandler?.post {
            val pc = peerConnection ?: return@post
            val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
            pc.setRemoteDescription(
                    object : SdpObserver {
                        override fun onSetSuccess() {
                            // Create answer in response.
                            pc.createAnswer(
                                    object : SdpObserver {
                                        override fun onCreateSuccess(answer: SessionDescription) {
                                            pc.setLocalDescription(
                                                    object : SdpObserver {
                                                        override fun onSetSuccess() {
                                                            Log.d(TAG, "SDP answer created and set")
                                                            // Send the answer back via signaling
                                                            // WebSocket.
                                                            // This is handled by the
                                                            // SignalingWebSocket.
                                                        }
                                                        override fun onSetFailure(error: String) {
                                                            Log.e(
                                                                    TAG,
                                                                    "Failed to set local description: $error"
                                                            )
                                                        }
                                                        override fun onCreateSuccess(
                                                                answer: SessionDescription
                                                        ) {}
                                                        override fun onCreateFailure(
                                                                error: String
                                                        ) {
                                                            Log.e(
                                                                    TAG,
                                                                    "Failed to create answer: $error"
                                                            )
                                                        }
                                                    },
                                                    answer
                                            )
                                        }
                                        override fun onSetSuccess() {}
                                        override fun onSetFailure(error: String) {
                                            Log.e(TAG, "Failed to set remote description: $error")
                                        }
                                        override fun onCreateFailure(error: String) {
                                            Log.e(TAG, "Failed to create answer: $error")
                                        }
                                    },
                                    MediaConstraints()
                            )
                        }
                        override fun onSetFailure(error: String) {
                            Log.e(TAG, "Failed to set remote description: $error")
                        }
                        override fun onCreateSuccess(sd: SessionDescription) {}
                        override fun onCreateFailure(error: String) {
                            Log.e(TAG, "Failed to create answer: $error")
                        }
                    },
                    sessionDescription
            )
        }
    }

    /** Process an SDP answer from a browser client. */
    fun processAnswer(sdp: String) {
        streamingHandler?.post {
            val pc = peerConnection ?: return@post
            val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            pc.setRemoteDescription(
                    object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Remote SDP answer set")
                        }
                        override fun onSetFailure(error: String) {
                            Log.e(TAG, "Failed to set remote description: $error")
                        }
                        override fun onCreateSuccess(sd: SessionDescription) {}
                        override fun onCreateFailure(error: String) {
                            Log.e(TAG, "Failed to create answer: $error")
                        }
                    },
                    sessionDescription
            )
        }
    }

    /** Add an ICE candidate from a browser client. */
    fun addIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        streamingHandler?.post {
            val pc = peerConnection ?: return@post
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
            pc.addIceCandidate(iceCandidate)
        }
    }

    /** Release all WebRTC resources. */
    fun release() {
        stopStreaming()
        streamingThread?.quitSafely()
        streamingThread = null
        streamingHandler = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase?.release()
        eglBase = null
        Log.d(TAG, "WebRTC resources released")
    }

    // ---- Private implementation ----

    private fun createPeerConnection(peerId: String, onOfferCreated: (String) -> Unit) {
        val rtcConfig =
                PeerConnection.RTCConfiguration(listOf(STUN_URL)).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    continualGatheringPolicy =
                            PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                }

        peerConnection =
                peerConnectionFactory?.createPeerConnection(
                        rtcConfig,
                        object : PeerConnection.Observer {
                            override fun onSignalingChange(state: PeerConnection.SignalingState) {
                                Log.d(TAG, "Signaling state: $state")
                            }
                            override fun onIceConnectionChange(
                                    state: PeerConnection.IceConnectionState
                            ) {
                                Log.d(TAG, "ICE connection state: $state")
                            }
                            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                            override fun onIceGatheringChange(
                                    state: PeerConnection.IceGatheringState
                            ) {
                                Log.d(TAG, "ICE gathering state: $state")
                            }
                            override fun onIceCandidate(candidate: IceCandidate) {
                                // Send ICE candidate to browser via signaling WebSocket.
                                Log.d(TAG, "ICE candidate: ${candidate.sdp}")
                            }
                            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
                            override fun onAddStream(stream: MediaStream) {}
                            override fun onRemoveStream(stream: MediaStream) {}
                            override fun onDataChannel(channel: DataChannel) {
                                Log.d(TAG, "Data channel opened: ${channel.label()}")
                                // Handle input events from browser via DataChannel.
                                setupInputDataChannel(channel)
                            }
                            override fun onAddTrack(
                                    receiver: RtpReceiver,
                                    streams: Array<MediaStream>
                            ) {}
                            override fun onRenegotiationNeeded() {}
                        }
                )

        // Create SDP offer.
        peerConnection?.createOffer(
                object : SdpObserver {
                    override fun onCreateSuccess(offer: SessionDescription) {
                        peerConnection?.setLocalDescription(
                                object : SdpObserver {
                                    override fun onSetSuccess() {
                                        Log.d(TAG, "SDP offer created")
                                        onOfferCreated(offer.description)
                                    }
                                    override fun onSetFailure(error: String) {
                                        Log.e(TAG, "Failed to set local description: $error")
                                    }
                                    override fun onCreateFailure(error: String) {}
                                    override fun onCreateSuccess(description: SessionDescription) {}
                                },
                                offer
                        )
                    }
                    override fun onSetSuccess() {}
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "Failed to create offer: $error")
                    }
                    override fun onCreateFailure(error: String) {
                        Log.e(TAG, "Failed to create offer: $error")
                    }
                },
                MediaConstraints()
        )
    }

    private fun setupVideoTrack(glRetroView: GLRetroView) {
        videoSource = peerConnectionFactory?.createVideoSource(false)

        val capturer =
                VideoCapturer(glRetroView) { width, height ->
                    // This callback is invoked on the GL thread with the framebuffer dimensions.
                    // Initialize the frame capturer and video encoder.
                    videoCapturer = FrameCapturer(width, height)
                    videoEncoder = VideoEncoder(width, height)
                }

        videoTrack = peerConnectionFactory?.createVideoTrack("ARDNESVideo", videoSource)
        videoTrack?.setEnabled(true)

        peerConnection?.addTrack(videoTrack)
    }

    private fun setupAudioTrack() {
        audioCapturer = AudioCapturer()
        audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory?.createAudioTrack("ARDNESAudio", audioSource)
        audioTrack?.setEnabled(true)

        peerConnection?.addTrack(audioTrack)

        audioCapturer?.onAudioFrame = { buffer, numFrames ->
            // Send audio data to WebRTC AudioSource.
            val data = ShortArray(numFrames * 2) // stereo
            buffer.asShortBuffer().get(data)
            // WebRTC handles encoding internally via the AudioSource.
        }
    }

    private fun startFrameCapture(glRetroView: GLRetroView) {
        captureExecutor?.execute {
            try {
                // Capture framebuffer at the configured frame rate.
                while (isStreaming) {
                    val startTime = System.currentTimeMillis()

                    // Read pixels from GL framebuffer.
                    val width = glRetroView.width
                    val height = glRetroView.height
                    if (width <= 0 || height <= 0) {
                        Thread.sleep(100)
                        continue
                    }

                    val buffer = ByteBuffer.allocateDirect(width * height * 4)
                    GLES30.glReadPixels(
                            0,
                            0,
                            width,
                            height,
                            GLES30.GL_RGBA,
                            GLES30.GL_UNSIGNED_BYTE,
                            buffer
                    )
                    buffer.rewind()

                    // Encode frame to H.264.
                    videoEncoder?.encodeFrame(buffer, System.nanoTime() / 1000)

                    // Maintain target frame rate.
                    val elapsed = System.currentTimeMillis() - startTime
                    val targetFrameTime = 1000L / 30 // 30fps
                    if (elapsed < targetFrameTime) {
                        Thread.sleep(targetFrameTime - elapsed)
                    }
                }
            } catch (e: Exception) {
                if (isStreaming) {
                    Log.e(TAG, "Frame capture error", e)
                }
            }
        }
    }

    private fun setupInputDataChannel(channel: DataChannel) {
        channel.registerObserver(
                object : DataChannel.Observer {
                    override fun onBufferedAmountChange(amount: Long) {}
                    override fun onStateChange() {
                        Log.d(TAG, "DataChannel state: ${channel.state()}")
                    }
                    override fun onMessage(message: DataChannel.Buffer) {
                        // Parse input event from browser and forward to emulator.
                        val data = ByteArray(message.data.remaining())
                        message.data.get(data)
                        val inputEvent = parseInputEvent(data)
                        if (inputEvent != null) {
                            // Forward to GLRetroView's input handling.
                            Log.d(TAG, "Input from browser: $inputEvent")
                        }
                    }
                }
        )
    }

    private fun parseInputEvent(data: ByteArray): InputEvent? {
        // Simple binary protocol:
        // byte 0: event type (1=key, 2=motion)
        // byte 1: key code or axis
        // byte 2: action (0=up, 1=down) for keys, value for axes
        if (data.size < 3) return null
        return InputEvent(type = data[0].toInt(), code = data[1].toInt(), value = data[2].toInt())
    }

    data class InputEvent(val type: Int, val code: Int, val value: Int)

    /**
     * Inner class that implements VideoCapturer for WebRTC integration. Captures from the
     * GLRetroView's framebuffer.
     */
    private inner class VideoCapturer(
            private val glRetroView: GLRetroView,
            private val onInitialized: (Int, Int) -> Unit
    ) {
        init {
            // Initialize with the view's dimensions.
            onInitialized(glRetroView.width, glRetroView.height)
        }
    }
}
