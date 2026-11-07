package com.dahua.tvviewer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dahua.tvviewer.R
import com.dahua.tvviewer.data.AppUpdateState
import com.dahua.tvviewer.data.ConnectionMethod
import com.dahua.tvviewer.data.GridLayoutMode
import com.dahua.tvviewer.data.NvrConfig
import com.dahua.tvviewer.ui.NetworkDiagnosticState
import com.dahua.tvviewer.ui.P2pTunnelUiState
import com.dahua.tvviewer.ui.components.RtspPlayerTile
import com.dahua.tvviewer.ui.components.TvIconButton
import com.dahua.tvviewer.ui.components.VisionTvBackground

@Composable
fun CameraGridScreen(
    config: NvrConfig,
    gridLayout: GridLayoutMode,
    currentPage: Int,
    p2pTunnelState: P2pTunnelUiState,
    diagnosticState: NetworkDiagnosticState,
    knownLiveChannels: Set<Int> = emptySet(),
    updateState: AppUpdateState = AppUpdateState.Idle,
    onUpdateLiveChannel: (Int, Boolean) -> Unit = { _, _ -> },
    onSelectLayout: (GridLayoutMode) -> Unit,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onReconnectP2p: () -> Unit,
    onRunDiagnostic: () -> Unit,
    onCloseDiagnostic: () -> Unit,
    onOpenFullscreen: (Int) -> Unit,
    onSwapChannels: (Int, Int) -> Unit = { _, _ -> },
    onMoveChannel: (Int, Int) -> Unit = { _, _ -> },
    onOpenUpdateDialog: () -> Unit = {},
    onOpenSettings: () -> Unit
) {
    var retryCounter by remember { mutableIntStateOf(0) }
    var isGridExpanded by remember { mutableStateOf(false) }
    var isOrganizeMode by remember { mutableStateOf(false) }
    var selectedSwapChannel by remember { mutableStateOf<Int?>(null) }
    var isGridMainStream by remember { mutableStateOf(false) }
    var isImmersiveGrid by remember { mutableStateOf(false) }

    BackHandler(enabled = isImmersiveGrid || isOrganizeMode) {
        if (isImmersiveGrid) {
            isImmersiveGrid = false
        } else {
            isOrganizeMode = false
            selectedSwapChannel = null
        }
    }

    val activeChannels = remember(config, knownLiveChannels) {
        val configured = config.getActiveChannels()
        if (config.autoSortBySignal && knownLiveChannels.isNotEmpty()) {
            val live = configured.filter { it in knownLiveChannels }
            val offline = configured.filter { it !in knownLiveChannels }
            live + offline
        } else {
            configured
        }
    }
    val cellsPerPage = gridLayout.cellsPerPage
    val columns = gridLayout.columns
    val rows = cellsPerPage / columns

    val totalPages = ((activeChannels.size + cellsPerPage - 1) / cellsPerPage).coerceAtLeast(1)
    val safePage = currentPage.coerceIn(0, totalPages - 1)
    val startIndex = safePage * cellsPerPage
    val pageChannels = activeChannels.drop(startIndex).take(cellsPerPage)

    val firstCameraFocusRequester = remember { FocusRequester() }

    LaunchedEffect(gridLayout, safePage, isImmersiveGrid) {
        try {
            firstCameraFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    VisionTvBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isImmersiveGrid) 0.dp else 8.dp,
                    vertical = if (isImmersiveGrid) 0.dp else 6.dp
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Minimalist Single-Row Action Bar (Hidden when Grid is in Fullscreen Mode)
                if (!isImmersiveGrid) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Brand Mark + Grid selector pills
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher),
                                contentDescription = "VisionTV",
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xD90B1B2B))
                                    .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(8.dp))
                                    .padding(3.dp)
                            ) {
                                val defaultModes = listOf(GridLayoutMode.GRID_4, GridLayoutMode.GRID_8, GridLayoutMode.GRID_12)
                                val displayedModes = if (isGridExpanded) {
                                    listOf(
                                        GridLayoutMode.GRID_1,
                                        GridLayoutMode.GRID_4,
                                        GridLayoutMode.GRID_8,
                                        GridLayoutMode.GRID_9,
                                        GridLayoutMode.GRID_12,
                                        GridLayoutMode.GRID_16
                                    )
                                } else {
                                    if (gridLayout !in defaultModes) defaultModes + gridLayout else defaultModes
                                }

                                displayedModes.forEach { mode ->
                                    TvActionButton(
                                        label = "${mode.cellsPerPage}",
                                        isSelected = gridLayout == mode,
                                        onClick = { onSelectLayout(mode) },
                                        modifier = Modifier.height(34.dp)
                                    )
                                }

                                TvIconButton(
                                    icon = if (isGridExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isGridExpanded) "Menos opciones de cuadrícula" else "Ver todas las opciones de cuadrícula",
                                    onClick = { isGridExpanded = !isGridExpanded },
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Quick Organize Grid Button (Accessible to ANY user without Admin PIN)
                            TvActionButton(
                                label = if (isOrganizeMode) "✓ Listo" else "Organizar",
                                icon = Icons.Default.SwapHoriz,
                                isSelected = isOrganizeMode,
                                onClick = {
                                    isOrganizeMode = !isOrganizeMode
                                    selectedSwapChannel = null
                                },
                                modifier = Modifier.height(34.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // SD / HD Quality Switcher for Main Grid
                            TvActionButton(
                                label = if (isGridMainStream) "HD" else "SD",
                                isSelected = isGridMainStream,
                                onClick = { isGridMainStream = !isGridMainStream },
                                modifier = Modifier.height(34.dp)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Fullscreen Grid Button (Hides Top Bar; Back button restores normal grid)
                            TvActionButton(
                                label = "Pantalla Completa",
                                icon = Icons.Default.Fullscreen,
                                isSelected = false,
                                onClick = {
                                    isOrganizeMode = false
                                    selectedSwapChannel = null
                                    isImmersiveGrid = true
                                },
                                modifier = Modifier.height(34.dp)
                            )
                        }

                        // Right: Page scroll and action icons in the exact same line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (totalPages > 1) {
                                TvIconButton(
                                    icon = Icons.Default.ChevronLeft,
                                    contentDescription = "Página anterior",
                                    onClick = onPrevPage,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "${safePage + 1}/$totalPages",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                TvIconButton(
                                    icon = Icons.Default.ChevronRight,
                                    contentDescription = "Página siguiente",
                                    onClick = onNextPage,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            TvIconButton(
                                icon = Icons.Default.Refresh,
                                contentDescription = "Actualizar cámaras",
                                onClick = {
                                    onReconnectP2p()
                                    retryCounter += 1
                                },
                                modifier = Modifier.size(36.dp)
                            )

                            TvIconButton(
                                icon = Icons.Default.NetworkCheck,
                                contentDescription = "Diagnóstico",
                                onClick = onRunDiagnostic,
                                modifier = Modifier.size(36.dp)
                            )

                            if (updateState is AppUpdateState.UpdateAvailable) {
                                TvActionButton(
                                    label = "Actualizar v${updateState.release.versionClean}",
                                    icon = Icons.Default.SystemUpdateAlt,
                                    isSelected = true,
                                    onClick = onOpenUpdateDialog,
                                    modifier = Modifier.height(36.dp)
                                )
                            } else {
                                TvIconButton(
                                    icon = Icons.Default.SystemUpdateAlt,
                                    contentDescription = "Buscar actualización en GitHub",
                                    onClick = onOpenUpdateDialog,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            TvIconButton(
                                icon = Icons.Default.Settings,
                                contentDescription = "Configurar",
                                onClick = onOpenSettings,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Quick Organize Info Banner
                AnimatedVisibility(visible = isOrganizeMode && !isImmersiveGrid) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE60F172A))
                        .border(1.dp, Color(0xFF00A3FF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedSwapChannel != null)
                                "Cámara $selectedSwapChannel seleccionada: haz clic en otra cámara para intercambiarlas de posición"
                            else
                                "Modo Organizar: Haz clic en una cámara para cambiarla por otra, o usa las flechas ◄ ► en cada cámara",
                            color = if (selectedSwapChannel != null) Color(0xFFFDE047) else Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TvActionButton(
                        label = "✓ Terminar",
                        isSelected = true,
                        onClick = {
                            isOrganizeMode = false
                            selectedSwapChannel = null
                        },
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            val isP2pMode = config.connectionMethod == ConnectionMethod.SN_P2P
            val p2pPort = (p2pTunnelState as? P2pTunnelUiState.Ready)?.localPort

            // Camera Grid Area or Unconfigured Welcome
            if (!config.isConfigured) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0A101D))
                        .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Bienvenido a Dahua TV Viewer",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aún no has configurado tu grabador NVR Dahua o cámaras de seguridad.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Utiliza el nuevo asistente guiado paso a paso para conectarlo rápidamente.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        TvActionButton(
                            label = "Iniciar Configuración Guiada (Paso a Paso)",
                            icon = Icons.Default.PlayArrow,
                            isSelected = true,
                            onClick = onOpenSettings,
                            modifier = Modifier.focusRequester(firstCameraFocusRequester)
                        )
                    }
                }
            } else if (isP2pMode && p2pTunnelState is P2pTunnelUiState.Connecting) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0A0F1D), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF00B4D8),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Estableciendo túnel P2P Cloud con NVR Dahua (${config.serialNumber})...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Autenticando usuario '${config.username}' en Easy4ip / SmartPSS Cloud",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (isP2pMode && p2pTunnelState is P2pTunnelUiState.Error) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0A0F1D), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No se pudo establecer el túnel P2P por Número de Serie",
                            color = Color(0xFFF87171),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = p2pTunnelState.errorText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvActionButton(
                                label = "Reintentar Conexión P2P",
                                icon = Icons.Default.Refresh,
                                isSelected = true,
                                onClick = onReconnectP2p
                            )
                            TvActionButton(
                                label = "Abrir Configuración",
                                icon = Icons.Default.Settings,
                                isSelected = false,
                                onClick = onOpenSettings
                            )
                        }
                    }
                }
            } else {
                val isDense = gridLayout.cellsPerPage >= 12
                val gridSpacing = when {
                    isImmersiveGrid -> 0.dp
                    isDense -> 6.dp
                    else -> 8.dp
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing)
                        ) {
                            for (c in 0 until columns) {
                                val cellIndex = r * columns + c
                                val channelNumber = pageChannels.getOrNull(cellIndex)

                                if (channelNumber != null) {
                                    val isFirstCell = cellIndex == 0
                                    key(gridLayout, channelNumber, isGridMainStream) {
                                        CameraCardCell(
                                            channelNumber = channelNumber,
                                            channelTitle = config.getChannelTitle(channelNumber),
                                            positionNumber = startIndex + cellIndex + 1,
                                            isOrganizeMode = isOrganizeMode,
                                            isImmersiveGrid = isImmersiveGrid,
                                            isSelectedForSwap = selectedSwapChannel == channelNumber,
                                            onMoveLeft = { onMoveChannel(channelNumber, -1) },
                                            onMoveRight = { onMoveChannel(channelNumber, 1) },
                                            rtspUrl = config.buildRtspUrl(
                                                channelNumber = channelNumber,
                                                isMainStream = isGridMainStream,
                                                p2pLocalPort = p2pPort
                                            ),
                                            useTcp = config.useTcpTransport,
                                            username = config.username,
                                            password = config.password,
                                            streamBadge = if (isGridMainStream) "HD" else "SD",
                                            isDenseGrid = isDense,
                                            retryTrigger = retryCounter,
                                            onSignalStatusChanged = onUpdateLiveChannel,
                                            onClick = {
                                                if (isOrganizeMode) {
                                                    val currentSelected = selectedSwapChannel
                                                    if (currentSelected == null) {
                                                        selectedSwapChannel = channelNumber
                                                    } else if (currentSelected == channelNumber) {
                                                        selectedSwapChannel = null
                                                    } else {
                                                        onSwapChannels(currentSelected, channelNumber)
                                                        selectedSwapChannel = null
                                                    }
                                                } else {
                                                    onOpenFullscreen(channelNumber)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight()
                                                .then(
                                                    if (isFirstCell) Modifier.focusRequester(firstCameraFocusRequester)
                                                    else Modifier
                                                )
                                        )
                                    }
                                } else {
                                    // Empty Slot Placeholder
                                    val emptyShape = RoundedCornerShape(if (isImmersiveGrid) 0.dp else 8.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(emptyShape)
                                            .background(Color(0xFF0A0F1D))
                                            .then(
                                                if (isImmersiveGrid) Modifier
                                                else Modifier.border(1.dp, Color(0xFF1E293B), emptyShape)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Canal vacío",
                                            color = Color(0xFF475569),
                                            fontSize = if (isDense) 10.sp else 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (diagnosticState.isOpen) {
            NetworkDiagnosticDialog(
                state = diagnosticState,
                onRetry = onRunDiagnostic,
                onClose = onCloseDiagnostic
            )
        }
    }
}
}

@Composable
private fun CameraCardCell(
    channelNumber: Int,
    channelTitle: String,
    positionNumber: Int = 1,
    rtspUrl: String,
    useTcp: Boolean,
    username: String = "",
    password: String = "",
    streamBadge: String,
    isDenseGrid: Boolean = false,
    retryTrigger: Int,
    isOrganizeMode: Boolean = false,
    isImmersiveGrid: Boolean = false,
    isSelectedForSwap: Boolean = false,
    onMoveLeft: () -> Unit = {},
    onMoveRight: () -> Unit = {},
    onSignalStatusChanged: ((Int, Boolean) -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelectedForSwap -> Color(0xFFFBBF24)
            isOrganizeMode && isFocused -> Color(0xFF00E5FF)
            isFocused -> Color(0xFF00A3FF)
            isOrganizeMode -> Color(0xFF0284C7)
            else -> Color(0xFF1E3A5F)
        },
        label = "borderFocus"
    )
    val borderWidth = if (isSelectedForSwap || isFocused) 3.dp else if (isOrganizeMode) 2.dp else 1.5.dp
    val cellShape = RoundedCornerShape(if (isImmersiveGrid) 0.dp else 8.dp)

    Box(
        modifier = modifier
            .clip(cellShape)
            .background(Color(0xFF07111D))
            .then(
                if (isImmersiveGrid) Modifier
                else Modifier.border(borderWidth, borderColor, cellShape)
            )
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
    ) {
        RtspPlayerTile(
            rtspUrl = rtspUrl,
            channelTitle = channelTitle,
            channelNumber = channelNumber,
            useTcp = useTcp,
            username = username,
            password = password,
            audioEnabled = false,
            isFullscreen = false,
            isDenseGrid = isDenseGrid,
            streamQualityBadge = streamBadge,
            retryTrigger = retryTrigger,
            onSignalStatusChanged = onSignalStatusChanged
        )

        if (isOrganizeMode) {
            // Position Badge in top left corner (e.g. #1, #2)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelectedForSwap) Color(0xFFF59E0B) else Color(0xD90066FF))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Posición #$positionNumber",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Move Left / Right buttons in top right corner
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xD90B1B2B))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                        .clickable { onMoveLeft() }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Mover a la izquierda",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xD90B1B2B))
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                        .clickable { onMoveRight() }
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Mover a la derecha",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isSelectedForSwap) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xE678350F))
                        .border(1.5.dp, Color(0xFFFBBF24), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Seleccionada · Clic en otra para intercambiar",
                        color = Color(0xFFFEF08A),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Sleek Minimalist Corner Expand Indicator on Focus / Touch
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color(0xCC050811), RoundedCornerShape(4.dp))
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Pantalla Completa",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TvActionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isFocused -> Color(0xFF00E5FF)
        isSelected -> Color(0xFF0284C7)
        else -> Color(0xFF1E293B)
    }

    val contentColor = when {
        isFocused -> Color(0xFF050811)
        else -> Color.White
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF334155),
                shape = RoundedCornerShape(8.dp)
            )
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
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}


@Composable
private fun NetworkDiagnosticDialog(
    state: NetworkDiagnosticState,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            closeFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, Color(0xFF00B4D8), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = Color(0xFF00B4D8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Diagnóstico de Red y Conectividad",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (state.isRunning) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00B4D8), modifier = Modifier.size(36.dp))
                        Text(
                            text = "Probando conexión con ${state.targetIp}...",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DiagnosticRow(
                            label = "IP del Televisor (Local):",
                            value = state.localTvIp.ifBlank { "Detectando..." }
                        )
                        DiagnosticRow(
                            label = "IP Grabador NVR:",
                            value = state.targetIp.ifBlank { "No configurada" }
                        )
                        DiagnosticStatusRow(
                            label = "Puerto 37777 (TCP Dahua):",
                            status = state.tcpPort37777Ok
                        )
                        DiagnosticStatusRow(
                            label = "Puerto 554 (RTSP Video):",
                            status = state.rtspPort554Ok
                        )
                        DiagnosticStatusRow(
                            label = "Autenticación RTSP Digest:",
                            status = state.rtspAuthOk
                        )
                    }

                    if (state.message.isNotBlank()) {
                        val isSuccess = state.rtspAuthOk == true
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSuccess) Color(0xFF064E3B) else Color(0xFF450A0A))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = state.message,
                                color = if (isSuccess) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvActionButton(
                            label = "Reintentar",
                            icon = Icons.Default.Refresh,
                            isSelected = false,
                            onClick = onRetry
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        TvActionButton(
                            label = "Cerrar",
                            icon = Icons.Default.Close,
                            isSelected = true,
                            onClick = onClose,
                            modifier = Modifier.focusRequester(closeFocusRequester)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DiagnosticStatusRow(label: String, status: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (status) {
                true -> {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Correcto", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                false -> {
                    Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Fallido", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                null -> {
                    Text(text = "—", color = Color(0xFF64748B), fontSize = 13.sp)
                }
            }
        }
    }
}
