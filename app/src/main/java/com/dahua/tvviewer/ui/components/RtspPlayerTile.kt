package com.dahua.tvviewer.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.Dialog
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

enum class StreamStatus {
    CONNECTING,
    LIVE,
    ERROR
}

object SharedVlcInstance {
    @Volatile
    private var instance: LibVLC? = null

    @Volatile
    var activeUsername: String = ""

    @Volatile
    var activePassword: String = ""

    @Volatile
    var lastErrorMessage: String? = null

    fun get(context: Context): LibVLC {
        return instance ?: synchronized(this) {
            instance ?: LibVLC(
                context.applicationContext,
                arrayListOf(
                    "--rtsp-tcp",
                    "--network-caching=500",
                    "--live-caching=500",
                    "--file-caching=500",
                    "--clock-jitter=0",
                    "--clock-synchro=0",
                    "--drop-late-frames",
                    "--skip-frames",
                    "--no-osd",
                    "--no-stats",
                    "--no-sub-autodetect-file"
                )
            ).also { vlc ->
                instance = vlc
                // Intercept RTSP Digest 401 challenges and supply raw credentials without URL encoding bugs
                Dialog.setCallbacks(vlc, object : Dialog.Callbacks {
                    override fun onDisplay(dialog: Dialog.LoginDialog) {
                        val user = activeUsername
                        val pass = activePassword
                        Log.d("LibVLC", "LoginDialog responding with user: $user")
                        dialog.postLogin(user, pass, false)
                    }

                    override fun onDisplay(dialog: Dialog.ErrorMessage) {
                        lastErrorMessage = dialog.text
                        Log.e("LibVLC", "VLC Error Dialog: ${dialog.text}")
                    }

                    override fun onDisplay(dialog: Dialog.QuestionDialog) {
                        dialog.dismiss()
                    }

                    override fun onDisplay(dialog: Dialog.ProgressDialog) {}
                    override fun onCanceled(dialog: Dialog) {}
                    override fun onProgressUpdate(dialog: Dialog.ProgressDialog) {}
                })
            }
        }
    }
}

@Composable
fun RtspPlayerTile(
    rtspUrl: String,
    channelTitle: String,
    channelNumber: Int,
    useTcp: Boolean,
    username: String = "",
    password: String = "",
    audioEnabled: Boolean = false,
    isFullscreen: Boolean = false,
    isDenseGrid: Boolean = false,
    streamQualityBadge: String = "SUB",
    retryTrigger: Int = 0,
    onSignalStatusChanged: ((channelNumber: Int, hasSignal: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var status by remember(rtspUrl, retryTrigger) { mutableStateOf(StreamStatus.CONNECTING) }
    var errorMessage by remember(rtspUrl, retryTrigger) { mutableStateOf<String?>(null) }
    var autoRetryCount by remember(rtspUrl, retryTrigger) { mutableIntStateOf(0) }
    var isRetryExhausted by remember(rtspUrl, retryTrigger) { mutableStateOf(false) }
    var isSurfaceReady by remember { mutableStateOf(false) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }

    val activeUser = username.trim()
    val activePass = password
    SharedVlcInstance.activeUsername = activeUser
    SharedVlcInstance.activePassword = activePass

    val libVlc = remember(context) { SharedVlcInstance.get(context) }
    val mediaPlayer = remember(libVlc) { MediaPlayer(libVlc) }

    LaunchedEffect(username, password) {
        SharedVlcInstance.activeUsername = activeUser
        SharedVlcInstance.activePassword = activePass
    }

    LaunchedEffect(audioEnabled) {
        try {
            mediaPlayer.volume = if (audioEnabled) 100 else 0
        } catch (_: Exception) {
        }
    }

    // Set up LibVLC media and playback
    LaunchedEffect(mediaPlayer, rtspUrl, useTcp, username, password, retryTrigger, autoRetryCount) {
        if (isRetryExhausted) return@LaunchedEffect

        val parsedUri = try {
            Uri.parse(rtspUrl)
        } catch (_: Exception) {
            null
        }
        val host = parsedUri?.host

        if (host.isNullOrBlank()) {
            status = StreamStatus.ERROR
            isRetryExhausted = true
            errorMessage = "Falta la IP del NVR en Configurar (ej. 192.168.68.142)"
            onSignalStatusChanged?.invoke(channelNumber, false)
            return@LaunchedEffect
        }

        status = StreamStatus.CONNECTING
        errorMessage = null

        // Stagger connections slightly in grid view so 16 cameras don't hit Dahua at the exact same millisecond
        val staggerDelay = if (isFullscreen) 0L else ((channelNumber - 1) % 8) * 120L
        if (staggerDelay > 0) {
            delay(staggerDelay)
        }

        val eventListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Buffering -> {
                    if (event.buffering >= 100f) {
                        status = StreamStatus.LIVE
                        errorMessage = null
                        onSignalStatusChanged?.invoke(channelNumber, true)
                    }
                }
                MediaPlayer.Event.Playing,
                MediaPlayer.Event.Vout -> {
                    status = StreamStatus.LIVE
                    errorMessage = null
                    onSignalStatusChanged?.invoke(channelNumber, true)
                    if (surfaceWidth > 0 && surfaceHeight > 0) {
                        try {
                            mediaPlayer.aspectRatio = "$surfaceWidth:$surfaceHeight"
                            mediaPlayer.scale = 0f
                        } catch (_: Exception) {}
                    }
                }
                MediaPlayer.Event.EncounteredError,
                MediaPlayer.Event.EndReached -> {
                    val nextAttempt = autoRetryCount + 1
                    if (nextAttempt < 2) {
                        status = StreamStatus.ERROR
                        errorMessage = "Reintentando conexión (2/2)..."
                        coroutineScope.launch {
                            delay(2000L)
                            autoRetryCount = nextAttempt
                        }
                    } else {
                        status = StreamStatus.ERROR
                        autoRetryCount = nextAttempt
                        isRetryExhausted = true
                        errorMessage = "Sin señal de cámara (2/2 intentos agotados)"
                        onSignalStatusChanged?.invoke(channelNumber, false)
                    }
                }
            }
        }

        mediaPlayer.setEventListener(eventListener)

        try {
            if (mediaPlayer.isPlaying) {
                try {
                    mediaPlayer.stop()
                } catch (_: Exception) {}
            }

            val media = Media(libVlc, Uri.parse(rtspUrl)).apply {
                // Use hardware decoding only for Fullscreen HD Main stream; SUB streams use fast SW decoding
                setHWDecoderEnabled(isFullscreen && streamQualityBadge == "HD", false)

                if (useTcp) {
                    addOption(":rtsp-tcp")
                }
                addOption(":rtsp-user=$activeUser")
                addOption(":rtsp-pwd=$activePass")
                addOption(":network-caching=250")
                addOption(":live-caching=250")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
                addOption(":drop-late-frames")
                addOption(":skip-frames")
                if (!audioEnabled) {
                    addOption(":no-audio")
                }
            }

            mediaPlayer.media = media
            media.release()
            if (surfaceWidth > 0 && surfaceHeight > 0) {
                mediaPlayer.aspectRatio = "$surfaceWidth:$surfaceHeight"
            } else {
                mediaPlayer.aspectRatio = "16:9"
            }
            mediaPlayer.scale = 0f
            mediaPlayer.volume = if (audioEnabled) 100 else 0

            if (isSurfaceReady && mediaPlayer.vlcVout.areViewsAttached()) {
                mediaPlayer.play()
            }
        } catch (e: Exception) {
            val nextAttempt = autoRetryCount + 1
            if (nextAttempt < 2) {
                status = StreamStatus.ERROR
                errorMessage = "Error al conectar (Reintentando 2/2...)"
                coroutineScope.launch {
                    delay(2000L)
                    autoRetryCount = nextAttempt
                }
            } else {
                status = StreamStatus.ERROR
                autoRetryCount = nextAttempt
                isRetryExhausted = true
                errorMessage = "Sin señal de cámara (2/2 intentos agotados)"
                onSignalStatusChanged?.invoke(channelNumber, false)
            }
        }
    }

    // Watchdog timer: if stuck in CONNECTING for > 7 seconds without frames, advance/stop attempts
    LaunchedEffect(status, autoRetryCount, retryTrigger) {
        if (status == StreamStatus.CONNECTING && !isRetryExhausted) {
            delay(7000L)
            if (status == StreamStatus.CONNECTING) {
                try {
                    mediaPlayer.stop()
                } catch (_: Exception) {}
                val nextAttempt = autoRetryCount + 1
                if (nextAttempt < 2) {
                    status = StreamStatus.ERROR
                    errorMessage = "Sin respuesta (Reintentando 2/2...)"
                    delay(1500L)
                    autoRetryCount = nextAttempt
                } else {
                    status = StreamStatus.ERROR
                    autoRetryCount = nextAttempt
                    isRetryExhausted = true
                    errorMessage = "Sin señal de cámara (2/2 intentos agotados)"
                    onSignalStatusChanged?.invoke(channelNumber, false)
                }
            }
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            try {
                mediaPlayer.setEventListener(null)
                mediaPlayer.stop()
                if (mediaPlayer.vlcVout.areViewsAttached()) {
                    mediaPlayer.vlcVout.detachViews()
                }
                mediaPlayer.release()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B19))
    ) {
        // Video View with robust SurfaceTexture lifecycle
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            try {
                                surfaceWidth = width
                                surfaceHeight = height
                                val vout = mediaPlayer.vlcVout
                                if (!vout.areViewsAttached()) {
                                    vout.setVideoView(this@apply)
                                    vout.attachViews()
                                }
                                vout.setWindowSize(width, height)
                                if (width > 0 && height > 0) {
                                    mediaPlayer.aspectRatio = "$width:$height"
                                    mediaPlayer.scale = 0f
                                }
                                isSurfaceReady = true
                                if (status != StreamStatus.ERROR && !mediaPlayer.isPlaying && mediaPlayer.media != null) {
                                    mediaPlayer.play()
                                }
                            } catch (e: Exception) {
                                Log.e("RtspPlayerTile", "Error attaching video surface", e)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            try {
                                surfaceWidth = width
                                surfaceHeight = height
                                val vout = mediaPlayer.vlcVout
                                if (vout.areViewsAttached()) {
                                    vout.detachViews()
                                    vout.setVideoView(this@apply)
                                    vout.attachViews()
                                }
                                vout.setWindowSize(width, height)
                                if (width > 0 && height > 0) {
                                    mediaPlayer.aspectRatio = "$width:$height"
                                    mediaPlayer.scale = 0f
                                }
                            } catch (_: Exception) {
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            isSurfaceReady = false
                            try {
                                val vout = mediaPlayer.vlcVout
                                if (vout.areViewsAttached()) {
                                    vout.detachViews()
                                }
                            } catch (_: Exception) {
                            }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            if (status != StreamStatus.LIVE) {
                                status = StreamStatus.LIVE
                                errorMessage = null
                                onSignalStatusChanged?.invoke(channelNumber, true)
                                if (surfaceWidth > 0 && surfaceHeight > 0) {
                                    try {
                                        mediaPlayer.aspectRatio = "$surfaceWidth:$surfaceHeight"
                                        mediaPlayer.scale = 0f
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }

                    if (isAvailable) {
                        try {
                            surfaceWidth = width
                            surfaceHeight = height
                            val vout = mediaPlayer.vlcVout
                            if (!vout.areViewsAttached()) {
                                vout.setVideoView(this)
                                vout.attachViews()
                            }
                            vout.setWindowSize(width, height)
                            if (width > 0 && height > 0) {
                                mediaPlayer.aspectRatio = "$width:$height"
                                mediaPlayer.scale = 0f
                            }
                            isSurfaceReady = true
                            if (status != StreamStatus.ERROR && !mediaPlayer.isPlaying && mediaPlayer.media != null) {
                                mediaPlayer.play()
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            },
            update = { textureView ->
                try {
                    if (textureView.width > 0 && textureView.height > 0) {
                        surfaceWidth = textureView.width
                        surfaceHeight = textureView.height
                        val vout = mediaPlayer.vlcVout
                        vout.setWindowSize(textureView.width, textureView.height)
                        mediaPlayer.aspectRatio = "${textureView.width}:${textureView.height}"
                        mediaPlayer.scale = 0f
                    }
                } catch (_: Exception) {
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Overlay (only shown if not actively rendering/playing)
        if (status == StreamStatus.CONNECTING && !mediaPlayer.isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC070B19)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(if (isFullscreen) 38.dp else 24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Conectando CH$channelNumber...",
                        color = Color(0xFFE2E8F0),
                        fontSize = if (isFullscreen) 14.sp else 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error Overlay
        if (status == StreamStatus.ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6080F1E)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(if (isFullscreen) 36.dp else 24.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "Sin señal RTSP",
                        color = Color(0xFFF8FAFC),
                        fontSize = if (isFullscreen) 14.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRetryExhausted) "Búsqueda detenida · Toca Actualizar para reintentar" else "Reintentando automáticamente...",
                        color = Color(0xFF94A3B8),
                        fontSize = if (isFullscreen) 12.sp else 10.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
