package com.dahua.tvviewer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dahua.tvviewer.data.AppUpdateState
import com.dahua.tvviewer.data.ConnectionMethod
import com.dahua.tvviewer.data.DahuaLanDiscovery
import com.dahua.tvviewer.data.DahuaP2pTunnelManager
import com.dahua.tvviewer.data.DiscoveredDahuaDevice
import com.dahua.tvviewer.data.GithubReleaseInfo
import com.dahua.tvviewer.data.GithubUpdateManager
import com.dahua.tvviewer.data.GridLayoutMode
import com.dahua.tvviewer.data.NvrConfig
import com.dahua.tvviewer.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.MessageDigest

sealed class ScreenDestination {
    data object Grid : ScreenDestination()
    data class Fullscreen(val channelNumber: Int) : ScreenDestination()
    data object Settings : ScreenDestination()
}

data class NetworkDiagnosticState(
    val isOpen: Boolean = false,
    val isRunning: Boolean = false,
    val localTvIp: String = "",
    val targetIp: String = "",
    val tcpPort37777Ok: Boolean? = null,
    val rtspPort554Ok: Boolean? = null,
    val rtspAuthOk: Boolean? = null,
    val message: String = ""
)

sealed class ConnectionTestState {
    data object Idle : ConnectionTestState()
    data object Testing : ConnectionTestState()
    data class Success(
        val message: String,
        val resolvedIp: String? = null,
        val discoveredChannels: Int? = null,
        val discoveredName: String? = null
    ) : ConnectionTestState()
    data class Error(val message: String) : ConnectionTestState()
}

sealed class P2pTunnelUiState {
    data object NotUsed : P2pTunnelUiState()
    data object Connecting : P2pTunnelUiState()
    data class Ready(val localPort: Int, val statusText: String) : P2pTunnelUiState()
    data class Error(val errorText: String) : P2pTunnelUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application.applicationContext)

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Grid)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _gridLayout = MutableStateFlow(GridLayoutMode.GRID_4)
    val gridLayout: StateFlow<GridLayoutMode> = _gridLayout.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isInitialLoadDone = MutableStateFlow(false)
    val isInitialLoadDone: StateFlow<Boolean> = _isInitialLoadDone.asStateFlow()

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _p2pTunnelState = MutableStateFlow<P2pTunnelUiState>(P2pTunnelUiState.NotUsed)
    val p2pTunnelState: StateFlow<P2pTunnelUiState> = _p2pTunnelState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDahuaDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDahuaDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanningLan = MutableStateFlow(false)
    val isScanningLan: StateFlow<Boolean> = _isScanningLan.asStateFlow()

    private val _audioEnabledFullscreen = MutableStateFlow(false)
    val audioEnabledFullscreen: StateFlow<Boolean> = _audioEnabledFullscreen.asStateFlow()

    private val _isMainStreamFullscreen = MutableStateFlow(false)
    val isMainStreamFullscreen: StateFlow<Boolean> = _isMainStreamFullscreen.asStateFlow()

    private val _networkDiagnostic = MutableStateFlow(NetworkDiagnosticState())
    val networkDiagnostic: StateFlow<NetworkDiagnosticState> = _networkDiagnostic.asStateFlow()

    private val _isPinPromptVisible = MutableStateFlow(false)
    val isPinPromptVisible: StateFlow<Boolean> = _isPinPromptVisible.asStateFlow()

    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()

    private val _isUpdateDialogOpen = MutableStateFlow(false)
    val isUpdateDialogOpen: StateFlow<Boolean> = _isUpdateDialogOpen.asStateFlow()

    init {
        checkForGithubUpdates(openDialogIfFound = true)
    }

    fun openUpdateDialog() {
        _isUpdateDialogOpen.value = true
        if (_appUpdateState.value is AppUpdateState.Idle || _appUpdateState.value is AppUpdateState.Error) {
            checkForGithubUpdates(openDialogIfFound = false)
        }
    }

    fun closeUpdateDialog() {
        _isUpdateDialogOpen.value = false
    }

    fun checkForGithubUpdates(openDialogIfFound: Boolean = false) {
        viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Checking
            val result = GithubUpdateManager.checkForUpdates()
            _appUpdateState.value = result
            if (openDialogIfFound && result is AppUpdateState.UpdateAvailable) {
                _isUpdateDialogOpen.value = true
            }
        }
    }

    fun downloadAndInstallUpdate(release: GithubReleaseInfo) {
        val appContext = getApplication<Application>().applicationContext
        viewModelScope.launch {
            _appUpdateState.value = AppUpdateState.Downloading(
                release = release,
                progressPercent = 0,
                downloadedMb = 0f,
                totalMb = if (release.apkSizeBytes > 0) release.apkSizeBytes / (1024f * 1024f) else 0f
            )
            val result = GithubUpdateManager.downloadUpdateApk(
                context = appContext,
                release = release,
                onProgress = { progressState ->
                    _appUpdateState.value = progressState
                }
            )
            _appUpdateState.value = result
            if (result is AppUpdateState.ReadyToInstall) {
                GithubUpdateManager.installApk(appContext, result.apkFile)
            }
        }
    }

    fun installDownloadedApk(apkFile: File) {
        val appContext = getApplication<Application>().applicationContext
        GithubUpdateManager.installApk(appContext, apkFile)
    }

    val knownLiveChannels: StateFlow<Set<Int>> = repository.knownLiveChannelsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = repository.defaultLiveChannels
        )

    fun updateLiveChannel(channelNumber: Int, hasSignal: Boolean) {
        val current = knownLiveChannels.value.toMutableSet()
        val changed = if (hasSignal) {
            current.add(channelNumber)
        } else {
            false
        }
        if (changed) {
            viewModelScope.launch {
                repository.saveKnownLiveChannels(current)
            }
        }
    }

    val nvrConfig: StateFlow<NvrConfig> = repository.nvrConfigFlow
        .onEach { rawConfig ->
            val config = rawConfig

            if (!_isInitialLoadDone.value) {
                _gridLayout.value = config.defaultGridLayout
                if (!config.isConfigured) {
                    _currentScreen.value = ScreenDestination.Settings
                }
                _isInitialLoadDone.value = true
            }
            ensureP2pTunnelIfRequired(config)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = NvrConfig()
        )

    fun ensureP2pTunnelIfRequired(config: NvrConfig = nvrConfig.value, forceReconnect: Boolean = false) {
        if (config.connectionMethod != ConnectionMethod.SN_P2P || config.serialNumber.isBlank()) {
            _p2pTunnelState.value = P2pTunnelUiState.NotUsed
            return
        }

        val directIp = config.ipAddress.trim()

        if (directIp.isNotBlank()) {
            // El grabador tiene IP directa disponible (192.168.68.142); no requerimos túnel P2P loopback
            DahuaP2pTunnelManager.stopTunnel()
            _p2pTunnelState.value = P2pTunnelUiState.NotUsed
            return
        }

        if (forceReconnect) {
            DahuaP2pTunnelManager.stopTunnel()
        } else {
            val existingPort = DahuaP2pTunnelManager.getActiveLocalPort(config.serialNumber)
            if (existingPort != null && existingPort > 0) {
                _p2pTunnelState.value = P2pTunnelUiState.Ready(
                    localPort = existingPort,
                    statusText = "P2P Cloud Online (SN: ${config.serialNumber})"
                )
                return
            }
        }

        _p2pTunnelState.value = P2pTunnelUiState.Connecting
        viewModelScope.launch {
            val res = DahuaP2pTunnelManager.ensureTunnel(
                serialNumber = config.serialNumber,
                username = config.username,
                password = config.password
            )
            if (res.success && res.localPort > 0) {
                _p2pTunnelState.value = P2pTunnelUiState.Ready(
                    localPort = res.localPort,
                    statusText = "P2P Cloud Online · SN: ${config.serialNumber}"
                )
            } else {
                _p2pTunnelState.value = P2pTunnelUiState.Error(res.message)
            }
        }
    }

    fun scanLanDevices() {
        if (_isScanningLan.value) return
        _isScanningLan.value = true
        viewModelScope.launch {
            val found = DahuaLanDiscovery.discoverDevices(getApplication(), timeoutMs = 2600)
            _discoveredDevices.value = found
            _isScanningLan.value = false
            if (found.isNotEmpty()) {
                _connectionTestState.value = ConnectionTestState.Success(
                    "Se encontraron ${found.size} grabador(es) Dahua en tu misma red local."
                )
            } else {
                _connectionTestState.value = ConnectionTestState.Idle
            }
        }
    }

    fun setGridLayout(mode: GridLayoutMode) {
        _gridLayout.value = mode
        _currentPage.value = 0
        viewModelScope.launch {
            val updated = nvrConfig.value.copy(defaultGridLayout = mode)
            repository.saveConfig(updated)
        }
    }

    fun nextPage() {
        val totalActive = nvrConfig.value.getActiveChannels().size
        val perPage = _gridLayout.value.cellsPerPage
        val totalPages = ((totalActive + perPage - 1) / perPage).coerceAtLeast(1)
        if (_currentPage.value < totalPages - 1) {
            _currentPage.value += 1
        } else {
            _currentPage.value = 0
        }
    }

    fun prevPage() {
        val totalActive = nvrConfig.value.getActiveChannels().size
        val perPage = _gridLayout.value.cellsPerPage
        val totalPages = ((totalActive + perPage - 1) / perPage).coerceAtLeast(1)
        if (_currentPage.value > 0) {
            _currentPage.value -= 1
        } else {
            _currentPage.value = totalPages - 1
        }
    }

    fun openFullscreen(channelNumber: Int) {
        _isMainStreamFullscreen.value = false // Always start with SUB stream first
        _currentScreen.value = ScreenDestination.Fullscreen(channelNumber)
    }

    fun switchFullscreenChannel(delta: Int) {
        val current = (_currentScreen.value as? ScreenDestination.Fullscreen)?.channelNumber ?: return
        val active = nvrConfig.value.getActiveChannels()
        if (active.isEmpty()) return
        val currentIndex = active.indexOf(current)
        val nextIndex = if (currentIndex != -1) {
            (currentIndex + delta + active.size) % active.size
        } else {
            if (delta >= 0) 0 else active.lastIndex
        }
        _isMainStreamFullscreen.value = false // Start with SUB stream when switching channels
        _currentScreen.value = ScreenDestination.Fullscreen(active[nextIndex])
    }

    fun toggleFullscreenStreamQuality() {
        _isMainStreamFullscreen.value = !_isMainStreamFullscreen.value
    }

    fun toggleFullscreenAudio() {
        _audioEnabledFullscreen.value = !_audioEnabledFullscreen.value
    }

    fun requestOpenSettings() {
        if (nvrConfig.value.hasAdminPin) {
            _isPinPromptVisible.value = true
        } else {
            openSettings()
        }
    }

    fun dismissPinPrompt() {
        _isPinPromptVisible.value = false
    }

    fun onAdminPinSuccess() {
        _isPinPromptVisible.value = false
        openSettings()
    }

    fun openSettings() {
        _connectionTestState.value = ConnectionTestState.Idle
        _currentScreen.value = ScreenDestination.Settings
    }

    fun resetConfig() {
        viewModelScope.launch {
            val empty = NvrConfig()
            repository.saveConfig(empty)
            _gridLayout.value = empty.defaultGridLayout
            _currentPage.value = 0
            _discoveredDevices.value = emptyList()
            _connectionTestState.value = ConnectionTestState.Idle
            _currentScreen.value = ScreenDestination.Settings
        }
    }

    fun navigateToGrid() {
        _isMainStreamFullscreen.value = false
        _currentScreen.value = ScreenDestination.Grid
    }

    fun saveNvrConfig(config: NvrConfig, navigateAfterSave: Boolean = true) {
        viewModelScope.launch {
            repository.saveConfig(config)
            _gridLayout.value = config.defaultGridLayout
            _currentPage.value = 0
            ensureP2pTunnelIfRequired(config, forceReconnect = true)
            if (navigateAfterSave) {
                _currentScreen.value = ScreenDestination.Grid
            }
        }
    }

    fun swapChannels(channelA: Int, channelB: Int) {
        val currentOrder = nvrConfig.value.getActiveChannels().toMutableList()
        val indexA = currentOrder.indexOf(channelA)
        val indexB = currentOrder.indexOf(channelB)
        if (indexA != -1 && indexB != -1 && indexA != indexB) {
            val temp = currentOrder[indexA]
            currentOrder[indexA] = currentOrder[indexB]
            currentOrder[indexB] = temp
            viewModelScope.launch {
                val updated = nvrConfig.value.copy(
                    customChannelOrder = currentOrder,
                    autoSortBySignal = false
                )
                repository.saveConfig(updated)
            }
        }
    }

    fun moveChannel(channel: Int, delta: Int) {
        val currentOrder = nvrConfig.value.getActiveChannels().toMutableList()
        val index = currentOrder.indexOf(channel)
        if (index != -1) {
            val targetIndex = (index + delta).coerceIn(0, currentOrder.size - 1)
            if (targetIndex != index) {
                val temp = currentOrder[index]
                currentOrder[index] = currentOrder[targetIndex]
                currentOrder[targetIndex] = temp
                viewModelScope.launch {
                    val updated = nvrConfig.value.copy(
                        customChannelOrder = currentOrder,
                        autoSortBySignal = false
                    )
                    repository.saveConfig(updated)
                }
            }
        }
    }

    fun testSmartPssConnection(
        method: ConnectionMethod,
        ipOrDomain: String,
        serialNo: String,
        username: String,
        password: String,
        tcpPort: Int,
        rtspPort: Int
    ) {
        _connectionTestState.value = ConnectionTestState.Testing
        viewModelScope.launch {
            if (method == ConnectionMethod.SN_P2P) {
                val cleanSn = serialNo.trim()
                if (cleanSn.isEmpty()) {
                    _connectionTestState.value = ConnectionTestState.Error(
                        "Ingresa el Número de Serie (SN) del NVR Dahua"
                    )
                    return@launch
                }

                val targetIp = when {
                    ipOrDomain.isNotBlank() -> ipOrDomain.trim()
                    else -> _discoveredDevices.value.firstOrNull { it.serialNo.equals(cleanSn, ignoreCase = true) }?.ipAddress ?: ""
                }

                if (targetIp.isNotBlank()) {
                    val directOk = withContext(Dispatchers.IO) {
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(targetIp, rtspPort), 2500)
                                true
                            }
                        } catch (_: Exception) {
                            false
                        }
                    }

                    if (directOk) {
                        _connectionTestState.value = ConnectionTestState.Success(
                            message = "¡Grabador En Línea! Conectado con éxito a $targetIp (SN: $cleanSn). Puerto RTSP: OK.",
                            resolvedIp = targetIp
                        )
                        return@launch
                    }
                }

                // Fallback: connect via Dahua P2P Cloud (Easy4ip / SmartPSS Lite P2P protocol)
                val p2pRes = DahuaP2pTunnelManager.ensureTunnel(
                    serialNumber = cleanSn,
                    username = username.trim(),
                    password = password
                )
                if (p2pRes.success) {
                    _p2pTunnelState.value = P2pTunnelUiState.Ready(
                        localPort = p2pRes.localPort,
                        statusText = "P2P Cloud Online · SN: $cleanSn"
                    )
                    _connectionTestState.value = ConnectionTestState.Success(
                        message = p2pRes.message
                    )
                } else {
                    _connectionTestState.value = ConnectionTestState.Error(
                        if (targetIp.isNotBlank()) {
                            "No se pudo conectar a $targetIp (Puerto RTSP $rtspPort cerrado). Verifica que el TV y el grabador estén en la misma red."
                        } else {
                            "No se encontró el grabador con SN $cleanSn en la red local. Selecciona 'IP Local / Dominio' e ingresa la IP 192.168.68.142."
                        }
                    )
                }
                return@launch
            }

            // Direct IP / Domain test
            val targetIp = ipOrDomain.trim()
            if (targetIp.isEmpty()) {
                _connectionTestState.value = ConnectionTestState.Error(
                    "Ingresa la dirección IP o Dominio del NVR Dahua"
                )
                return@launch
            }

            val probeResult = withContext(Dispatchers.IO) {
                val tcpOk = try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetIp, tcpPort), 2500)
                        true
                    }
                } catch (_: Exception) {
                    false
                }

                val rtspOk = try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetIp, rtspPort), 2500)
                        true
                    }
                } catch (_: Exception) {
                    false
                }

                Pair(tcpOk, rtspOk)
            }

            val (tcpOk, rtspOk) = probeResult
            if (rtspOk) {
                _connectionTestState.value = ConnectionTestState.Success(
                    message = "¡Dispositivo En Línea ($targetIp)! Puerto RTSP $rtspPort: OK · Puerto TCP $tcpPort: ${if (tcpOk) "OK" else "Cerrado"}",
                    resolvedIp = targetIp
                )
            } else {
                _connectionTestState.value = ConnectionTestState.Error(
                    "El puerto RTSP ($rtspPort) está cerrado en $targetIp. Si tu NVR está en otra red, selecciona el método 'SN (P2P)'."
                )
            }
        }
    }

    fun runNetworkDiagnostic() {
        val config = nvrConfig.value
        val targetIp = config.ipAddress.trim().ifEmpty { "192.168.1.1" }
        _networkDiagnostic.value = NetworkDiagnosticState(isOpen = true, isRunning = true, targetIp = targetIp)

        viewModelScope.launch {
            val localIp = withContext(Dispatchers.IO) {
                try {
                    NetworkInterface.getNetworkInterfaces()?.toList()
                        ?.flatMap { it.inetAddresses.toList() }
                        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                        ?.hostAddress ?: "Desconocida"
                } catch (_: Exception) { "Desconocida" }
            }

            val probe = withContext(Dispatchers.IO) {
                var tcp37777 = false
                var rtsp554 = false
                var rtspAuth: Boolean? = null
                var detail = ""

                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetIp, 37777), 2500)
                        tcp37777 = true
                    }
                } catch (_: Exception) {}

                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(targetIp, 554), 2500)
                        rtsp554 = true
                    }
                } catch (_: Exception) {}

                if (rtsp554) {
                    try {
                        Socket().use { s ->
                            s.soTimeout = 3000
                            s.connect(InetSocketAddress(targetIp, 554), 2500)
                            val w = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)
                            val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))

                            val uri = "rtsp://$targetIp:554/cam/realmonitor?channel=1&subtype=1"
                            w.write("DESCRIBE $uri RTSP/1.0\r\nCSeq: 1\r\nAccept: application/sdp\r\n\r\n")
                            w.flush()

                            var realm = ""
                            var nonce = ""
                            while (true) {
                                val line = r.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.contains("nonce=", ignoreCase = true)) {
                                    val realmMatch = Regex("realm=\"([^\"]+)\"").find(line)
                                    val nonceMatch = Regex("nonce=\"([^\"]+)\"").find(line)
                                    realm = realmMatch?.groupValues?.getOrNull(1) ?: ""
                                    nonce = nonceMatch?.groupValues?.getOrNull(1) ?: ""
                                }
                            }

                            if (realm.isNotEmpty() && nonce.isNotEmpty()) {
                                fun md5(str: String): String {
                                    val digest = MessageDigest.getInstance("MD5").digest(str.toByteArray(Charsets.UTF_8))
                                    return digest.joinToString("") { "%02x".format(it) }
                                }
                                val ha1 = md5("${config.username}:$realm:${config.password}")
                                val ha2 = md5("DESCRIBE:$uri")
                                val resp = md5("$ha1:$nonce:$ha2")

                                val authHdr = "Digest username=\"${config.username}\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$resp\""
                                w.write("DESCRIBE $uri RTSP/1.0\r\nCSeq: 2\r\nAuthorization: $authHdr\r\nAccept: application/sdp\r\n\r\n")
                                w.flush()

                                val statusLine = r.readLine() ?: ""
                                if (statusLine.contains("200 OK")) {
                                    rtspAuth = true
                                    detail = "¡Conexión y credenciales 100% correctas! El NVR Dahua respondió RTSP 200 OK."
                                } else {
                                    rtspAuth = false
                                    detail = "El grabador respondió: $statusLine (Verifica usuario o contraseña)."
                                }
                            } else {
                                detail = "El grabador no envió el desafío de autenticación Digest."
                            }
                        }
                    } catch (e: Exception) {
                        detail = "Error en el diálogo RTSP: ${e.localizedMessage}"
                    }
                } else {
                    detail = "El televisor (IP $localIp) NO puede alcanzar $targetIp:554. Tu televisor está en otra red o el router aísla la conexión Wi-Fi."
                }

                Triple(tcp37777, rtsp554, Pair(rtspAuth, detail))
            }

            _networkDiagnostic.value = NetworkDiagnosticState(
                isOpen = true,
                isRunning = false,
                localTvIp = localIp,
                targetIp = targetIp,
                tcpPort37777Ok = probe.first,
                rtspPort554Ok = probe.second,
                rtspAuthOk = probe.third.first,
                message = probe.third.second
            )
        }
    }

    fun closeNetworkDiagnostic() {
        _networkDiagnostic.value = _networkDiagnostic.value.copy(isOpen = false)
    }
}
