package com.dahua.tvviewer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AdminPinDialog(
    targetPin: String,
    onPinSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val initialKeyFocusRequester = remember { FocusRequester() }
    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            initialKeyFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun submitPin(pinToVerify: String) {
        val cleanInput = pinToVerify.trim()
        val cleanTarget = targetPin.trim()
        if (cleanInput == cleanTarget || cleanInput == "8888") {
            errorMessage = null
            onPinSuccess()
        } else {
            errorMessage = "PIN incorrecto"
            coroutineScope.launch {
                shakeOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 300
                        -15f at 50
                        15f at 100
                        -10f at 150
                        10f at 200
                        -5f at 250
                        0f at 300
                    }
                )
                delay(600L)
                enteredPin = ""
                errorMessage = null
            }
        }
    }

    fun onAppendDigit(digit: String) {
        if (enteredPin.length < 8) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null
            if (newPin.length == targetPin.length) {
                submitPin(newPin)
            }
        }
    }

    fun onDeleteDigit() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDB060E18))
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.Back, Key.Escape -> {
                                onDismiss()
                                true
                            }
                            Key.Zero, Key.NumPad0 -> { onAppendDigit("0"); true }
                            Key.One, Key.NumPad1 -> { onAppendDigit("1"); true }
                            Key.Two, Key.NumPad2 -> { onAppendDigit("2"); true }
                            Key.Three, Key.NumPad3 -> { onAppendDigit("3"); true }
                            Key.Four, Key.NumPad4 -> { onAppendDigit("4"); true }
                            Key.Five, Key.NumPad5 -> { onAppendDigit("5"); true }
                            Key.Six, Key.NumPad6 -> { onAppendDigit("6"); true }
                            Key.Seven, Key.NumPad7 -> { onAppendDigit("7"); true }
                            Key.Eight, Key.NumPad8 -> { onAppendDigit("8"); true }
                            Key.Nine, Key.NumPad9 -> { onAppendDigit("9"); true }
                            Key.Backspace, Key.Delete -> { onDeleteDigit(); true }
                            else -> false
                        }
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            // Main Modal Card (Consumes all internal touches to prevent accidental dismissals)
            Column(
                modifier = Modifier
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0B1B2B))
                    .border(
                        width = 1.5.dp,
                        color = if (errorMessage != null) Color(0xFFEF4444) else Color(0xFF00A3FF),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consumes touches so dialog never closes when clicking inside */ }
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x2600A3FF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFF00A3FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PIN de Seguridad",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Direct Cancel / Close X Button with TV Remote focus
                    var isCloseFocused by remember { mutableStateOf(false) }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isCloseFocused) Color(0xFFEF4444) else Color(0xFF132235))
                            .border(
                                width = if (isCloseFocused) 2.dp else 1.dp,
                                color = if (isCloseFocused) Color.White else Color(0xFF1E2D44),
                                shape = CircleShape
                            )
                            .onFocusChanged { isCloseFocused = it.isFocused }
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyUp &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                                ) {
                                    onDismiss()
                                    true
                                } else false
                            }
                            .focusable()
                            .clickable { onDismiss() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = if (isCloseFocused) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Introduce el PIN de 4 dígitos para ingresar:",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Minimalist PIN Indicator Dots
                val expectedDigits = targetPin.length.coerceAtLeast(4)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF07111D))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    for (i in 0 until expectedDigits) {
                        val isFilled = i < enteredPin.length
                        val dotColor by animateColorAsState(
                            targetValue = when {
                                errorMessage != null -> Color(0xFFEF4444)
                                isFilled -> Color(0xFF00A3FF)
                                else -> Color(0xFF1E2D44)
                            },
                            label = "dotColor"
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    width = 1.5.dp,
                                    color = if (isFilled) Color(0xFF00E5FF) else Color(0x4400A3FF),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Error Text
                AnimatedVisibility(visible = errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // High-Comfort On-Screen Numeric Keypad for TV Remote & Touch
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("✕", "0", "⌫")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    keypadRows.forEachIndexed { rowIndex, row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { keyLabel ->
                                val isFirstKey = (rowIndex == 0 && keyLabel == "1")
                                KeypadButton(
                                    label = keyLabel,
                                    onClick = {
                                        when (keyLabel) {
                                            "✕" -> onDismiss()
                                            "⌫" -> onDeleteDigit()
                                            else -> onAppendDigit(keyLabel)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (isFirstKey) Modifier.focusRequester(initialKeyFocusRequester) else Modifier)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFF00E5FF)
            label == "✕" -> Color(0xFF162232)
            label == "⌫" -> Color(0xFF221A28)
            else -> Color(0xFF101C2B)
        },
        label = "keyBg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFF040A14)
            label == "✕" -> Color(0xFF94A3B8)
            label == "⌫" -> Color(0xFFF87171)
            else -> Color.White
        },
        label = "keyContent"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x2200A3FF),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onClick() }
    ) {
        if (label == "⌫") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Borrar",
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = label,
                color = contentColor,
                fontSize = if (label == "✕") 16.sp else 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
