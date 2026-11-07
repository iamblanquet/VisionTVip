package com.dahua.tvviewer.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

data class DiscoveredDahuaDevice(
    val ipAddress: String,
    val serialNo: String,
    val deviceType: String,
    val deviceClass: String,
    val machineName: String,
    val tcpPort: Int,
    val httpPort: Int,
    val videoChannels: Int,
    val macAddress: String
)

object DahuaLanDiscovery {

    private const val DISCOVERY_PORT = 37810
    private const val MULTICAST_ADDR = "239.255.255.251"
    private const val BROADCAST_ADDR = "255.255.255.255"

    private fun buildDhDiscoverPacket(): ByteArray {
        val jsonPayload = """{"method":"DHDiscover.search","params":{"mac":"","uni":1}}""" + "\n"
        val payloadBytes = jsonPayload.toByteArray(Charsets.UTF_8)
        val payloadLen = payloadBytes.size

        val buffer = ByteBuffer.allocate(32 + payloadLen).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x20)          // headFlag = 32 bytes
        buffer.putInt(0x50494844)    // magic = "DHIP" in little-endian
        buffer.putInt(0)             // sessionID = 0
        buffer.putInt(0x49)          // requestID = 0x49
        buffer.putInt(payloadLen)    // packageLength
        buffer.putInt(0)             // packageIndex
        buffer.putInt(payloadLen)    // messageLength
        buffer.putInt(0)             // dataLength
        buffer.put(payloadBytes)
        return buffer.array()
    }

    private fun getLocalIpv4Prefix(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            // Prioritize standard wlan / eth interfaces
            val sortedIntfs = interfaces.sortedByDescending { intf ->
                val name = intf.name.lowercase()
                when {
                    name.startsWith("wlan") || name.startsWith("eth") -> 2
                    name.contains("virtual") || name.contains("docker") || name.contains("dummy") || name.contains("tun") || name.contains("vethernet") -> 0
                    else -> 1
                }
            }

            for (intf in sortedIntfs) {
                if (!intf.isUp || intf.isLoopback) continue
                val name = intf.name.lowercase()
                if (name.contains("docker") || name.contains("vethernet") || name.contains("dummy")) continue

                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val parts = ip.split(".")
                        if (parts.size == 4) {
                            // Prefer 192.168.x.x and 10.x.x.x
                            if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                                return "${parts[0]}.${parts[1]}.${parts[2]}"
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return null
    }

    suspend fun discoverDevices(context: Context, timeoutMs: Int = 2600): List<DiscoveredDahuaDevice> =
        withContext(Dispatchers.IO) {
            val discoveredByIp = linkedMapOf<String, DiscoveredDahuaDevice>()
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = wifiManager?.createMulticastLock("DahuaDiscoveryLock")?.apply {
                setReferenceCounted(true)
                try {
                    acquire()
                } catch (_: Exception) {
                }
            }

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    soTimeout = 400
                    bind(InetSocketAddress(0))
                }

                val packetBytes = buildDhDiscoverPacket()
                val subnetPrefix = getLocalIpv4Prefix()
                val targets = mutableListOf(MULTICAST_ADDR, BROADCAST_ADDR)
                if (subnetPrefix != null) {
                    targets.add("$subnetPrefix.255")
                }

                repeat(2) {
                    for (target in targets) {
                        try {
                            val dest = InetAddress.getByName(target)
                            val dgram = DatagramPacket(packetBytes, packetBytes.size, dest, DISCOVERY_PORT)
                            socket.send(dgram)
                        } catch (_: Exception) {
                        }
                    }
                }

                val receiveBuf = ByteArray(8192)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        val respPacket = DatagramPacket(receiveBuf, receiveBuf.size)
                        socket.receive(respPacket)
                        val senderIp = respPacket.address?.hostAddress ?: continue
                        val parsed = parseDhDiscoverResponse(receiveBuf, respPacket.length, senderIp)
                        if (parsed != null && parsed.ipAddress.isNotBlank()) {
                            discoveredByIp[parsed.ipAddress] = parsed
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
                try {
                    if (multicastLock?.isHeld == true) {
                        multicastLock.release()
                    }
                } catch (_: Exception) {
                }
            }

            // Fallback: If UDP broadcast is filtered by the router/WiFi AP, do a fast parallel TCP scan on Dahua port 37777
            if (discoveredByIp.isEmpty()) {
                val prefix = getLocalIpv4Prefix()
                if (prefix != null) {
                    val tcpFound = scanSubnetForDahuaTcpPort(prefix)
                    for (dev in tcpFound) {
                        discoveredByIp[dev.ipAddress] = dev
                    }
                }
            }

            discoveredByIp.values.toList()
        }

    /**
     * Scans the local /24 subnet in parallel for Dahua's signature TCP Port 37777
     * when Wi-Fi routers block UDP multicast/broadcast packets.
     */
    private suspend fun scanSubnetForDahuaTcpPort(prefix: String): List<DiscoveredDahuaDevice> =
        coroutineScope {
            val found = mutableListOf<DiscoveredDahuaDevice>()
            // Prioritize common Dahua default IPs (.108, .250, .100, etc.) and scan 1..254 in chunks
            val chunks = (1..254).chunked(45)
            for (chunk in chunks) {
                val results = chunk.map { host ->
                    async(Dispatchers.IO) {
                        val ip = "$prefix.$host"
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(ip, 554), 220)
                            }
                            Socket().use { s ->
                                s.connect(InetSocketAddress(ip, 37777), 220)
                            }
                            DiscoveredDahuaDevice(
                                ipAddress = ip,
                                serialNo = "",
                                deviceType = "NVR / DVR Dahua",
                                deviceClass = "NVR",
                                machineName = "Dahua NVR ($ip)",
                                tcpPort = 37777,
                                httpPort = 80,
                                videoChannels = 8,
                                macAddress = ""
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
                found.addAll(results)
            }
            found
        }

    private fun parseDhDiscoverResponse(data: ByteArray, length: Int, fallbackIp: String): DiscoveredDahuaDevice? {
        if (length <= 32) return null
        return try {
            val rawString = String(data, 0, length, Charsets.UTF_8)
            val jsonStart = rawString.indexOf('{')
            val jsonEnd = rawString.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd <= jsonStart) return null

            val jsonStr = rawString.substring(jsonStart, jsonEnd + 1)
            val root = JSONObject(jsonStr)
            val mac = root.optString("mac", "")
            val params = root.optJSONObject("params") ?: return null
            val devInfo = params.optJSONObject("deviceInfo") ?: params

            val ipv4Obj = devInfo.optJSONObject("IPv4Address")
            val ipAddr = ipv4Obj?.optString("IPAddress")?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                ?: fallbackIp

            val serialNo = devInfo.optString("SerialNo", "").trim()
            val deviceType = devInfo.optString("DeviceType", "Dahua NVR").trim()
            val deviceClass = devInfo.optString("DeviceClass", "NVR").trim()
            // Ignore PC-NVR virtual service running on SmartPSS PCs so we don't confuse a PC with a real NVR
            if (deviceClass.equals("PC-NVR", ignoreCase = true) || deviceType.startsWith("PC-NVR", ignoreCase = true)) {
                return null
            }
            val machineName = devInfo.optString("MachineName", "").trim()
            val tcpPort = devInfo.optInt("Port", 37777).takeIf { it > 0 } ?: 37777
            val httpPort = devInfo.optInt("HttpPort", 80).takeIf { it > 0 } ?: 80

            val videoIn = devInfo.optInt("VideoInputChannels", 0)
            val remoteVideoIn = devInfo.optInt("RemoteVideoInputChannels", 0)
            val totalCh = (videoIn + remoteVideoIn).let { sum ->
                when {
                    sum <= 0 -> 8
                    sum <= 4 -> 4
                    sum <= 8 -> 8
                    sum <= 16 -> 16
                    else -> 32
                }
            }

            DiscoveredDahuaDevice(
                ipAddress = ipAddr,
                serialNo = serialNo,
                deviceType = deviceType,
                deviceClass = deviceClass,
                machineName = machineName.ifBlank { "$deviceClass $deviceType".trim() },
                tcpPort = tcpPort,
                httpPort = httpPort,
                videoChannels = totalCh,
                macAddress = mac
            )
        } catch (_: Exception) {
            null
        }
    }
}
