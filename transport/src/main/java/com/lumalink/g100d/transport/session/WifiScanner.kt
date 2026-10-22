package com.lumalink.g100d.transport.session

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresPermission

data class WifiCandidate(
    val ssid: String,
    val level: Int,
    val secured: Boolean,
    val isLikelyCamera: Boolean,
    val is5Ghz: Boolean,
    val isWifi5: Boolean,
)

class WifiScanner(context: Context) {

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)

    @RequiresPermission(
        anyOf = [
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ],
    )
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun scan(): List<WifiCandidate> {
        runCatching { wifiManager.startScan() }
        val results = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
        return results
            .asSequence()
            .filter { it.SSID.isNotBlank() }
            .distinctBy { it.SSID }
            .map { result ->
                val ssid = result.SSID
                val is5Ghz = result.frequency >= 4_900
                val isWifi5 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    result.wifiStandard == android.net.wifi.ScanResult.WIFI_STANDARD_11AC
                WifiCandidate(
                    ssid = ssid,
                    level = result.level,
                    secured = result.capabilities.contains("WPA") ||
                        result.capabilities.contains("WEP") ||
                        result.capabilities.contains("SAE"),
                    isLikelyCamera = isLikelyCamera(ssid),
                    is5Ghz = is5Ghz,
                    isWifi5 = isWifi5,
                )
            }
            .sortedWith(
                compareByDescending<WifiCandidate> { it.isLikelyCamera }
                    .thenByDescending { it.isWifi5 }
                    .thenByDescending { it.is5Ghz }
                    .thenByDescending { it.level },
            )
            .take(40)
            .toList()
    }

    fun isLikelyCamera(ssid: String): Boolean {
        val upper = ssid.uppercase()
        return "LUMIX" in upper ||
            "PANASONIC" in upper ||
            "DC-" in upper ||
            "G100" in upper ||
            "S5" in upper ||
            "S1" in upper ||
            "GH" in upper ||
            "G9" in upper ||
            "TZ" in upper ||
            "ZS" in upper
    }
}
