package com.dahua.tvviewer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dahua.tvviewer.data.NvrConfig
import com.dahua.tvviewer.ui.P2pTunnelUiState
import com.dahua.tvviewer.ui.components.RtspPlayerTile
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun FullscreenCameraScreen(
    config: NvrConfig,
    channelNumber: Int,
    p2pTunnelState: P2pTunnelUiState,
    audioEnabled: Boolean,
    isMainStream: Boolean = false,
    onSwitchChannel: (Int) -> Unit,
    onToggleQuality: () -> Unit,
    onToggleAudio: () -> Unit,
    onBackToGrid: () -> Unit
) {
    // Show controls by default on open so user sees channel info & buttons
    var controlsVisible by remember { mutableStateOf(true) }
    var isTopBarFocused by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    val p2pPort = (p2pTunnelState as? P2pTunnelUiState.Ready)?.localPort
    val rtspUrl = remember(config, channelNumber, isMainStream, p2pPort) {
        config.buildRtspUrl(channelNumber = channelNumber, isMainStream = isMainStream, p2pLocalPort = p2pPort)
    }

    val screenFocusRequester = remember { FocusRequester() }
    val backButtonFocusRequester = remember { FocusRequester() }

    BackHandler {
        onBackToGrid()
    }

    // Auto-focus screen container on entry or when HUD hides so TV remote D-pad arrows always work
    LaunchedEffect(controlsVisible, channelNumber) {
        if (!controlsVisible || !isTopBarFocused) {
            isTopBarFocused = false
            try {
                screenFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-hide floating controls after 5 seconds of inactivity
    LaunchedEffect(interactionTick, channelNumber, controlsVisible) {
        if (controlsVisible) {
            delay(5000L)
            controlsVisible = false
            isTopBarFocused = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(screenFocusRequester)
            .focusable()
            // TV Remote Control: Left/Right arrows switch cameras directly unless user pressed Up to focus Top Bar
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    interactionTick += 1
                    when (event.key) {
                        // Dedicated Channel buttons always switch channel
                        Key.ChannelDown, Key.PageDown, Key.MediaPrevious -> {
                            onSwitchChannel(-1)
                            controlsVisible = true
                            true
                        }
                        Key.ChannelUp, Key.PageUp, Key.MediaNext -> {
                            onSwitchChannel(1)
                            controlsVisible = true
                            true
                        }
                        // Left / Right arrows switch camera immediately unless user navigated Up into the Top Bar
                        Key.DirectionLeft -> {
                            if (!isTopBarFocused) {
                                onSwitchChannel(-1)
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionRight -> {
                            if (!isTopBarFocused) {
                                onSwitchChannel(1)
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionUp -> {
                            controlsVisible = true
                            if (!isTopBarFocused) {
                                isTopBarFocused = true
                                try {
                                    backButtonFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionDown -> {
                            controlsVisible = true
                            if (isTopBarFocused) {
                                isTopBarFocused = false
                                try {
                                    screenFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                true
                            } else {
                                false
                            }
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (!controlsVisible) {
                                controlsVisible = true
                                isTopBarFocused = true
                                try {
                                    backButtonFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                true
                            } else if (!isTopBarFocused) {
                                isTopBarFocused = true
                                try {
                                    backButtonFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                true
                            } else {
                                false
                            }
                        }
                        Key.Back, Key.Escape -> {
                            onBackToGrid()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // 100% Edge-to-Edge Camera Video Stream (no black bars, fills screen)
        key(channelNumber, isMainStream, rtspUrl) {
            RtspPlayerTile(
                rtspUrl = rtspUrl,
                channelTitle = config.getChannelTitle(channelNumber),
                channelNumber = channelNumber,
                useTcp = config.useTcpTransport,
                username = config.username,
                password = config.password,
                audioEnabled = audioEnabled,
                isFullscreen = true,
                streamQualityBadge = if (isMainStream) "HD" else "SD",
                retryTrigger = retryTrigger,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Transparent Touch Layer: Seamless gestures for swipe channel change & tap to toggle HUD
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(channelNumber) {
                    var accumulatedX = 0f
                    var isDrag = false
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        accumulatedX = 0f
                        isDrag = false
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                val deltaX = change.positionChange().x
                                accumulatedX += deltaX
                                if (abs(accumulatedX) > 25f) {
                                    isDrag = true
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isDrag) {
                            interactionTick += 1
                            if (accumulatedX < -50f) {
                                // Swiped left on screen -> Next channel
                                onSwitchChannel(1)
                                controlsVisible = true
                            } else if (accumulatedX > 50f) {
                                // Swiped right on screen -> Previous channel
                                onSwitchChannel(-1)
                                controlsVisible = true
                            }
                        } else {
                            // Tap on screen -> Toggle controls
                            controlsVisible = !controlsVisible
                            interactionTick += 1
                        }
                    }
                }
        )

        // Minimalist Floating Top HUD (Auto-Hides)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back button + Channel title in a sleek glassmorphic pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xD90B1B2B))
                        .border(1.dp, Color(0x3300A3FF), RoundedCornerShape(30.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    MinimalIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        onClick = onBackToGrid,
                        modifier = Modifier.focusRequester(backButtonFocusRequester)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CH$channelNumber · ${config.getChannelTitle(channelNumber)}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Right: Fast minimal action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quality Toggle Pill (HD / SUB)
                    MinimalQualityPill(
                        isMainStream = isMainStream,
                        onClick = {
                            interactionTick += 1
                            onToggleQuality()
                        }
                    )

                    // Audio Toggle
                    MinimalIconButton(
                        icon = if (audioEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Audio",
                        isActive = audioEnabled,
                        onClick = {
                            interactionTick += 1
                            onToggleAudio()
                        }
                    )

                    // Refresh
                    MinimalIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Reconectar",
                        onClick = {
                            interactionTick += 1
                            retryTrigger += 1
                        }
                    )
                }
            }
        }

        // Minimalist Floating Side Navigation Arrows (Touch & Remote)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            MinimalNavArrow(
                icon = Icons.Default.ChevronLeft,
                contentDescription = "Canal anterior",
                onClick = {
                    interactionTick += 1
                    onSwitchChannel(-1)
                }
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            MinimalNavArrow(
                icon = Icons.Default.ChevronRight,
                contentDescription = "Canal siguiente",
                onClick = {
                    interactionTick += 1
                    onSwitchChannel(1)
                }
            )
        }
    }
}

@Composable
private fun MinimalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color(0xFF00E5FF)
                    isActive -> Color(0xFF0066FF)
                    else -> Color(0xD90B1B2B)
                }
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x3300A3FF),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color(0xFF040A14) else Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun MinimalQualityPill(
    isMainStream: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    isFocused -> Color(0xFF00E5FF)
                    isMainStream -> Color(0xFF0284C7)
                    else -> Color(0xD90B1B2B)
                }
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x3300A3FF),
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (isMainStream) "HD" else "SD",
            color = if (isFocused) Color(0xFF040A14) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MinimalNavArrow(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isFocused) Color(0xFF00E5FF) else Color(0xCC0B1B2B))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x4400A3FF),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else false
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color(0xFF040A14) else Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}
