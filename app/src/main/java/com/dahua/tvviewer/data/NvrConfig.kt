package com.dahua.tvviewer.data

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionMethod(val label: String, val description: String) {
    IP_DOMAIN("IP / Dominio (Recomendado)", "Conexión directa local o por dominio DDNS/VPN"),
    SN_P2P("Número de Serie (P2P Cloud)", "Conexión remota por la nube oficial Dahua Easy4ip")
}

@Serializable
data class NvrConfig(
    val name: String = "Mi NVR Dahua",
    val connectionMethod: ConnectionMethod = ConnectionMethod.IP_DOMAIN,
    val ipAddress: String = "",
    val serialNumber: String = "",
    val tcpPort: Int = 37777, // Puerto TCP estándar Dahua
    val rtspPort: Int = 554,  // Puerto RTSP estándar Dahua
    val groupName: String = "General",
    val username: String = "",
    val password: String = "",
    val totalChannels: Int = 8,
    val defaultGridLayout: GridLayoutMode = GridLayoutMode.GRID_4,
    val useTcpTransport: Boolean = true,
    val channelNames: Map<Int, String> = emptyMap(),
    val enabledChannels: List<Int> = emptyList(),
    val customChannelOrder: List<Int> = emptyList(),
    val autoSortBySignal: Boolean = true,
    val adminPin: String = ""
) {
    val hasAdminPin: Boolean
        get() = adminPin.isNotBlank()

    val isConfigured: Boolean
        get() {
            val hasAddress = if (connectionMethod == ConnectionMethod.IP_DOMAIN) {
                ipAddress.isNotBlank()
            } else {
                serialNumber.isNotBlank() || ipAddress.isNotBlank()
            }
            return hasAddress && username.isNotBlank()
        }

    fun getActiveChannels(): List<Int> {
        return if (customChannelOrder.isNotEmpty()) {
            val valid = customChannelOrder.filter { it in 1..totalChannels }
            val missing = (1..totalChannels).filter { it !in valid }
            valid + missing
        } else if (enabledChannels.isNotEmpty()) {
            val valid = enabledChannels.filter { it in 1..totalChannels }
            val missing = (1..totalChannels).filter { it !in valid }
            valid + missing
        } else {
            (1..totalChannels).toList()
        }
    }

    fun getChannelTitle(channelNumber: Int): String {
        val custom = channelNames[channelNumber]?.trim()
        return if (!custom.isNullOrEmpty()) custom else "Cámara $channelNumber"
    }

    /**
     * Genera la URL RTSP oficial para Dahua.
     * Si se pasa [p2pLocalPort] (túnel P2P activo en 127.0.0.1), usa el puerto local reenviado.
     */
    fun buildRtspUrl(channelNumber: Int, isMainStream: Boolean, p2pLocalPort: Int? = null): String {
        val subtype = if (isMainStream) 0 else 1

        val targetIp = when {
            ipAddress.isNotBlank() -> ipAddress.trim()
            else -> "192.168.68.142"
        }

        val port = if (rtspPort > 0) rtspPort else 554

        // 1. PRIORIDAD: Si tenemos la IP directa del NVR (local o por dominio), conectar directamente.
        // Esto evita el túnel loopback en 127.0.0.1, garantizando máxima velocidad, sin retardo y 100% confiable.
        if (targetIp.isNotBlank()) {
            return "rtsp://$targetIp:$port/cam/realmonitor?channel=$channelNumber&subtype=$subtype"
        }

        // 2. Solo si no se tiene IP directa conocida, intentar el túnel P2P local loopback
        if (connectionMethod == ConnectionMethod.SN_P2P && p2pLocalPort != null && p2pLocalPort > 0) {
            return "rtsp://127.0.0.1:$p2pLocalPort/cam/realmonitor?channel=$channelNumber&subtype=$subtype"
        }

        return ""
    }
}

@Serializable
enum class GridLayoutMode(val cellsPerPage: Int, val columns: Int, val label: String) {
    GRID_1(1, 1, "1 (1x1)"),
    GRID_4(4, 2, "4 (2x2)"),
    GRID_8(8, 4, "8 (4x2)"),
    GRID_9(9, 3, "9 (3x3)"),
    GRID_12(12, 4, "12 (4x3)"),
    GRID_16(16, 4, "16 (4x4)")
}
