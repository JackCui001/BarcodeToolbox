package com.jackcui.codesc

import android.content.Context
import android.net.wifi.WifiManager


class WifiUtils {
    data class WifiInfo(
        var phoneNumbers: MutableSet<String> = mutableSetOf(),
    )

    fun getConnectedWifiSSID(context: Context): String {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connInfo = wifiManager.connectionInfo
        return connInfo.getSSID()
    }

}