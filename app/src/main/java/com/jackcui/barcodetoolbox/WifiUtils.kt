package com.jackcui.barcodetoolbox

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_WIFI_ADD_NETWORKS
import android.provider.Settings.EXTRA_WIFI_NETWORK_LIST
import androidx.annotation.RequiresApi
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.jackcui.barcodetoolbox.MainActivity.Companion.WIFI_INTENT_REQ_CODE
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast
import com.jackcui.barcodetoolbox.MainActivity.Companion.showInfoToast


typealias WlanInfo = MutableMap<String, String>

class WifiUtils(context: Context) {
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val mContext = context

    companion object {
        const val TAG = "WifiUtils"
        val PERMISSION = arrayOf(Permission.ACCESS_FINE_LOCATION)
        var ssid = ""
    }

    enum class Cipher {
        WIFI_CIPHER_WEP, WIFI_CIPHER_WPA, WIFI_CIPHER_NO_PASS
    }

    /**
     * 搜索周围热点，并返回信息
     */
    @SuppressLint("MissingPermission")
    fun scanWifiAndGetInfo(): WlanInfo {
        val ssidMap: WlanInfo = mutableMapOf()
        val bGranted = XXPermissions.isGranted(mContext, PERMISSION)
        val bWifiEnabled = enableWifi()
        if (!bGranted || !bWifiEnabled) {
            return ssidMap
        }
        for (ap in wifiManager.scanResults) {
            if (ap.SSID.isBlank()) {
                continue
            }
            ssidMap[ap.SSID] = ap.capabilities
        }
        return ssidMap
    }

    /**
     * 连接到指定热点
     */
    @SuppressLint("MissingPermission")
    fun connectWifi(ssid: String, password: String) {
        val bGranted = XXPermissions.isGranted(mContext, PERMISSION)
        val bWifiEnabled = enableWifi()
        if (!bGranted || !bWifiEnabled) {
            return
        }
        val scanResult = wifiManager.scanResults.singleOrNull { it.SSID == ssid }
        // Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                if (scanResult == null) {
                    showErrorToast("未在附近扫描到此热点")
                    return
                }
                connectByP2P(ssid, password)
            } else {  // Android 11+，直接添加到网络配置，不需要判断周围是否存在网络
                connectByIntent(ssid, password)
            }
        }
        // Android 10-
        else {
            if (scanResult == null) {
                showErrorToast("未在附近扫描到此热点")
                return
            }
            val bSuccess = connectByConfig(ssid, password, getCipherType(scanResult.capabilities))
            if (bSuccess) {
                showInfoToast("$ssid - 热点连接成功")
            } else {
                showErrorToast("$ssid - 热点连接失败")
            }
        }
    }

    private fun enableWifi(): Boolean {
        if (!wifiManager.isWifiEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showErrorToast("请先打开WLAN功能")
                val it = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                mContext.applicationContext.startActivity(it)
                return false
            } else {
                return wifiManager.setWifiEnabled(true)
            }
        }
        return true
    }

    /**
     *  Android 10以下适用，最底层最直接调用连接，保存到配置文件
     */
    @SuppressLint("MissingPermission")
    private fun connectByConfig(ssid: String, password: String, cipherType: Cipher): Boolean {
        // 如果找到了wifi了，从配置表中搜索该wifi的配置config，也就是以前有没有连接过
        // 注意configuredNetworks中的ssid，系统源码中加上了双引号，这里比对的时候要去掉
        val config =
            wifiManager.configuredNetworks.singleOrNull { it.SSID.replace("\"", "") == ssid }
        return if (config != null) {
            // 如果找到了，那么直接连接，不要调用wifiManager.addNetwork  这个方法会更改config的！
            wifiManager.enableNetwork(config.networkId, true)
        } else {
            // 没找到的话，就创建一个新的配置，然后正常的addNetWork、enableNetwork即可
            val padWifiNetwork = createWifiConfig(
                ssid, password, cipherType
            )
            val netId = wifiManager.addNetwork(padWifiNetwork)
            wifiManager.enableNetwork(netId, true)
        }
    }

    /**
     * Android 10+适用，通过P2P临时连接Wifi，应用被杀掉后就会断连，不保存到配置文件
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectByP2P(ssid: String, password: String) {
        val specifier =
            WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(password).build()
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier).build()

        val connectivityManager =
            mContext.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                showInfoToast("$ssid - 热点连接成功")
            }

            override fun onUnavailable() {
                showErrorToast("$ssid - 热点连接失败")
            }
        }
        connectivityManager.requestNetwork(request, networkCallback)
    }

    /**
     *  Android 10+适用，通过Suggestion连接Wifi，和P2P一样不保存到配置文件，而且无法取消连接？？？不建议使用
     */
//    @RequiresApi(Build.VERSION_CODES.Q)
//    private fun connectBySuggestion(ssid: String, password: String) {
//        val suggestion = WifiNetworkSuggestion.Builder().setSsid(ssid).setWpa2Passphrase(password)
//            .setIsAppInteractionRequired(true) // Optional (Needs location permission)
//            .build()
//        val suggestionsList = listOf(suggestion)
////        wifiManager.removeNetworkSuggestions(suggestionsList)
//        val status = wifiManager.addNetworkSuggestions(suggestionsList)
//        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
//            // do error handling here
//        }
//        Log.d(TAG, status.toString())
//        val intentFilter = IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
//        val broadcastReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                if (!intent.action.equals(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)) {
//                    showInfoToast("$ssid - 热点连接成功")
//                    return
//                }
//            }
//        }
//        mContext.applicationContext.registerReceiver(broadcastReceiver, intentFilter)
//    }

    /**
     *  Android 11+适用，使用系统Intent: ACTION_WIFI_ADD_NETWORKS连接，保存到配置文件，高版本的最佳方案
     *  并且可以一次添加最多5个网络
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun connectByIntent(ssid: String, password: String) {
        val suggestion =
            WifiNetworkSuggestion.Builder().setSsid(ssid).setWpa2Passphrase(password).build()
        val suggestions = arrayListOf(suggestion)
//        suggestions.add(...)
        WifiUtils.ssid = ssid

        // Create intent
        val bundle = Bundle()
        bundle.putParcelableArrayList(EXTRA_WIFI_NETWORK_LIST, suggestions)
        val intent = Intent(ACTION_WIFI_ADD_NETWORKS)
        intent.putExtras(bundle)

        // Launch intent
        (mContext as Activity).startActivityForResult(intent, WIFI_INTENT_REQ_CODE)
    }

    @SuppressLint("MissingPermission")
    private fun createWifiConfig(
        ssid: String, password: String, type: Cipher
    ): WifiConfiguration {
        // 初始化Wifi Configuration
        val config = WifiConfiguration()
        config.allowedAuthAlgorithms.clear()
        config.allowedGroupCiphers.clear()
        config.allowedKeyManagement.clear()
        config.allowedPairwiseCiphers.clear()
        config.allowedProtocols.clear()

        // 指定对应的SSID
        config.SSID = "\"" + ssid + "\""

        // 如果之前有类似的配置
        val tempConfig = wifiManager.configuredNetworks.singleOrNull { it.SSID == "\"$ssid\"" }
        if (tempConfig != null) {
            //则清除旧有配置  不是自己创建的network 这里其实是删不掉的
            wifiManager.removeNetwork(tempConfig.networkId)
            wifiManager.saveConfiguration()
        }

        //不需要密码的场景
        when (type) {
            Cipher.WIFI_CIPHER_NO_PASS -> {
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                //以WEP加密的场景
            }

            Cipher.WIFI_CIPHER_WEP -> {
                config.hiddenSSID = true
                config.wepKeys[0] = "\"" + password + "\""
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                config.wepTxKeyIndex = 0
            }

            Cipher.WIFI_CIPHER_WPA -> {
                config.preSharedKey = "\"" + password + "\""
                config.hiddenSSID = true
                config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
                config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                config.status = WifiConfiguration.Status.ENABLED
            }
        }
        return config
    }

    private fun getCipherType(capabilities: String): Cipher {
        return when {
            capabilities.contains("WEP") -> {
                Cipher.WIFI_CIPHER_WEP
            }

            capabilities.contains("WPA") -> {
                Cipher.WIFI_CIPHER_WPA
            }

            else -> {
                Cipher.WIFI_CIPHER_NO_PASS
            }
        }
    }
}
