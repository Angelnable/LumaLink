package com.lumalink.g100d.transport.session

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import androidx.annotation.RequiresPermission
import com.lumalink.g100d.core.diagnostics.Diagnostics
import com.lumalink.g100d.protocol.CameraModel
import com.lumalink.g100d.protocol.CameraStatus
import com.lumalink.g100d.protocol.HostGuard
import com.lumalink.g100d.protocol.LumixEndpoints
import com.lumalink.g100d.protocol.LumixHttpClient
import com.lumalink.g100d.protocol.CameraSessionStore
import com.lumalink.g100d.protocol.LumixHttpProtocol
import com.lumalink.g100d.protocol.LumixLogger
import com.lumalink.g100d.protocol.LumixResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class CameraSession(
    val protocol: LumixHttpProtocol,
    val client: LumixHttpClient,
    val model: CameraModel,
    val status: CameraStatus?,
    val baseUrl: String,
)

sealed interface CameraConnectionState {
    data object Disconnected : CameraConnectionState
    data class Connecting(val ssid: String) : CameraConnectionState
    data class Handshaking(val ssid: String) : CameraConnectionState
    data class Connected(val session: CameraSession) : CameraConnectionState
    data class Error(val message: String) : CameraConnectionState
}

class ConnectionManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected)
    val state: StateFlow<CameraConnectionState> = _state.asStateFlow()

    private val _session = MutableStateFlow<CameraSession?>(null)
    val session: StateFlow<CameraSession?> = _session.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null
    private var timeoutJob: Job? = null
    private var handshakeJob: Job? = null

    private val logger = object : LumixLogger {
        override fun log(entry: com.lumalink.g100d.protocol.LumixHttpLogEntry) {
            Diagnostics.logHttp(
                method = entry.method,
                url = entry.url,
                statusCode = entry.statusCode,
                durationMs = entry.durationMs,
                error = entry.error,
            )
        }

        override fun logBody(url: String, body: String) {
            Diagnostics.info("响应体 $url: ${body.take(1500)}")
        }
    }

    @RequiresPermission(
        anyOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ],
    )
    @SuppressLint("MissingPermission")
    fun connect(ssid: String, password: String, baseUrl: String) {
        disconnect()

        val cleanSsid = ssid.trim().trim('"')
        if (cleanSsid.isBlank()) {
            _state.value = CameraConnectionState.Error("请输入相机 SSID")
            return
        }
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        if (cleanBaseUrl == null) {
            _state.value = CameraConnectionState.Error("相机地址必须是本地地址（如 192.168.54.1）")
            return
        }

        // If the phone is already on the camera hotspot (for example the user
        // joined it manually), binding the existing Wi-Fi network is more
        // reliable than asking the system for a new specifier.
        val currentSsid = runCatching {
            wifiManager.connectionInfo.ssid?.trim('"')
        }.getOrNull()
        if (!currentSsid.isNullOrBlank() && currentSsid.equals(cleanSsid, ignoreCase = true)) {
            val network = findWifiNetwork()
            if (network != null) {
                Diagnostics.info("已连接到目标 SSID $currentSsid，直接绑定现有网络")
                _state.value = CameraConnectionState.Connecting(cleanSsid)
                bindAndHandshake(network, cleanSsid, cleanBaseUrl)
                return
            }
        }

        Diagnostics.info("请求连接相机热点 SSID=$cleanSsid 地址=$cleanBaseUrl")
        _state.value = CameraConnectionState.Connecting(cleanSsid)

        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(cleanSsid)
        if (password.isNotBlank()) {
            specifierBuilder.setWpa2Passphrase(password)
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Diagnostics.info("系统已加入相机热点：${describeNetwork(network)}")
                bindAndHandshake(network, cleanSsid, cleanBaseUrl)
            }

            override fun onUnavailable() {
                handshakeJob = scope.launch {
                    timeoutJob?.cancel()
                    cleanupNetwork()
                    Diagnostics.warn("系统未能加入相机热点（onUnavailable）")
                    _state.value = CameraConnectionState.Error(
                        "未能加入相机热点。请在系统弹窗中允许连接，并确认相机 Wi-Fi 已开启。",
                    )
                }
            }

            override fun onLost(network: Network) {
                scope.launch {
                    if (network == boundNetwork) {
                        Diagnostics.warn("相机 Wi-Fi 网络丢失，自动断开")
                        disconnect()
                    }
                }
            }
        }

        callback = networkCallback
        try {
            connectivityManager.requestNetwork(request, networkCallback)
        } catch (error: SecurityException) {
            cleanupNetwork()
            Diagnostics.error("requestNetwork 权限失败：${error.message}")
            _state.value = CameraConnectionState.Error("缺少 Wi-Fi 权限：${error.message}")
            return
        } catch (error: IllegalArgumentException) {
            cleanupNetwork()
            Diagnostics.error("requestNetwork 参数失败：${error.message}")
            _state.value = CameraConnectionState.Error("Wi-Fi 参数无效：${error.message}")
            return
        }

        timeoutJob = scope.launch {
            delay(60_000)
            if (_state.value is CameraConnectionState.Connecting) {
                cleanupNetwork()
                _state.value = CameraConnectionState.Error("连接超时。请确认相机处于 Wi-Fi 待连接状态。")
            }
        }
    }

    /**
     * Fallback for devices where WifiNetworkSpecifier is unreliable: the user
     * joins the camera hotspot in system Settings, then taps this action.
     */
    @RequiresPermission(
        anyOf = [
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ],
    )
    @SuppressLint("MissingPermission")
    fun useCurrentWifi(baseUrl: String) {
        disconnect()
        val cleanBaseUrl = normalizeBaseUrl(baseUrl)
        if (cleanBaseUrl == null) {
            _state.value = CameraConnectionState.Error("相机地址必须是本地地址（如 192.168.54.1）")
            return
        }
        val network = findWifiNetwork()
        if (network == null) {
            _state.value = CameraConnectionState.Error(
                "当前没有可用的 Wi-Fi。请先在系统设置中加入相机热点，再回来点击此按钮。",
            )
            return
        }
        Diagnostics.info("使用当前 Wi-Fi：${describeNetwork(network)}")
        val currentSsid = runCatching {
            wifiManager.connectionInfo.ssid?.trim('"')
        }.getOrNull()
            ?.takeUnless { it.startsWith("<") || it.isBlank() }
            .orEmpty()
            .ifBlank { "当前 Wi-Fi" }
        Diagnostics.info("当前 SSID：$currentSsid")
        _state.value = CameraConnectionState.Connecting(currentSsid)
        bindAndHandshake(network, currentSsid, cleanBaseUrl)
    }

    private var ssidHint: String? = null

    private fun bindAndHandshake(network: Network, ssid: String, baseUrl: String) {
        ssidHint = ssid
        handshakeJob = scope.launch {
            timeoutJob?.cancel()
            val bound = connectivityManager.bindProcessToNetwork(network)
            Diagnostics.info(
                "bindProcessToNetwork=$bound ${describeNetwork(network)}",
            )
            if (!bound) {
                cleanupNetwork()
                _state.value = CameraConnectionState.Error("无法绑定相机 Wi-Fi 网络")
                return@launch
            }
            boundNetwork = network
            logWifiLink()
            _state.value = CameraConnectionState.Handshaking(ssid)
            handshake(baseUrl)
        }
    }

    @SuppressLint("MissingPermission")
    private fun logWifiLink() {
        runCatching {
            val info = wifiManager.connectionInfo
            val rx = if (android.os.Build.VERSION.SDK_INT >= 30) {
                " rx=${info.rxLinkSpeedMbps}Mbps"
            } else {
                ""
            }
            Diagnostics.info(
                "Wi-Fi 链路：freq=${info.frequency}MHz link=${info.linkSpeed}Mbps$rx" +
                    if (info.frequency >= 4900) "（5GHz）" else "（2.4GHz）",
            )
        }.onFailure { Diagnostics.warn("读取 Wi-Fi 链路失败：${it.message}") }
    }

    private fun findWifiNetwork(): Network? {
        val active = connectivityManager.activeNetwork
        if (active != null &&
            connectivityManager.getNetworkCapabilities(active)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        ) {
            return active
        }
        return connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun describeNetwork(network: Network): String {
        val link = connectivityManager.getLinkProperties(network)
        val addresses = link?.linkAddresses?.joinToString(",") { it.address.hostAddress.orEmpty() }
        val dns = link?.dnsServers?.joinToString(",") { it.hostAddress.orEmpty() }
        return "if=${link?.interfaceName ?: "?"} addrs=$addresses dns=$dns"
    }

    private suspend fun handshake(baseUrl: String) {
        // Panasonic models do not share one AP subnet. G100D uses
        // 192.168.54.1; several LUMIX Sync bodies use 192.168.11.1 or
        // 192.168.1.1. Try the user's address first, then common fallbacks.
        val candidates = linkedSetOf(
            baseUrl,
            "http://192.168.54.1",
            "http://192.168.11.1",
            "http://192.168.1.1",
        )
        var lastError: String? = null

        for (candidate in candidates) {
            CameraSessionStore.clear()
            val protocol = LumixHttpProtocol(candidate, logger = logger)
            val timeout = if (candidate == baseUrl) 20_000L else 8_000L
            val result = withTimeoutOrNull(timeout) { protocol.handshake() }
            if (result == null) {
                lastError = "${candidate.removePrefix("http://")} 无响应"
                continue
            }
            if (result is LumixResult.Failure) {
                lastError = result.error.message ?: "未知协议错误"
                continue
            }
            if (result is LumixResult.Success) {
                val status = withTimeoutOrNull(8_000) { protocol.readStatus() }
                    ?.let { if (it is LumixResult.Success) it.value else null }
                val resolvedModel = if (result.value.model == "LUMIX") {
                    modelFromSsid(ssidHint) ?: result.value
                } else {
                    result.value
                }
                val session = CameraSession(
                    protocol = protocol,
                    client = protocol.httpClient(),
                    model = resolvedModel,
                    status = status,
                    baseUrl = candidate,
                )
                _session.value = session
                Diagnostics.info(
                    "握手成功：${result.value.model} / 固件 ${result.value.firmware ?: "未知"} / $candidate",
                )
                _state.value = CameraConnectionState.Connected(session)
                return
            }
        }

        cleanupNetwork()
        Diagnostics.error("握手失败：$lastError")
        _state.value = CameraConnectionState.Error(
            "已加入相机热点，但握手失败：${lastError ?: "未知协议错误"}",
        )
    }

    fun updateModel(model: com.lumalink.g100d.protocol.CameraModel) {
        val current = _session.value ?: return
        val updated = current.copy(model = model)
        _session.value = updated
        _state.value = CameraConnectionState.Connected(updated)
        Diagnostics.info("已更新相机型号：${model.model}")
    }

    fun disconnect() {
        Diagnostics.info("断开相机会话")
        ssidHint = null
        CameraSessionStore.clear()
        timeoutJob?.cancel()
        timeoutJob = null
        handshakeJob?.cancel()
        handshakeJob = null
        cleanupNetwork()
        _session.value = null
        _state.value = CameraConnectionState.Disconnected
    }

    private fun cleanupNetwork() {
        callback?.let { networkCallback ->
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
        callback = null
        if (boundNetwork != null) {
            runCatching { connectivityManager.bindProcessToNetwork(null) }
        }
        boundNetwork = null
    }

    private fun modelFromSsid(ssid: String?): com.lumalink.g100d.protocol.CameraModel? {
        val upper = ssid?.uppercase().orEmpty()
        val patterns = listOf(
            "G100D", "S5M2X", "S5M2", "GH5M2", "G9M2", "GH7", "GH6", "S1R", "S1H",
            "S1", "S5", "S9", "G7", "ZS99", "TZ99",
        )
        val model = patterns.firstOrNull { upper.contains(it) } ?: return null
        return com.lumalink.g100d.protocol.CameraModel(model = model)
    }

    private fun normalizeBaseUrl(value: String): String? {
        val trimmed = value.trim().ifBlank { LumixEndpoints.DEFAULT_BASE_URL }.trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val host = withScheme
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
            .substringBefore(':')
        if (!HostGuard.isLocalHost(host)) return null
        return withScheme
    }
}
