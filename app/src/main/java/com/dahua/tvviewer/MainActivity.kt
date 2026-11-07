package com.dahua.tvviewer

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dahua.tvviewer.ui.MainViewModel
import com.dahua.tvviewer.ui.ScreenDestination
import com.dahua.tvviewer.ui.components.AdminPinDialog
import com.dahua.tvviewer.ui.components.UpdateDialog
import com.dahua.tvviewer.ui.screens.CameraGridScreen
import com.dahua.tvviewer.ui.screens.FullscreenCameraScreen
import com.dahua.tvviewer.ui.screens.SettingsScreen

private val TvDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00A3FF),     // Azul Primario VisionTV
    secondary = Color(0xFF0066FF),   // Azul Secundario VisionTV
    background = Color(0xFF0B1B2B),  // Azul Profundo VisionTV
    surface = Color(0xFF071220),
    onPrimary = Color(0xFF0B1B2B),
    onBackground = Color.White,
    onSurface = Color(0xFFE5E7EB)    // Gris Claro VisionTV
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep TV screen awake while viewing live security cameras
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(colorScheme = TvDarkColorScheme) {
                DahuaTvApp(viewModel = viewModel)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            viewModel.requestOpenSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun DahuaTvApp(viewModel: MainViewModel) {
    val isLoaded by viewModel.isInitialLoadDone.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val config by viewModel.nvrConfig.collectAsState()
    val gridLayout by viewModel.gridLayout.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val connectionTestState by viewModel.connectionTestState.collectAsState()
    val p2pTunnelState by viewModel.p2pTunnelState.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanningLan by viewModel.isScanningLan.collectAsState()
    val audioEnabled by viewModel.audioEnabledFullscreen.collectAsState()
    val isMainStreamFullscreen by viewModel.isMainStreamFullscreen.collectAsState()
    val diagnosticState by viewModel.networkDiagnostic.collectAsState()
    val knownLiveChannels by viewModel.knownLiveChannels.collectAsState()
    val isPinPromptVisible by viewModel.isPinPromptVisible.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val isUpdateDialogOpen by viewModel.isUpdateDialogOpen.collectAsState()

    if (!isLoaded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1B2B)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF00A3FF))
        }
        return
    }

    when (val screen = currentScreen) {
        is ScreenDestination.Grid -> {
            CameraGridScreen(
                config = config,
                gridLayout = gridLayout,
                currentPage = currentPage,
                p2pTunnelState = p2pTunnelState,
                diagnosticState = diagnosticState,
                knownLiveChannels = knownLiveChannels,
                updateState = appUpdateState,
                onUpdateLiveChannel = { channel, hasSignal -> viewModel.updateLiveChannel(channel, hasSignal) },
                onSelectLayout = { viewModel.setGridLayout(it) },
                onNextPage = { viewModel.nextPage() },
                onPrevPage = { viewModel.prevPage() },
                onReconnectP2p = { viewModel.ensureP2pTunnelIfRequired(config, forceReconnect = true) },
                onRunDiagnostic = { viewModel.runNetworkDiagnostic() },
                onCloseDiagnostic = { viewModel.closeNetworkDiagnostic() },
                onOpenFullscreen = { channel -> viewModel.openFullscreen(channel) },
                onSwapChannels = { chA, chB -> viewModel.swapChannels(chA, chB) },
                onMoveChannel = { ch, delta -> viewModel.moveChannel(ch, delta) },
                onOpenUpdateDialog = { viewModel.openUpdateDialog() },
                onOpenSettings = { viewModel.requestOpenSettings() }
            )
        }
        is ScreenDestination.Fullscreen -> {
            FullscreenCameraScreen(
                config = config,
                channelNumber = screen.channelNumber,
                p2pTunnelState = p2pTunnelState,
                audioEnabled = audioEnabled,
                isMainStream = isMainStreamFullscreen,
                onSwitchChannel = { delta -> viewModel.switchFullscreenChannel(delta) },
                onToggleQuality = { viewModel.toggleFullscreenStreamQuality() },
                onToggleAudio = { viewModel.toggleFullscreenAudio() },
                onBackToGrid = { viewModel.navigateToGrid() }
            )
        }
        is ScreenDestination.Settings -> {
            SettingsScreen(
                initialConfig = config,
                connectionTestState = connectionTestState,
                discoveredDevices = discoveredDevices,
                isScanningLan = isScanningLan,
                onScanLan = { viewModel.scanLanDevices() },
                onTestConnection = { method, ip, sn, user, pass, tcpPort, rtspPort ->
                    viewModel.testSmartPssConnection(method, ip, sn, user, pass, tcpPort, rtspPort)
                },
                onSaveConfig = { newConfig -> viewModel.saveNvrConfig(newConfig, navigateAfterSave = true) },
                onResetConfig = { viewModel.resetConfig() },
                onCancel = { viewModel.navigateToGrid() }
            )
        }
    }

    // Modal dialog to protect settings with Admin PIN
    if (isPinPromptVisible && config.hasAdminPin) {
        AdminPinDialog(
            targetPin = config.adminPin,
            onPinSuccess = { viewModel.onAdminPinSuccess() },
            onDismiss = { viewModel.dismissPinPrompt() }
        )
    }

    // Modal dialog for GitHub OTA Updates
    if (isUpdateDialogOpen) {
        UpdateDialog(
            updateState = appUpdateState,
            onCheckUpdates = { viewModel.checkForGithubUpdates(openDialogIfFound = false) },
            onDownloadUpdate = { release -> viewModel.downloadAndInstallUpdate(release) },
            onInstallApk = { apkFile -> viewModel.installDownloadedApk(apkFile) },
            onClose = { viewModel.closeUpdateDialog() }
        )
    }
}
