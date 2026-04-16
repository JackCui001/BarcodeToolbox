package com.jackcui.barcodetoolbox

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationListenerCompat
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast

class LocationHelper(context: Context) {
    private val locManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val appContext = context.applicationContext

    // 用来存储最优的结果
    private var bestLocation: Location? = null

    companion object {
        const val TAG = "LocationHelper"
        const val PERMISSION = android.Manifest.permission.ACCESS_FINE_LOCATION
    }

    /**
     * 判断定位服务是否开启
     */
    private fun isLocationServiceEnabled(): Boolean {
        val gps = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        return gps || network
    }

    @SuppressLint("MissingPermission")
    fun getLocationInfo(callback: (Location) -> Unit) {
        if (!isLocationServiceEnabled()) {
            showErrorToast("请打开定位服务")
            return
        }

        val providers = locManager.getProviders(true)
        for (provider in providers) {
            locManager.requestSingleUpdate(provider, object : LocationListenerCompat {
                override fun onLocationChanged(location: Location) {
                    val currentBest = bestLocation
                    if (currentBest == null || location.accuracy < currentBest.accuracy) {
                        bestLocation = location
                        callback(location)
                    }
                }
            }, null)
            Log.i(TAG, "getLocationInfo: 正在尝试刷新定位")
        }
    }
}