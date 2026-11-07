package com.dahua.tvviewer.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native Kotlin implementation of the Dahua P2P Cloud & PTCP Tunneling Protocol
 * (Easy4ip / SmartPSS Lite / DMSS P2P Cloud).
 *
 * Connects to any Dahua NVR/DVR/IPC worldwide using only its Serial Number (SN),
 * username, and password, and exposes a local TCP port (127.0.0.1:<localPort>)
 * mapped to the remote NVR's RTSP port (554).
 */
object DahuaP2pTunnelManager {

    private const val TAG = "DahuaP2P"

    private const val MAIN_SERVER = "www.easy4ipcloud.com"
    private const val MAIN_PORT = 8800
    private const val WSSE_USERNAME = "cba1b29e32cb17aa46b8ff9e73c7f40b"
    private const val WSSE_USERKEY = "996103384cdf19179e19243e959bbf8b"

    private val AES_IV = "2z52*lk9o6HRyJrf".toByteArray(Charsets.US_ASCII)
    private val DEVINFO_KEY = "kRjmsUB&ezmdGLL67H#\$ojw@XflcaIaf".toByteArray(Charsets.US_ASCII)
    private val DEVINFO_IV = "MydvJw*Iw1w&i^kk".toByteArray(Charsets.US_ASCII)

    private val random = SecureRandom()
    private val globalCseq = AtomicInteger(10)

    @Volatile
    private var serverTimeOffsetSec: Long = 0L

    @Volatile
    private var activeTunnel: P2pTunnelSession? = null
    private val tunnelLock = Any()

    data class P2pProbeResult(
        val success: Boolean,
        val localPort: Int = 0,
        val message: String,
        val remoteLanIp: String = "",
        val remoteWanIp: String = "",
        val rtspPort: Int = 554
    )

    /**
     * Ensures a live P2P tunnel is running for the given Serial Number, username, and password,
     * returning the local TCP port on 127.0.0.1 that forwards to the NVR's RTSP port.
     */
    suspend fun ensureTunnel(
        serialNumber: String,
        username: String,
        password: String
    ): P2pProbeResult = withContext(Dispatchers.IO) {
        val cleanSn = serialNumber.trim().uppercase(Locale.US)
        val cleanUser = username.trim()
        if (cleanSn.isEmpty()) {
            return@withContext P2pProbeResult(false, 0, "Número de Serie (SN) vacío")
        }

        synchronized(tunnelLock) {
            val current = activeTunnel
            if (current != null &&
                current.serialNumber == cleanSn &&
                current.username == cleanUser &&
                current.password == password &&
                current.isRunning()
            ) {
                return@withContext P2pProbeResult(
                    success = true,
                    localPort = current.localListenPort,
                    message = "Túnel P2P activo (${current.modeDescription})",
                    remoteLanIp = current.deviceLanAddr,
                    remoteWanIp = current.devicePubAddr,
                    rtspPort = current.remoteRtspPort
                )
            }
            current?.close()
            activeTunnel = null
        }

        val session = P2pTunnelSession(cleanSn, cleanUser, password)
        val result = session.establishAndStart()
        if (result.success) {
            synchronized(tunnelLock) {
                activeTunnel?.close()
                activeTunnel = session
            }
        } else {
            session.close()
        }
        result
    }

    fun getActiveLocalPort(serialNumber: String): Int? {
        val current = activeTunnel ?: return null
        return if (current.serialNumber.equals(serialNumber.trim(), ignoreCase = true) && current.isRunning()) {
            current.localListenPort
        } else {
            null
        }
    }

    fun stopTunnel() {
        synchronized(tunnelLock) {
            activeTunnel?.close()
            activeTunnel = null
        }
    }

    // =========================================================================
    // Cryptographic & Protocol Helpers
    // =========================================================================

    private fun nowAdjustedMillis(): Long {
        return System.currentTimeMillis() - (serverTimeOffsetSec * 1000L)
    }

    private fun updateTimeOffsetFromHeader(dateHeader: String?) {
        if (dateHeader.isNullOrBlank()) return
        try {
            // Try HTTP Date format: "Wed, 04 Nov 2026 16:43:05 GMT" or ISO "2026-11-04T16:43:05Z"
            val formats = listOf(
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            )
            for (fmt in formats) {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                val parsed = try { fmt.parse(dateHeader.trim()) } catch (_: Exception) { null }
                if (parsed != null) {
                    val diffSec = (System.currentTimeMillis() - parsed.time) / 1000L
                    serverTimeOffsetSec = diffSec
                    break
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun aes256OfbCrypt(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        val out = ByteArray(input.size)
        var block = iv.copyOf()
        var offset = 0
        while (offset < input.size) {
            val ks = cipher.doFinal(block)
            block = ks
            val chunk = minOf(16, input.size - offset)
            for (j in 0 until chunk) {
                out[offset + j] = (input[offset + j].toInt() xor ks[j].toInt()).toByte()
            }
            offset += 16
        }
        return out
    }

    private fun decryptDeviceInfoBlob(base64Info: String): JSONObject? {
        return try {
            val raw = Base64.decode(base64Info.trim(), Base64.DEFAULT)
            val plain = aes256OfbCrypt(DEVINFO_KEY, DEVINFO_IV, raw)
            val str = String(plain, Charsets.UTF_8).trim { it <= ' ' || it == '\u0000' }
            JSONObject(str)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveMasterKey(username: String, password: String, randsalt: String): ByteArray {
        val md5 = MessageDigest.getInstance("MD5")
        val input = "$username:Login to $randsalt:$password".toByteArray(Charsets.UTF_8)
        val digest = md5.digest(input)
        val hex = digest.joinToString("") { "%02X".format(it) }
        return hex.toByteArray(Charsets.US_ASCII)
    }

    private fun derivePbkdf2Key(masterKey: ByteArray, nonce: Int): ByteArray {
        val chars = String(masterKey, Charsets.US_ASCII).toCharArray()
        val salt = nonce.toString().toByteArray(Charsets.US_ASCII)
        val spec = PBEKeySpec(chars, salt, 20000, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }

    private fun encryptLocalAddr(masterKey: ByteArray, nonce: Int, plainAddr: String): String {
        val dk = derivePbkdf2Key(masterKey, nonce)
        val enc = aes256OfbCrypt(dk, AES_IV, plainAddr.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    private fun decryptLocalAddr(masterKey: ByteArray, nonce: Int, encBase64: String): String {
        return try {
            val dk = derivePbkdf2Key(masterKey, nonce)
            val raw = Base64.decode(encBase64.trim(), Base64.DEFAULT)
            val dec = aes256OfbCrypt(dk, AES_IV, raw)
            String(dec, Charsets.UTF_8)
        } catch (_: Exception) {
            encBase64
        }
    }

    private fun buildDevAuthXml(
        username: String,
        masterKey: ByteArray,
        nonce: Int,
        payload: String,
        randsalt: String,
        createdSec: Long
    ): String {
        val msg = "$nonce$createdSec$payload".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(masterKey, "HmacSHA256"))
        val authB64 = Base64.encodeToString(mac.doFinal(msg), Base64.NO_WRAP)
        val saltXml = if (randsalt.isNotEmpty()) "<RandSalt>$randsalt</RandSalt>" else "<RandSalt></RandSalt>"
        return "<CreateDate>$createdSec</CreateDate><DevAuth>$authB64</DevAuth><Nonce>$nonce</Nonce>$saltXml<UserName>$username</UserName>"
    }

    private fun buildDhHttpRequest(
        method: String,
        path: String,
        body: String = "",
        cseq: Int = globalCseq.incrementAndGet()
    ): ByteArray {
        val wnonce = random.nextInt(Int.MAX_VALUE - 1000) + 1000
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val createdStr = sdf.format(Date(nowAdjustedMillis()))
        val sha1 = MessageDigest.getInstance("SHA-1")
        val pwdInput = "${wnonce}${createdStr}DHP2P:$WSSE_USERNAME:$WSSE_USERKEY".toByteArray(Charsets.UTF_8)
        val digestB64 = Base64.encodeToString(sha1.digest(pwdInput), Base64.NO_WRAP)

        val sb = StringBuilder()
        sb.append("$method $path HTTP/1.1\r\n")
        sb.append("CSeq: $cseq\r\n")
        sb.append("Authorization: WSSE profile=\"UsernameToken\"\r\n")
        sb.append("X-WSSE: UsernameToken Username=\"$WSSE_USERNAME\", PasswordDigest=\"$digestB64\", Nonce=\"$wnonce\", Created=\"$createdStr\"\r\n")
        if (body.isNotEmpty()) {
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            sb.append("Content-Type: \r\n")
            sb.append("Content-Length: ${bodyBytes.size}\r\n")
        }
        sb.append("\r\n")
        sb.append(body)
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private data class DhHttpResponse(
        val code: Int,
        val status: String,
        val headers: Map<String, String>,
        val rawBody: String
    ) {
        fun xmlTag(tag: String): String {
            val regex = Regex("<$tag>([^<]*)</$tag>", RegexOption.IGNORE_CASE)
            return regex.find(rawBody)?.groupValues?.getOrNull(1)?.trim() ?: ""
        }
    }

    private fun parseDhHttpResponse(data: ByteArray, length: Int): DhHttpResponse? {
        val text = String(data, 0, length, Charsets.UTF_8)
        if (!text.startsWith("HTTP/")) return null
        val splitIdx = text.indexOf("\r\n\r\n")
        val headPart = if (splitIdx >= 0) text.substring(0, splitIdx) else text
        val bodyPart = if (splitIdx >= 0) text.substring(splitIdx + 4) else ""

        val lines = headPart.split("\r\n")
        val statusLine = lines.firstOrNull() ?: return null
        val sParts = statusLine.split(" ", limit = 3)
        val code = sParts.getOrNull(1)?.toIntOrNull() ?: 0
        val status = sParts.getOrNull(2) ?: ""

        val headers = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val idx = lines[i].indexOf(':')
            if (idx > 0) {
                val k = lines[i].substring(0, idx).trim()
                val v = lines[i].substring(idx + 1).trim()
                headers[k] = v
            }
        }
        updateTimeOffsetFromHeader(headers["Date"] ?: headers["ServerTime"])
        return DhHttpResponse(code, status, headers, bodyPart)
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {
        }
        return "127.0.0.1"
    }

    // =========================================================================
    // PTCP Framing & Session Engine
    // =========================================================================

    private class PtcpFrame(
        val rlid: Int,
        val llid: Int,
        val pid: Int,
        val lmid: Int,
        val rmid: Int,
        val body: ByteArray
    ) {
        fun toBytes(): ByteArray {
            val buf = ByteBuffer.allocate(24 + body.size).order(ByteOrder.BIG_ENDIAN)
            buf.put('P'.code.toByte())
            buf.put('T'.code.toByte())
            buf.put('C'.code.toByte())
            buf.put('P'.code.toByte())
            buf.putInt(rlid)
            buf.putInt(llid)
            buf.putInt(pid)
            buf.putInt(lmid)
            buf.putInt(rmid)
            buf.put(body)
            return buf.array()
        }

        companion object {
            fun parse(data: ByteArray, length: Int): PtcpFrame? {
                if (length < 24) return null
                if (data[0] != 'P'.code.toByte() ||
                    data[1] != 'T'.code.toByte() ||
                    data[2] != 'C'.code.toByte() ||
                    data[3] != 'P'.code.toByte()
                ) {
                    return null
                }
                val buf = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN)
                buf.position(4)
                val rlid = buf.int
                val llid = buf.int
                val pid = buf.int
                val lmid = buf.int
                val rmid = buf.int
                val bodyLen = length - 24
                val body = ByteArray(bodyLen)
                if (bodyLen > 0) {
                    System.arraycopy(data, 24, body, 0, bodyLen)
                }
                return PtcpFrame(rlid, llid, pid, lmid, rmid, body)
            }
        }
    }

    private class PtcpSocket(
        var remoteHost: String,
        var remotePort: Int
    ) {
        val socket = DatagramSocket(null).apply {
            reuseAddress = true
            receiveBufferSize = 2 * 1024 * 1024
            sendBufferSize = 512 * 1024
            bind(InetSocketAddress(0))
        }
        val localPort: Int = socket.localPort

        @Volatile
        var remoteAddress: InetAddress = InetAddress.getByName(remoteHost)

        private val ptcpLock = Any()
        private var ptcpSent: Int = 0
        private var ptcpRecv: Int = 0
        private var ptcpId: Int = 0
        private var rmid: Int = 0
        private var rcvNxt: Int = 0
        private val rcvOoo = HashMap<Int, PtcpFrame>()

        @Volatile
        var lastRecvTime: Long = System.currentTimeMillis()

        fun setRemote(host: String, port: Int) {
            remoteHost = host
            remotePort = port
            remoteAddress = InetAddress.getByName(host)
        }

        fun sendRaw(bytes: ByteArray, targetAddr: InetAddress = remoteAddress, targetPort: Int = remotePort) {
            try {
                val pkt = DatagramPacket(bytes, bytes.size, targetAddr, targetPort)
                socket.send(pkt)
            } catch (_: Exception) {
            }
        }

        fun sendDhRequest(method: String, path: String, body: String = "", cseq: Int = globalCseq.incrementAndGet()) {
            val req = buildDhHttpRequest(method, path, body, cseq)
            sendRaw(req)
        }

        fun readDhResponse(timeoutMs: Int): DhHttpResponse? {
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(8192)
            while (System.currentTimeMillis() < deadline) {
                val rem = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(100)
                try {
                    socket.soTimeout = rem
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    lastRecvTime = System.currentTimeMillis()
                    val resp = parseDhHttpResponse(buf, pkt.length)
                    if (resp != null) return resp
                } catch (_: Exception) {
                    break
                }
            }
            return null
        }

        fun sendPtcp(body: ByteArray = ByteArray(0)) {
            val raw = synchronized(ptcpLock) {
                val isSync = body.size == 4 &&
                    body[0] == 0x00.toByte() &&
                    body[1] == 0x03.toByte() &&
                    body[2] == 0x01.toByte() &&
                    body[3] == 0x00.toByte()

                val pid = if (isSync) 0x0002FFFF else 0x0000FFFF
                val frame = PtcpFrame(
                    rlid = ptcpSent,
                    llid = ptcpRecv,
                    pid = pid,
                    lmid = ptcpId,
                    rmid = rmid,
                    body = body
                )
                ptcpSent += body.size
                ptcpId += 1
                frame.toBytes()
            }
            sendRaw(raw)
        }

        fun readPtcp(timeoutMs: Int): PtcpFrame? {
            val deadline = System.currentTimeMillis() + timeoutMs
            val buf = ByteArray(65535)
            while (System.currentTimeMillis() < deadline) {
                val rem = (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(50)
                try {
                    socket.soTimeout = rem
                    val pkt = DatagramPacket(buf, buf.size)
                    socket.receive(pkt)
                    lastRecvTime = System.currentTimeMillis()
                    val frame = PtcpFrame.parse(buf, pkt.length) ?: continue

                    var deliver: PtcpFrame? = null
                    var sendAck = false

                    synchronized(ptcpLock) {
                        rmid = frame.lmid
                        if (frame.body.isEmpty()) {
                            deliver = frame
                        } else if (frame.rlid == rcvNxt) {
                            rcvNxt += frame.body.size
                            ptcpRecv = rcvNxt
                            deliver = frame
                            // Advance contiguous out-of-order frames if any
                            while (rcvOoo.containsKey(rcvNxt)) {
                                val next = rcvOoo.remove(rcvNxt)!!
                                rcvNxt += next.body.size
                                ptcpRecv = rcvNxt
                            }
                        } else if (frame.rlid > rcvNxt) {
                            if (rcvOoo.size < 128) {
                                rcvOoo[frame.rlid] = frame
                            }
                            sendAck = true
                        } else {
                            // Duplicate frame already processed
                            sendAck = true
                        }
                    }

                    if (sendAck) {
                        sendPtcp()
                        continue
                    }
                    if (deliver != null) {
                        return deliver
                    }
                } catch (_: Exception) {
                    return null
                }
            }
            return null
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private class P2pTunnelSession(
        val serialNumber: String,
        val username: String,
        val password: String
    ) {
        private val running = AtomicBoolean(false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        var localListenPort: Int = 0
            private set
        var remoteRtspPort: Int = 554
            private set
        var deviceLanAddr: String = ""
            private set
        var devicePubAddr: String = ""
            private set
        var modeDescription: String = "P2P Cloud"
            private set

        private var mainRemote: PtcpSocket? = null
        private var deviceRemote: PtcpSocket? = null
        @Volatile
        private var primarySocket: PtcpSocket? = null
        private var serverSocket: ServerSocket? = null

        private val clients = ConcurrentHashMap<Int, ClientRealmSession>()
        private val bindWaiters = ConcurrentHashMap<Int, LinkedBlockingQueue<Boolean>>()

        fun isRunning(): Boolean = running.get() && serverSocket?.isClosed == false

        fun establishAndStart(): P2pProbeResult {
            try {
                val mainSock = PtcpSocket(MAIN_SERVER, MAIN_PORT)
                mainRemote = mainSock

                // Step 1: Warmup & P2P Server Lookup
                mainSock.sendDhRequest("DHGET", "/probe/p2psrv")
                mainSock.readDhResponse(3000)

                mainSock.sendDhRequest("DHGET", "/online/p2psrv/$serialNumber")
                val srvResp = mainSock.readDhResponse(4000)
                    ?: return P2pProbeResult(false, 0, "Sin respuesta del servidor Dahua P2P Cloud")

                if (srvResp.code >= 400) {
                    return P2pProbeResult(false, 0, "El Número de Serie $serialNumber no existe en Dahua Cloud (${srvResp.code})")
                }
                val usAddr = srvResp.xmlTag("US")
                if (!usAddr.contains(":")) {
                    return P2pProbeResult(false, 0, "El NVR ($serialNumber) aparece Fuera de Línea en P2P Cloud")
                }
                val usParts = usAddr.split(":")
                val usHost = usParts[0]
                val usPort = usParts[1].toIntOrNull() ?: 8803

                // Step 2: Query Device Info & RandSalt from US Server
                val usSock = PtcpSocket(usHost, usPort)
                var randsalt = ""
                try {
                    usSock.sendDhRequest("DHGET", "/probe/device/$serialNumber")
                    usSock.readDhResponse(2500)
                    usSock.sendDhRequest("DHGET", "/info/device/$serialNumber")
                    val infoResp = usSock.readDhResponse(3500)
                    if (infoResp != null && infoResp.code < 300) {
                        val infoB64 = infoResp.xmlTag("Info")
                        if (infoB64.isNotEmpty()) {
                            val json = decryptDeviceInfoBlob(infoB64)
                            if (json != null) {
                                randsalt = json.optString("randsalt", "")
                                val rtsp = json.optInt("rtspport", 554)
                                if (rtsp > 0) remoteRtspPort = rtsp
                            }
                        }
                    }
                } finally {
                    usSock.close()
                }

                // Step 3: Allocate Relay Dispatcher & Agent
                mainSock.sendDhRequest("DHGET", "/online/relay")
                val relayDispResp = mainSock.readDhResponse(3500)
                val relayDispAddr = relayDispResp?.xmlTag("Address") ?: ""

                // Step 4: Send /device/{SN}/p2p-channel with Type-1 Authentication
                val devSock = PtcpSocket(MAIN_SERVER, MAIN_PORT)
                deviceRemote = devSock

                val localIp = getLocalIpAddress()
                val laddrPlain = "$localIp,$localIp:${devSock.localPort}"
                val masterKey = deriveMasterKey(username, password, randsalt)
                val aid = ByteArray(8).also { random.nextBytes(it) }
                val identifyStr = aid.joinToString(" ") { "%x".format(it) }

                val chanCseq = globalCseq.incrementAndGet()
                fun sendP2pChannelReq() {
                    val nonce = random.nextInt(Int.MAX_VALUE - 1000) + 1000
                    val createdSec = nowAdjustedMillis() / 1000L
                    val laddrEnc = encryptLocalAddr(masterKey, nonce, laddrPlain)
                    val authXml = buildDevAuthXml(username, masterKey, nonce, laddrEnc, randsalt, createdSec)
                    val body = "<body>$authXml<Identify>$identifyStr</Identify><IpEncrptV2>true</IpEncrptV2><LocalAddr>$laddrEnc</LocalAddr><version>5.0.0</version></body>"
                    devSock.sendDhRequest("DHPOST", "/device/$serialNumber/p2p-channel", body, chanCseq)
                }

                sendP2pChannelReq()

                // Allocate Relay Agent on mainSock while p2p-channel is in flight
                var agentHost = ""
                var agentPort = 0
                var agentToken = ""
                if (relayDispAddr.contains(":")) {
                    val rParts = relayDispAddr.split(":")
                    mainSock.setRemote(rParts[0], rParts[1].toIntOrNull() ?: 8900)
                    mainSock.sendDhRequest("DHGET", "/relay/agent")
                    val agentResp = mainSock.readDhResponse(3500)
                    if (agentResp != null && agentResp.code == 200) {
                        agentToken = agentResp.xmlTag("Token")
                        val agAddr = agentResp.xmlTag("Agent")
                        if (agAddr.contains(":")) {
                            val agParts = agAddr.split(":")
                            agentHost = agParts[0]
                            agentPort = agParts[1].toIntOrNull() ?: 0
                        }
                    }
                }

                if (agentHost.isNotEmpty() && agentPort > 0 && agentToken.isNotEmpty()) {
                    mainSock.setRemote(agentHost, agentPort)
                    mainSock.sendDhRequest(
                        "DHPOST",
                        "/relay/start/$agentToken",
                        "<body><Client>$localIp:${mainSock.localPort}</Client></body>"
                    )
                    mainSock.readDhResponse(2500)
                }

                // Read p2p-channel response (handle 100 Trying then 200 Server Nat Info)
                var chanResp = devSock.readDhResponse(4000)
                if (chanResp != null && chanResp.code == 100) {
                    chanResp = devSock.readDhResponse(6000)
                }
                // If first attempt returned 403 DevPwd_Timeout due to clock skew, serverTimeOffsetSec is now updated; retry once!
                if (chanResp == null || (chanResp.code == 403 && chanResp.rawBody.contains("Timeout", ignoreCase = true))) {
                    sendP2pChannelReq()
                    chanResp = devSock.readDhResponse(4000)
                    if (chanResp != null && chanResp.code == 100) {
                        chanResp = devSock.readDhResponse(6000)
                    }
                }

                if (chanResp == null) {
                    return P2pProbeResult(false, 0, "Tiempo de espera agotado esperando respuesta del NVR ($serialNumber)")
                }
                if (chanResp.code >= 400) {
                    val errDetail = chanResp.xmlTag("Error").ifEmpty { chanResp.status }
                    return P2pProbeResult(false, 0, "El NVR rechazó el acceso P2P ($errDetail). Verifica usuario y contraseña.")
                }

                val encDevLaddr = chanResp.xmlTag("LocalAddr")
                val devNonce = chanResp.xmlTag("Nonce").toIntOrNull() ?: 0
                deviceLanAddr = if (devNonce != 0 && encDevLaddr.isNotEmpty()) {
                    decryptLocalAddr(masterKey, devNonce, encDevLaddr)
                } else {
                    encDevLaddr
                }
                devicePubAddr = chanResp.xmlTag("PubAddr")

                // Step 5: Establish Relay Channel or Direct STUN Punch
                var tunnelEstablished = false

                // Try Relay Channel first (works across any firewall / CGNAT / different network)
                if (agentHost.isNotEmpty() && agentPort > 0) {
                    val relayCseq = globalCseq.incrementAndGet()
                    val nonce2 = random.nextInt(Int.MAX_VALUE - 1000) + 1000
                    val created2 = nowAdjustedMillis() / 1000L
                    val auth2 = buildDevAuthXml(username, masterKey, nonce2, "", randsalt, created2)
                    val relayBody = "<body>$auth2<agentAddr>$agentHost:$agentPort</agentAddr></body>"

                    for (attempt in 0..2) {
                        mainSock.setRemote(MAIN_SERVER, MAIN_PORT)
                        mainSock.sendDhRequest("DHPOST", "/device/$serialNumber/relay-channel", relayBody, relayCseq)
                        mainSock.setRemote(agentHost, agentPort)

                        val waitUntil = System.currentTimeMillis() + 2800L
                        while (System.currentTimeMillis() < waitUntil) {
                            val rResp = mainSock.readDhResponse(1200) ?: break
                            if (rResp.code == 100) continue
                            if (rResp.code == 200) {
                                // Relay channel acknowledged! Sync PTCP
                                mainSock.sendPtcp(byteArrayOf(0x00, 0x03, 0x01, 0x00))
                                val syncFrame = mainSock.readPtcp(4000)
                                if (syncFrame != null) {
                                    mainSock.sendPtcp()
                                    primarySocket = mainSock
                                    modeDescription = "P2P Cloud Relay"
                                    tunnelEstablished = true
                                }
                                break
                            }
                        }
                        if (tunnelEstablished) break
                    }
                }

                // If Relay wasn't used or failed, try Direct Inverted-STUN Punch
                if (!tunnelEstablished && devicePubAddr.contains(":")) {
                    val pubParts = devicePubAddr.split(":")
                    val pubHost = pubParts[0]
                    val pubPort = pubParts[1].toIntOrNull() ?: 0
                    if (pubPort > 0) {
                        devSock.setRemote(pubHost, pubPort)
                        val invAid = ByteArray(8) { i -> (aid[i].toInt() xor 0xFF).toByte() }
                        val cookie = ByteArray(4).also { random.nextBytes(it) }
                        val transId = ByteArray(12).also { random.nextBytes(it) }
                        val eaddr = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN).apply {
                            putShort(pubPort.toShort())
                            put(InetAddress.getByName(pubHost).address)
                        }.array()
                        for (i in eaddr.indices) eaddr[i] = (eaddr[i].toInt() xor 0xFF).toByte()

                        val stunInit = ByteBuffer.allocate(40).apply {
                            put(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xE7.toByte()))
                            put(cookie)
                            put(transId)
                            put(byteArrayOf(0x7F.toByte(), 0xD5.toByte(), 0xFF.toByte(), 0xF7.toByte()))
                            put(invAid)
                            put(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0xFF.toByte(), 0xF7.toByte(), 0xFF.toByte(), 0xFE.toByte()))
                            put(eaddr)
                        }.array()

                        repeat(3) { devSock.sendRaw(stunInit) }
                        val buf = ByteArray(4096)
                        try {
                            devSock.socket.soTimeout = 2500
                            val pkt = DatagramPacket(buf, buf.size)
                            devSock.socket.receive(pkt)
                            if (pkt.length >= 20) {
                                devSock.setRemote(pkt.address.hostAddress ?: pubHost, pkt.port)
                                val confirm = ByteBuffer.allocate(28).apply {
                                    put(byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xF3.toByte()))
                                    put(cookie)
                                    put(transId)
                                    put(byteArrayOf(0x7F.toByte(), 0xD6.toByte(), 0xFF.toByte(), 0xF7.toByte()))
                                    put(invAid)
                                }.array()
                                repeat(4) { devSock.sendRaw(confirm) }
                                devSock.sendPtcp(byteArrayOf(0x00, 0x03, 0x01, 0x00))
                                devSock.readPtcp(2000)
                                devSock.sendPtcp()
                                primarySocket = devSock
                                modeDescription = "P2P Direct STUN"
                                tunnelEstablished = true
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                if (!tunnelEstablished || primarySocket == null) {
                    return P2pProbeResult(
                        false,
                        0,
                        "NVR autenticado en P2P Cloud, pero no se pudo abrir el canal de transporte UDP"
                    )
                }

                // Step 6: Start local TCP ServerSocket on 127.0.0.1:0 and background PTCP pump loops
                val srv = ServerSocket()
                srv.reuseAddress = true
                srv.bind(InetSocketAddress("127.0.0.1", 0), 32)
                serverSocket = srv
                localListenPort = srv.localPort
                running.set(true)

                startBackgroundLoops()

                Log.i(TAG, "P2P Tunnel ready on 127.0.0.1:$localListenPort -> NVR($serialNumber):$remoteRtspPort ($modeDescription)")
                return P2pProbeResult(
                    success = true,
                    localPort = localListenPort,
                    message = "¡Conectado por P2P Cloud a SN $serialNumber! (LAN NVR: $deviceLanAddr · WAN: $devicePubAddr)",
                    remoteLanIp = deviceLanAddr,
                    remoteWanIp = devicePubAddr,
                    rtspPort = remoteRtspPort
                )
            } catch (e: Exception) {
                Log.e(TAG, "P2P establish error", e)
                return P2pProbeResult(false, 0, "Error P2P: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }

        private fun startBackgroundLoops() {
            val pSock = primarySocket ?: return

            // 1. PTCP Reader Loop
            scope.launch {
                while (running.get() && isActive) {
                    val frame = pSock.readPtcp(250)
                    if (frame == null) {
                        if (System.currentTimeMillis() - pSock.lastRecvTime > 40_000L) {
                            Log.w(TAG, "PTCP tunnel silence timeout")
                            close()
                            break
                        }
                        continue
                    }
                    routeInboundPtcp(frame, pSock)
                }
            }

            // 2. PTCP Heartbeat Loop (every 4.5 seconds)
            scope.launch {
                while (running.get() && isActive) {
                    delay(4500L)
                    if (!running.get()) break
                    pSock.sendPtcp()
                }
            }

            // 3. Local TCP Accept Loop (LibVLC connects here for each camera stream)
            scope.launch {
                val srv = serverSocket ?: return@launch
                while (running.get() && isActive && !srv.isClosed) {
                    val clientSock = try {
                        srv.accept()
                    } catch (_: Exception) {
                        break
                    }
                    clientSock.tcpNoDelay = true
                    scope.launch {
                        handleLocalClient(clientSock)
                    }
                }
            }
        }

        private fun routeInboundPtcp(frame: PtcpFrame, pSock: PtcpSocket) {
            val body = frame.body
            if (body.isEmpty()) {
                return
            }
            // Send cumulative ACK
            pSock.sendPtcp()

            when (body[0].toInt() and 0xFF) {
                0x00 -> {
                    // Peer SYNC
                    pSock.sendPtcp()
                }
                0x10 -> {
                    // Realm DATA fragment: [0]=0x10, [1]=0, [2..3]=len(BE), [4..7]=realm(BE), [8..11]=0, [12..]=payload
                    if (body.size < 12) return
                    val buf = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)
                    val len = buf.getShort(2).toInt() and 0xFFFF
                    val realmId = buf.getInt(4)
                    val actualLen = minOf(len, body.size - 12)
                    if (actualLen > 0) {
                        val client = clients[realmId]
                        if (client != null) {
                            try {
                                client.output.write(body, 12, actualLen)
                                client.output.flush()
                            } catch (_: Exception) {
                                closeClientRealm(realmId, sendDisc = true)
                            }
                        }
                    }
                }
                0x12 -> {
                    // Realm BIND ACK or DISC: [0]=0x12, [4..7]=realm(BE), [12..15]="DISC" optional
                    if (body.size < 8) return
                    val realmId = ByteBuffer.wrap(body, 4, 4).order(ByteOrder.BIG_ENDIAN).int
                    val isDisc = body.size >= 16 &&
                        body[12] == 'D'.code.toByte() &&
                        body[13] == 'I'.code.toByte() &&
                        body[14] == 'S'.code.toByte() &&
                        body[15] == 'C'.code.toByte()

                    val waiter = bindWaiters.remove(realmId)
                    if (waiter != null) {
                        waiter.offer(!isDisc)
                        return
                    }
                    if (isDisc) {
                        closeClientRealm(realmId, sendDisc = false)
                    }
                }
            }
        }

        private fun handleLocalClient(clientSocket: Socket) {
            val pSock = primarySocket ?: run {
                try { clientSocket.close() } catch (_: Exception) {}
                return
            }

            val realmId = random.nextInt()
            val waiter = LinkedBlockingQueue<Boolean>(1)
            bindWaiters[realmId] = waiter

            // Build 20-byte PTCP BIND packet for remoteRtspPort (554) on 127.0.0.1
            val bindPkt = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN).apply {
                put(0, 0x11.toByte())
                putInt(4, realmId)
                putInt(12, remoteRtspPort)
                put(16, 0x7F.toByte())
                put(19, 0x01.toByte())
            }.array()

            var boundOk = false
            for (attempt in 0..4) {
                pSock.sendPtcp(bindPkt)
                val res = try {
                    waiter.poll(600, TimeUnit.MILLISECONDS)
                } catch (_: Exception) {
                    null
                }
                if (res != null) {
                    boundOk = res
                    break
                }
            }
            bindWaiters.remove(realmId)

            if (!boundOk) {
                try { clientSocket.close() } catch (_: Exception) {}
                return
            }

            val session = try {
                ClientRealmSession(
                    realmId = realmId,
                    socket = clientSocket,
                    input = clientSocket.getInputStream(),
                    output = clientSocket.getOutputStream()
                )
            } catch (_: Exception) {
                try { clientSocket.close() } catch (_: Exception) {}
                return
            }

            clients[realmId] = session

            // Pump upstream bytes from local LibVLC RTSP socket into PTCP DATA frames
            val readBuf = ByteArray(1280)
            try {
                while (running.get() && !clientSocket.isClosed) {
                    val n = session.input.read(readBuf)
                    if (n <= 0) break
                    val dataFrame = ByteBuffer.allocate(12 + n).order(ByteOrder.BIG_ENDIAN).apply {
                        putInt(0, n or 0x10000000)
                        putInt(4, realmId)
                        putInt(8, 0)
                        put(12, readBuf, 0, n)
                    }.array()
                    pSock.sendPtcp(dataFrame)
                }
            } catch (_: Exception) {
            } finally {
                closeClientRealm(realmId, sendDisc = true)
            }
        }

        private fun closeClientRealm(realmId: Int, sendDisc: Boolean) {
            val session = clients.remove(realmId) ?: return
            if (sendDisc) {
                val pSock = primarySocket
                if (pSock != null && running.get()) {
                    val discPkt = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
                        put(0, 0x12.toByte())
                        putInt(4, realmId)
                        put(12, 'D'.code.toByte())
                        put(13, 'I'.code.toByte())
                        put(14, 'S'.code.toByte())
                        put(15, 'C'.code.toByte())
                    }.array()
                    pSock.sendPtcp(discPkt)
                }
            }
            try {
                session.socket.close()
            } catch (_: Exception) {
            }
        }

        fun close() {
            if (!running.getAndSet(false)) return
            for (id in clients.keys.toList()) {
                closeClientRealm(id, sendDisc = false)
            }
            clients.clear()
            bindWaiters.clear()
            try { serverSocket?.close() } catch (_: Exception) {}
            mainRemote?.close()
            deviceRemote?.close()
        }
    }

    private class ClientRealmSession(
        val realmId: Int,
        val socket: Socket,
        val input: InputStream,
        val output: OutputStream
    )
}
