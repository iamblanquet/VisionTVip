package com.dahua.tvviewer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dahua.tvviewer.BuildConfig
import com.dahua.tvviewer.data.AppUpdateState
import com.dahua.tvviewer.data.GithubReleaseInfo
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    updateState: AppUpdateState,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: (GithubReleaseInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onClose: () -> Unit
) {
    val primaryFocusRequester = remember { FocusRequester() }

    BackHandler(onBack = onClose)

    LaunchedEffect(updateState) {
        try {
            primaryFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9040914))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0B1B2B))
                .border(1.5.dp, Color(0xFF00A3FF), RoundedCornerShape(18.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF00A3FF).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Actualizaciones de VisionTV (GitHub)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Repositorio: github.com/iamblanquet/VisionTVip · Instalada: v${BuildConfig.VERSION_NAME}",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Content body based on state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF07111D))
                    .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                when (updateState) {
                    is AppUpdateState.Idle, is AppUpdateState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00E5FF),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Buscando nueva versión en GitHub...",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Consultando lanzamientos oficiales en iamblanquet/VisionTVip",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    is AppUpdateState.UpToDate -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(36.dp)
                            )
                            Column {
                                Text(
                                    text = "¡Tienes la última versión instalada! (v${updateState.currentVersion})",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tu aplicación VisionTV está actualizada con el último APK publicado en GitHub.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    is AppUpdateState.UpdateAvailable -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🎉 Nueva versión disponible: v${updateState.release.versionClean}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (updateState.release.apkSizeBytes > 0) {
                                    val mb = updateState.release.apkSizeBytes / (1024f * 1024f)
                                    Text(
                                        text = String.format(Locale.US, "%.1f MB", mb),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Text(
                                text = updateState.release.releaseTitle,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = updateState.release.releaseNotes,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    is AppUpdateState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Descargando ${updateState.release.apkFileName}...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${updateState.progressPercent}%",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (updateState.progressPercent / 100f).coerceIn(0f, 1f) },
                                color = Color(0xFF00A3FF),
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = if (updateState.totalMb > 0f) {
                                    String.format(
                                        Locale.US,
                                        "%.1f MB / %.1f MB descargados desde GitHub Releases",
                                        updateState.downloadedMb,
                                        updateState.totalMb
                                    )
                                } else {
                                    String.format(Locale.US, "%.1f MB descargados...", updateState.downloadedMb)
                                },
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    is AppUpdateState.ReadyToInstall -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(36.dp)
                            )
                            Column {
                                Text(
                                    text = "APK v${updateState.release.versionClean} descargado y listo",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Presiona 'Instalar Ahora' para actualizar la aplicación sin perder tu configuración.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    is AppUpdateState.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Error al verificar o descargar actualización",
                                    color = Color(0xFFF87171),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = updateState.message,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (updateState) {
                    is AppUpdateState.UpdateAvailable -> {
                        UpdateDialogButton(
                            label = "Descargar e Instalar v${updateState.release.versionClean}",
                            icon = Icons.Default.CloudDownload,
                            isPrimary = true,
                            onClick = { onDownloadUpdate(updateState.release) },
                            modifier = Modifier.focusRequester(primaryFocusRequester)
                        )
                        UpdateDialogButton(
                            label = "Más tarde",
                            isPrimary = false,
                            onClick = onClose
                        )
                    }

                    is AppUpdateState.ReadyToInstall -> {
                        UpdateDialogButton(
                            label = "Instalar Ahora",
                            icon = Icons.Default.SystemUpdateAlt,
                            isPrimary = true,
                            onClick = { onInstallApk(updateState.apkFile) },
                            modifier = Modifier.focusRequester(primaryFocusRequester)
                        )
                        UpdateDialogButton(
                            label = "Cerrar",
                            isPrimary = false,
                            onClick = onClose
                        )
                    }

                    is AppUpdateState.Downloading -> {
                        UpdateDialogButton(
                            label = "Ocultar",
                            isPrimary = false,
                            onClick = onClose,
                            modifier = Modifier.focusRequester(primaryFocusRequester)
                        )
                    }

                    else -> {
                        UpdateDialogButton(
                            label = "Volver a buscar",
                            icon = Icons.Default.Refresh,
                            isPrimary = true,
                            onClick = onCheckUpdates,
                            modifier = Modifier.focusRequester(primaryFocusRequester)
                        )
                        UpdateDialogButton(
                            label = "Cerrar",
                            isPrimary = false,
                            onClick = onClose
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDialogButton(
    label: String,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused && isPrimary -> Color(0xFF00A3FF)
            isFocused -> Color(0xFF1E3A5F)
            isPrimary -> Color(0xFF0284C7)
            else -> Color(0xFF162A45)
        },
        label = "updateBtnBg"
    )
    val borderColor = if (isFocused) Color(0xFF00E5FF) else if (isPrimary) Color(0xFF38BDF8) else Color(0xFF334155)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(if (isFocused) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 16.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
