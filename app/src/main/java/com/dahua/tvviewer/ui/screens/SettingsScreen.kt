package com.dahua.tvviewer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dahua.tvviewer.R
import com.dahua.tvviewer.data.ConnectionMethod
import com.dahua.tvviewer.data.DiscoveredDahuaDevice
import com.dahua.tvviewer.data.GridLayoutMode
import com.dahua.tvviewer.data.NvrConfig
import com.dahua.tvviewer.ui.ConnectionTestState
import com.dahua.tvviewer.ui.components.TvButton
import com.dahua.tvviewer.ui.components.TvIconButton
import com.dahua.tvviewer.ui.components.TvPill
import com.dahua.tvviewer.ui.components.TvStepProgress
import com.dahua.tvviewer.ui.components.VisionTvBackground

@Composable
fun SettingsScreen(
    initialConfig: NvrConfig,
    connectionTestState: ConnectionTestState,
    discoveredDevices: List<DiscoveredDahuaDevice>,
    isScanningLan: Boolean,
    onScanLan: () -> Unit,
    onTestConnection: (ConnectionMethod, String, String, String, String, Int, Int) -> Unit,
    onSaveConfig: (NvrConfig) -> Unit,
    onResetConfig: () -> Unit,
    onOpenUpdateDialog: () -> Unit = {},
    onCancel: () -> Unit
) {
    // Current wizard step: 1: Conexión -> 2: Credenciales -> 3: Canales -> 4: Resumen y Prueba
    var currentStep by remember { mutableIntStateOf(1) }

    // Form fields
    var name by remember(initialConfig) { mutableStateOf(initialConfig.name) }
    var connectionMethod by remember(initialConfig) { mutableStateOf(initialConfig.connectionMethod) }
    var ipAddress by remember(initialConfig) { mutableStateOf(initialConfig.ipAddress) }
    var serialNumber by remember(initialConfig) { mutableStateOf(initialConfig.serialNumber) }
    var tcpPortText by remember(initialConfig) { mutableStateOf(initialConfig.tcpPort.toString()) }
    var rtspPortText by remember(initialConfig) { mutableStateOf(initialConfig.rtspPort.toString()) }
    var username by remember(initialConfig) { mutableStateOf(initialConfig.username) }
    var password by remember(initialConfig) { mutableStateOf(initialConfig.password) }
    var showPassword by remember { mutableStateOf(false) }

    var adminPin by remember(initialConfig) { mutableStateOf(initialConfig.adminPin) }
    var confirmPin by remember(initialConfig) { mutableStateOf(initialConfig.adminPin) }
    var enablePinProtection by remember(initialConfig) { mutableStateOf(initialConfig.hasAdminPin) }
    var showAdminPin by remember { mutableStateOf(false) }

    var totalChannels by remember(initialConfig) { mutableIntStateOf(initialConfig.totalChannels) }
    var defaultLayout by remember(initialConfig) { mutableStateOf(initialConfig.defaultGridLayout) }
    var useTcpTransport by remember(initialConfig) { mutableStateOf(initialConfig.useTcpTransport) }
    var autoSortBySignal by remember(initialConfig) { mutableStateOf(initialConfig.autoSortBySignal) }
    var channelOrderList by remember(initialConfig) {
        val initialOrder = initialConfig.customChannelOrder.ifEmpty { initialConfig.getActiveChannels() }
        val valid = initialOrder.filter { it in 1..initialConfig.totalChannels }
        val missing = (1..initialConfig.totalChannels).filter { it !in valid }
        mutableStateOf(valid + missing)
    }
    var selectedSwapPosition by remember { mutableStateOf<Int?>(null) }
    var showAdvancedChannelNames by remember { mutableStateOf(false) }

    LaunchedEffect(totalChannels) {
        val valid = channelOrderList.filter { it in 1..totalChannels }
        val missing = (1..totalChannels).filter { it !in valid }
        channelOrderList = valid + missing
        selectedSwapPosition = null
    }

    val channelNamesMap = remember(initialConfig) {
        mutableStateMapOf<Int, String>().apply {
            putAll(initialConfig.channelNames)
        }
    }

    var stepError by remember { mutableStateOf<String?>(null) }

    // Auto-update if discovery provides channels or resolved IP
    LaunchedEffect(connectionTestState) {
        if (connectionTestState is ConnectionTestState.Success) {
            if (!connectionTestState.resolvedIp.isNullOrBlank() && ipAddress.isBlank()) {
                ipAddress = connectionTestState.resolvedIp
            }
            if (connectionTestState.discoveredChannels != null) {
                totalChannels = connectionTestState.discoveredChannels
            }
        }
    }

    BackHandler {
        if (currentStep > 1) {
            stepError = null
            currentStep -= 1
        } else if (initialConfig.isConfigured) {
            onCancel()
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color(0xFFE2E8F0),
        focusedBorderColor = Color(0xFF00E5FF),
        unfocusedBorderColor = Color(0xFF334155),
        focusedLabelColor = Color(0xFF00E5FF),
        unfocusedLabelColor = Color(0xFF94A3B8),
        cursorColor = Color(0xFF00E5FF),
        focusedContainerColor = Color(0xFF10192A),
        unfocusedContainerColor = Color(0xFF0A101C)
    )

    val stepTitles = listOf("1. Conexión", "2. Credenciales", "3. Cámaras", "4. Resumen", "5. PIN de Admin")

    VisionTvBackground {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val availableWidth = maxWidth
            val isWideScreen = availableWidth >= 900.dp

            // Responsive Card Container
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(if (isWideScreen) 0.85f else 0.96f)
                    .fillMaxHeight(0.96f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xD90B1B2B))
                    .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(16.dp))
                    .padding(horizontal = if (isWideScreen) 28.dp else 16.dp, vertical = 20.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher),
                            contentDescription = "VisionTV",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "VisionTV · Configuración",
                                color = Color.White,
                                fontSize = if (isWideScreen) 18.sp else 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Visualizador de Cámaras Dahua / RTSP",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    TvIconButton(
                        icon = Icons.Default.SystemUpdateAlt,
                        contentDescription = "Buscar actualización en GitHub",
                        onClick = onOpenUpdateDialog,
                        modifier = Modifier.size(38.dp)
                    )

                    TvButton(
                        label = "Limpiar Todo",
                        icon = Icons.Default.DeleteSweep,
                        onClick = {
                            name = "Mi NVR Dahua"
                            ipAddress = ""
                            serialNumber = ""
                            username = ""
                            password = ""
                            adminPin = ""
                            confirmPin = ""
                            enablePinProtection = false
                            totalChannels = 8
                            defaultLayout = GridLayoutMode.GRID_4
                            autoSortBySignal = true
                            channelOrderList = (1..8).toList()
                            selectedSwapPosition = null
                            channelNamesMap.clear()
                            stepError = null
                            currentStep = 1
                            onResetConfig()
                        }
                    )

                    if (initialConfig.isConfigured) {
                        TvButton(
                            label = "Cerrar",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            onClick = onCancel
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step Progress Bar
            TvStepProgress(
                currentStep = currentStep,
                totalSteps = 5,
                stepTitles = stepTitles
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E2D44))
            Spacer(modifier = Modifier.height(12.dp))

            // Error Banner if validation fails
            if (stepError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF450A0A))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stepError!!,
                            color = Color(0xFFFECACA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Scrollable Content Area for the Active Step
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (currentStep) {
                    // ==========================================
                    // PASO 1: CONEXIÓN DE RED (IP / DOMINIO vs SN)
                    // ==========================================
                    1 -> {
                        Text(
                            text = "Paso 1: ¿Cómo deseas conectar tu grabador NVR?",
                            color = Color(0xFF38BDF8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Recomendamos IP Local si tu Android TV y tu NVR están en el mismo router o red WiFi/Ethernet.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )

                        // Mode Selector Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TvButton(
                                label = "Conexión por IP Local / Dominio",
                                isSelected = connectionMethod == ConnectionMethod.IP_DOMAIN,
                                isPrimary = connectionMethod == ConnectionMethod.IP_DOMAIN,
                                onClick = {
                                    connectionMethod = ConnectionMethod.IP_DOMAIN
                                    stepError = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                            TvButton(
                                label = "Conexión por Número de Serie (SN)",
                                isSelected = connectionMethod == ConnectionMethod.SN_P2P,
                                isPrimary = connectionMethod == ConnectionMethod.SN_P2P,
                                onClick = {
                                    connectionMethod = ConnectionMethod.SN_P2P
                                    stepError = null
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Quick Autocomplete Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F2438))
                                .border(1.dp, Color(0xFF00A3FF).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Relleno Rápido de tu NVR Dahua:",
                                        color = Color(0xFF00A3FF),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "IP: 192.168.68.142 · SN: AD05962PAZC3EE8 · Usuario: vigilancia",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp
                                    )
                                }
                                TvButton(
                                    label = "Autocompletar en 1 Clic",
                                    isPrimary = true,
                                    onClick = {
                                        ipAddress = "192.168.68.142"
                                        serialNumber = "AD05962PAZC3EE8"
                                        username = "vigilancia"
                                        password = "t3rM1n@T0r1986"
                                        totalChannels = 8
                                        defaultLayout = GridLayoutMode.GRID_4
                                        stepError = null
                                    }
                                )
                            }
                        }

                        if (connectionMethod == ConnectionMethod.IP_DOMAIN) {
                            // IP Address Input
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = {
                                    ipAddress = it.trim()
                                    stepError = null
                                },
                                label = { Text("* Dirección IP o Dominio del NVR (ej. 192.168.1.108 o 192.168.68.142)") },
                                placeholder = { Text("Ingresa la IP local de tu grabador", color = Color(0xFF64748B)) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Quick IP Prefix helpers for TV Remote
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Atajos de IP:",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                                listOf("192.168.68.", "192.168.1.", "192.168.0.", "10.0.0.").forEach { prefix ->
                                    TvPill(
                                        label = prefix,
                                        isSelected = ipAddress.startsWith(prefix),
                                        onClick = { ipAddress = prefix }
                                    )
                                }
                            }

                            // Ports (RTSP 554 and TCP 37777)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = rtspPortText,
                                    onValueChange = { rtspPortText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Puerto RTSP de Video (554)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = textFieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = tcpPortText,
                                    onValueChange = { tcpPortText = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Puerto TCP Dahua (37777)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = textFieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            // Serial Number Input
                            OutlinedTextField(
                                value = serialNumber,
                                onValueChange = {
                                    serialNumber = it.trim().uppercase()
                                    stepError = null
                                },
                                label = { Text("* Número de Serie del NVR (SN Dahua / Easy4ip)") },
                                placeholder = { Text("Ej: 3K01234PAZC5678", color = Color(0xFF64748B)) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it.trim() },
                                label = { Text("IP de respaldo en red local (Opcional si conoces la IP)") },
                                placeholder = { Text("Ej: 192.168.1.108", color = Color(0xFF64748B)) },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Auto-Scan LAN helper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "¿No sabes la IP exacta? Búscalo en tu red WiFi/Ethernet:",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp
                            )
                            TvButton(
                                label = if (isScanningLan) "Buscando en red..." else "Buscar NVR en Red Local",
                                icon = Icons.Default.Radar,
                                onClick = onScanLan,
                                enabled = !isScanningLan
                            )
                        }

                        if (isScanningLan) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Escaneando dispositivos Dahua en tu red local...",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        if (discoveredDevices.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF08101E))
                                    .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Grabador(es) Dahua encontrados en tu red:",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                discoveredDevices.forEach { dev ->
                                    val isSelected = ipAddress == dev.ipAddress || (dev.serialNo.isNotBlank() && serialNumber == dev.serialNo)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Color(0xFF0C243B) else Color(0xFF0F1829))
                                            .border(
                                                1.dp,
                                                if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E2D44),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "${dev.deviceClass} · ${dev.deviceType} (${dev.videoChannels}CH)",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "IP: ${dev.ipAddress}   |   SN: ${dev.serialNo.ifBlank { "Detectado" }}",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 12.sp
                                            )
                                        }

                                        TvButton(
                                            label = if (isSelected) "Seleccionado" else "Usar este Grabador",
                                            isSelected = isSelected,
                                            onClick = {
                                                ipAddress = dev.ipAddress
                                                if (dev.serialNo.isNotBlank()) {
                                                    serialNumber = dev.serialNo
                                                }
                                                tcpPortText = dev.tcpPort.toString()
                                                totalChannels = dev.videoChannels
                                                stepError = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // PASO 2: CREDENCIALES DE ACCESO
                    // ==========================================
                    2 -> {
                        Text(
                            text = "Paso 2: Cuenta y Contraseña de tu Grabador Dahua",
                            color = Color(0xFF38BDF8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ingresa el mismo usuario y contraseña que utilizas para entrar a SmartPSS Lite o al menú de tu NVR.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it.trim()
                                stepError = null
                            },
                            label = { Text("* Nombre de Usuario (ej. admin o vigilancia)") },
                            placeholder = { Text("Ej: admin", color = Color(0xFF64748B)) },
                            singleLine = true,
                            colors = textFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = {
                                    password = it
                                    stepError = null
                                },
                                label = { Text("* Contraseña del Grabador") },
                                placeholder = { Text("Tu contraseña de seguridad", color = Color(0xFF64748B)) },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                colors = textFieldColors,
                                modifier = Modifier.weight(1f)
                            )

                            TvButton(
                                label = if (showPassword) "Ocultar" else "Ver",
                                icon = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                onClick = { showPassword = !showPassword }
                            )
                        }

                        // Help tip box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F1E33))
                                .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Consejo de Seguridad Dahua:",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Si creaste un usuario secundario en tu grabador (por ejemplo 'vigilancia'), asegúrate de que tenga permisos de 'Vista en Vivo' (Live View) asignados en la configuración del NVR.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // ==========================================
                    // PASO 3: CÁMARAS Y PANTALLA
                    // ==========================================
                    3 -> {
                        Text(
                            text = "Paso 3: Cámaras y Distribución en tu Televisor",
                            color = Color(0xFF38BDF8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Selecciona cuántas cámaras tiene tu grabador y cómo deseas verlas en tu TV.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )

                        // Card 1: Canales, Cuadrícula y Protocolo
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F1829))
                                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                // Channel Count
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Número de Canales de tu NVR:",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(4, 8, 12, 16, 32).forEach { count ->
                                            TvPill(
                                                label = "$count CH",
                                                isSelected = totalChannels == count,
                                                onClick = { totalChannels = count },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E2D44))

                                // Initial Grid Layout
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Cuadrícula inicial al abrir la aplicación:",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        GridLayoutMode.entries.forEach { mode ->
                                            TvPill(
                                                label = mode.label,
                                                isSelected = defaultLayout == mode,
                                                onClick = { defaultLayout = mode },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E2D44))

                                // Transport Protocol
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Protocolo de Video Streaming:",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        TvButton(
                                            label = "TCP (Más estable, sin cortes de imagen - Recomendado)",
                                            isSelected = useTcpTransport,
                                            isPrimary = useTcpTransport,
                                            onClick = { useTcpTransport = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                        TvButton(
                                            label = "UDP (Baja latencia)",
                                            isSelected = !useTcpTransport,
                                            onClick = { useTcpTransport = false },
                                            modifier = Modifier.weight(0.5f)
                                        )
                                    }
                                }
                            }
                        }

                        // Card 2: Orden y Priorización de Canales en Cuadrícula
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F1829))
                                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                // Row 1: Auto Sort by Signal toggle
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
                                                .background(if (autoSortBySignal) Color(0x3300A3FF) else Color(0x2294A3B8))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.NetworkCheck,
                                                contentDescription = "Señal",
                                                tint = if (autoSortBySignal) Color(0xFF00A3FF) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Priorizar Cámaras con Señal de Video",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (autoSortBySignal)
                                                    "Mueve automáticamente a la primera página las cámaras que tienen señal"
                                                else
                                                    "Respeta estrictamente el orden manual elegido abajo sin importar la señal",
                                                color = if (autoSortBySignal) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TvPill(
                                            label = "Activado",
                                            isSelected = autoSortBySignal,
                                            onClick = { autoSortBySignal = true }
                                        )
                                        TvPill(
                                            label = "Desactivado",
                                            isSelected = !autoSortBySignal,
                                            onClick = { autoSortBySignal = false }
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E2D44))

                                // Row 2: Manual Channel Order Header & Reset Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Orden Manual de Cámaras en Pantalla",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Define qué cámaras quieres ver primero en la cuadrícula (ej. Cámara 6, 8, 1, 2)",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }

                                    TvButton(
                                        label = "Restablecer (1..$totalChannels)",
                                        onClick = {
                                            channelOrderList = (1..totalChannels).toList()
                                            selectedSwapPosition = null
                                        }
                                    )
                                }

                                // Quick Channel Swap Bar if a position was tapped
                                if (selectedSwapPosition != null) {
                                    val targetPos = selectedSwapPosition!!
                                    val currentChAtPos = channelOrderList.getOrNull(targetPos) ?: 1
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0B1B2B))
                                            .border(1.dp, Color(0xFF00A3FF), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Elige qué cámara colocar en la Posición #${targetPos + 1}:",
                                                color = Color(0xFF38BDF8),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            TvPill(
                                                label = "✕ Cerrar",
                                                isSelected = false,
                                                onClick = { selectedSwapPosition = null }
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            (1..totalChannels).forEach { ch ->
                                                val isCurrent = currentChAtPos == ch
                                                TvPill(
                                                    label = "CH $ch",
                                                    isSelected = isCurrent,
                                                    onClick = {
                                                        val mutable = channelOrderList.toMutableList()
                                                        val existingIndex = mutable.indexOf(ch)
                                                        if (existingIndex != -1 && existingIndex != targetPos) {
                                                            val temp = mutable[targetPos]
                                                            mutable[targetPos] = ch
                                                            mutable[existingIndex] = temp
                                                        } else if (existingIndex == -1) {
                                                            mutable[targetPos] = ch
                                                        }
                                                        channelOrderList = mutable
                                                        selectedSwapPosition = null
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Ordered Camera Positions Grid (2 columns)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val items = channelOrderList.take(totalChannels)
                                    val half = (items.size + 1) / 2
                                    for (row in 0 until half) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val idx1 = row * 2
                                            val idx2 = row * 2 + 1

                                            // Tile 1
                                            if (idx1 < items.size) {
                                                val ch = items[idx1]
                                                val customName = channelNamesMap[ch]?.trim()
                                                val labelText = if (!customName.isNullOrEmpty()) "CH $ch · $customName" else "CH $ch (Cámara $ch)"

                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (selectedSwapPosition == idx1) Color(0xFF1E3A5F) else Color(0xFF07111D))
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (selectedSwapPosition == idx1) Color(0xFF00E5FF) else Color(0xFF1E2D44),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                selectedSwapPosition = if (selectedSwapPosition == idx1) null else idx1
                                                            }
                                                    ) {
                                                        Text(
                                                            text = "#${idx1 + 1}",
                                                            color = Color(0xFF00A3FF),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0x3300A3FF))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = labelText,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1
                                                        )
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        TvIconButton(
                                                            icon = Icons.Default.ChevronLeft,
                                                            contentDescription = "Mover antes",
                                                            onClick = {
                                                                if (idx1 > 0) {
                                                                    val mutable = channelOrderList.toMutableList()
                                                                    val temp = mutable[idx1]
                                                                    mutable[idx1] = mutable[idx1 - 1]
                                                                    mutable[idx1 - 1] = temp
                                                                    channelOrderList = mutable
                                                                }
                                                            },
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                        TvIconButton(
                                                            icon = Icons.Default.ChevronRight,
                                                            contentDescription = "Mover después",
                                                            onClick = {
                                                                if (idx1 < items.size - 1) {
                                                                    val mutable = channelOrderList.toMutableList()
                                                                    val temp = mutable[idx1]
                                                                    mutable[idx1] = mutable[idx1 + 1]
                                                                    mutable[idx1 + 1] = temp
                                                                    channelOrderList = mutable
                                                                }
                                                            },
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Tile 2
                                            if (idx2 < items.size) {
                                                val ch = items[idx2]
                                                val customName = channelNamesMap[ch]?.trim()
                                                val labelText = if (!customName.isNullOrEmpty()) "CH $ch · $customName" else "CH $ch (Cámara $ch)"

                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (selectedSwapPosition == idx2) Color(0xFF1E3A5F) else Color(0xFF07111D))
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (selectedSwapPosition == idx2) Color(0xFF00E5FF) else Color(0xFF1E2D44),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable {
                                                                selectedSwapPosition = if (selectedSwapPosition == idx2) null else idx2
                                                            }
                                                    ) {
                                                        Text(
                                                            text = "#${idx2 + 1}",
                                                            color = Color(0xFF00A3FF),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0x3300A3FF))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = labelText,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1
                                                        )
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        TvIconButton(
                                                            icon = Icons.Default.ChevronLeft,
                                                            contentDescription = "Mover antes",
                                                            onClick = {
                                                                if (idx2 > 0) {
                                                                    val mutable = channelOrderList.toMutableList()
                                                                    val temp = mutable[idx2]
                                                                    mutable[idx2] = mutable[idx2 - 1]
                                                                    mutable[idx2 - 1] = temp
                                                                    channelOrderList = mutable
                                                                }
                                                            },
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                        TvIconButton(
                                                            icon = Icons.Default.ChevronRight,
                                                            contentDescription = "Mover después",
                                                            onClick = {
                                                                if (idx2 < items.size - 1) {
                                                                    val mutable = channelOrderList.toMutableList()
                                                                    val temp = mutable[idx2]
                                                                    mutable[idx2] = mutable[idx2 + 1]
                                                                    mutable[idx2 + 1] = temp
                                                                    channelOrderList = mutable
                                                                }
                                                            },
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Card 3: Custom Camera Names (Organized in 2 columns to fill space visually)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F1829))
                                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Nombres de Cámaras (Opcional)",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Etiqueta tus cámaras para identificarlas fácilmente (ej. Entrada, Patio, Calle)",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }
                                    TvButton(
                                        label = if (showAdvancedChannelNames) "Ocultar" else "Personalizar (${totalChannels} Cámaras)",
                                        icon = if (showAdvancedChannelNames) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        onClick = { showAdvancedChannelNames = !showAdvancedChannelNames }
                                    )
                                }

                                AnimatedVisibility(
                                    visible = showAdvancedChannelNames,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val rows = (totalChannels + 1) / 2
                                        for (rowIndex in 0 until rows) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                val ch1 = rowIndex * 2 + 1
                                                val ch2 = rowIndex * 2 + 2

                                                OutlinedTextField(
                                                    value = channelNamesMap[ch1] ?: "",
                                                    onValueChange = { newValue ->
                                                        if (newValue.isBlank()) channelNamesMap.remove(ch1)
                                                        else channelNamesMap[ch1] = newValue
                                                    },
                                                    label = { Text("Canal $ch1 (Cámara $ch1)") },
                                                    placeholder = { Text("Ej. Entrada principal", color = Color(0xFF64748B)) },
                                                    singleLine = true,
                                                    colors = textFieldColors,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                if (ch2 <= totalChannels) {
                                                    OutlinedTextField(
                                                        value = channelNamesMap[ch2] ?: "",
                                                        onValueChange = { newValue ->
                                                            if (newValue.isBlank()) channelNamesMap.remove(ch2)
                                                            else channelNamesMap[ch2] = newValue
                                                        },
                                                        label = { Text("Canal $ch2 (Cámara $ch2)") },
                                                        placeholder = { Text("Ej. Patio o Cochera", color = Color(0xFF64748B)) },
                                                        singleLine = true,
                                                        colors = textFieldColors,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // PASO 4: PRUEBA Y RESUMEN
                    // ==========================================
                    4 -> {
                        Text(
                            text = "Paso 4: Resumen y Prueba de Conexión",
                            color = Color(0xFF38BDF8),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Comprueba que los datos sean correctos. Puedes probar la conexión antes de continuar al último paso.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )

                        // Configuration Summary Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F1829))
                                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Resumen de Conexión",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(color = Color(0xFF1E2D44))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Método:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(
                                    text = if (connectionMethod == ConnectionMethod.IP_DOMAIN) "IP Local / Dominio" else "Número de Serie (SN / P2P)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (connectionMethod == ConnectionMethod.IP_DOMAIN) "Dirección IP:" else "Número de Serie (SN):",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = if (connectionMethod == ConnectionMethod.IP_DOMAIN) ipAddress else serialNumber,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Usuario:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(text = username, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Canales y Vista:", color = Color(0xFF94A3B8), fontSize = 13.sp)
                                Text(
                                    text = "$totalChannels Cámaras · Cuadrícula ${defaultLayout.label}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Test Connection Button & Status
                        TvButton(
                            label = "Probar Conexión con el Grabador",
                            icon = Icons.Default.NetworkCheck,
                            onClick = {
                                val tcpPort = tcpPortText.toIntOrNull() ?: 37777
                                val rtspPort = rtspPortText.toIntOrNull() ?: 554
                                onTestConnection(connectionMethod, ipAddress, serialNumber, username, password, tcpPort, rtspPort)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        when (connectionTestState) {
                            is ConnectionTestState.Testing -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .border(1.dp, Color(0xFF00B4D8), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00B4D8),
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Verificando respuesta del grabador Dahua (Puertos 554 RTSP y 37777 TCP)...",
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            is ConnectionTestState.Success -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF052E16))
                                        .border(1.dp, Color(0xFF22C55E), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = connectionTestState.message,
                                        color = Color(0xFFBBF7D0),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            is ConnectionTestState.Error -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF450A0A))
                                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = connectionTestState.message,
                                        color = Color(0xFFFECACA),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            ConnectionTestState.Idle -> {}
                        }
                    }

                    // ==========================================
                    // PASO 5: PIN DE ADMINISTRADOR (ÚLTIMO PASO)
                    // ==========================================
                    5 -> {
                        Text(
                            text = "Paso 5: Seguridad y PIN de Administrador (Último Paso)",
                            color = Color(0xFF00A3FF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Protege el acceso a la configuración para evitar que cualquier persona modifique o vea los parámetros de tu NVR.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )

                        // Card: Enable / Disable PIN Protection
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F1829))
                                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (enablePinProtection) Color(0x3300A3FF) else Color(0x2294A3B8))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "PIN",
                                                tint = if (enablePinProtection) Color(0xFF00A3FF) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Bloqueo de Configuración con PIN",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (enablePinProtection) "PIN obligatorio para entrar a los ajustes" else "Sin PIN (Cualquiera puede entrar a los ajustes)",
                                                color = if (enablePinProtection) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TvPill(
                                            label = "Activado",
                                            isSelected = enablePinProtection,
                                            onClick = {
                                                enablePinProtection = true
                                                stepError = null
                                            }
                                        )
                                        TvPill(
                                            label = "Desactivado",
                                            isSelected = !enablePinProtection,
                                            onClick = {
                                                enablePinProtection = false
                                                stepError = null
                                            }
                                        )
                                    }
                                }

                                if (enablePinProtection) {
                                    HorizontalDivider(color = Color(0xFF1E2D44))

                                    // Single Clean PIN Input with Visibility Toggle
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = adminPin,
                                            onValueChange = { input ->
                                                val filtered = input.filter { it.isDigit() }.take(8)
                                                adminPin = filtered
                                                stepError = null
                                            },
                                            label = { Text("* PIN de Administrador (4 a 8 dígitos)") },
                                            placeholder = { Text("Ej. 1234", color = Color(0xFF64748B)) },
                                            singleLine = true,
                                            visualTransformation = if (showAdminPin) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                            colors = textFieldColors,
                                            trailingIcon = {
                                                IconButton(onClick = { showAdminPin = !showAdminPin }) {
                                                    Icon(
                                                        imageVector = if (showAdminPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = "Mostrar PIN",
                                                        tint = Color(0xFF94A3B8)
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Animated Indicator Dots
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF07111D))
                                                .border(1.dp, Color(0xFF1E2D44), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                        ) {
                                            for (i in 0 until 4) {
                                                val isFilled = i < adminPin.length
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isFilled) Color(0xFF00A3FF) else Color(0xFF1E2D44))
                                                        .border(
                                                            1.dp,
                                                            if (isFilled) Color(0xFF38BDF8) else Color(0xFF334155),
                                                            CircleShape
                                                        )
                                                )
                                            }
                                        }
                                    }

                                    // Quick TV remote numeric keypad helpers (0-9, Del)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "Ingresar PIN con control remoto:",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").forEach { num ->
                                                TvPill(
                                                    label = num,
                                                    isSelected = false,
                                                    onClick = {
                                                        if (adminPin.length < 8) {
                                                            adminPin += num
                                                            stepError = null
                                                        }
                                                    }
                                                )
                                            }
                                            TvPill(
                                                label = "⌫ Borrar",
                                                isSelected = false,
                                                onClick = {
                                                    if (adminPin.isNotEmpty()) {
                                                        adminPin = adminPin.dropLast(1)
                                                        stepError = null
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    Text(
                                        text = "PIN maestro de seguridad: 8888 (para recuperar el acceso si olvidas tu clave).",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF1E2D44))
            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Navigation Buttons (Atrás / Siguiente / Guardar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Button (Anterior)
                if (currentStep > 1) {
                    TvButton(
                        label = "⬅ Anterior",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = {
                            stepError = null
                            currentStep -= 1
                        }
                    )
                } else {
                    Spacer(modifier = Modifier.width(10.dp))
                }

                // Right Button (Siguiente o Guardar)
                if (currentStep < 5) {
                    TvButton(
                        label = "Siguiente",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        isPrimary = true,
                        onClick = {
                            stepError = null
                            when (currentStep) {
                                1 -> {
                                    val cleanIp = ipAddress.trim()
                                    val cleanSn = serialNumber.trim()
                                    if (connectionMethod == ConnectionMethod.IP_DOMAIN && cleanIp.isEmpty()) {
                                        stepError = "Por favor ingresa la dirección IP de tu NVR (ej. 192.168.1.108 o 192.168.68.142)."
                                        return@TvButton
                                    }
                                    if (connectionMethod == ConnectionMethod.SN_P2P && cleanSn.isEmpty()) {
                                        stepError = "Por favor ingresa el Número de Serie (SN) de tu NVR Dahua."
                                        return@TvButton
                                    }
                                    currentStep = 2
                                }
                                2 -> {
                                    val cleanUser = username.trim()
                                    if (cleanUser.isEmpty()) {
                                        stepError = "El nombre de usuario es obligatorio (ej. admin o vigilancia)."
                                        return@TvButton
                                    }
                                    if (password.isEmpty()) {
                                        stepError = "Por favor ingresa la contraseña de tu grabador Dahua."
                                        return@TvButton
                                    }
                                    currentStep = 3
                                }
                                3 -> {
                                    currentStep = 4
                                }
                                4 -> {
                                    currentStep = 5
                                }
                            }
                        }
                    )
                } else {
                    // Final Step (Paso 5): Big Primary Save Button
                    TvButton(
                        label = "Guardar y Ver Cámaras en Vivo",
                        icon = Icons.Default.PlayArrow,
                        isPrimary = true,
                        isSelected = true,
                        onClick = {
                            if (enablePinProtection) {
                                if (adminPin.length < 4) {
                                    stepError = "El PIN de administrador debe tener al menos 4 dígitos (ej. 1234)."
                                    return@TvButton
                                }
                            }

                            val cleanSn = serialNumber.trim()
                            val cleanIp = ipAddress.trim()
                            val tcpPort = tcpPortText.toIntOrNull() ?: 37777
                            val rtspPort = rtspPortText.toIntOrNull() ?: 554

                            val finalConfig = NvrConfig(
                                name = name.trim().ifEmpty { "VisionTV Dahua" },
                                connectionMethod = connectionMethod,
                                ipAddress = cleanIp,
                                serialNumber = cleanSn,
                                tcpPort = tcpPort,
                                rtspPort = rtspPort,
                                groupName = "General",
                                username = username.trim(),
                                password = password,
                                totalChannels = totalChannels,
                                defaultGridLayout = defaultLayout,
                                useTcpTransport = useTcpTransport,
                                channelNames = channelNamesMap.toMap(),
                                customChannelOrder = channelOrderList,
                                autoSortBySignal = autoSortBySignal,
                                adminPin = if (enablePinProtection) adminPin.trim() else ""
                            )
                            onSaveConfig(finalConfig)
                        }
                    )
                }
            }
        }
    }
}
}

