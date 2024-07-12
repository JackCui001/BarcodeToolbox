package com.jackcui.barcodetoolbox

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.location.LocationListenerCompat
import com.jackcui.barcodetoolbox.MainActivity.Companion.showErrorToast

class LocationUtils(context: Context) {
    private val locManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val mContext = context

    // 用来存储最优的结果
    var bestLocation: Location? = null

    companion object {
        const val TAG = "LocationUtils"
        const val PERMISSION = android.Manifest.permission.ACCESS_FINE_LOCATION
    }

    /**
     * 判断定位服务是否开启
     */
    private fun isLocationServiceEnabled(): Boolean {
        val gps = locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val network = locManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        // 有一个开启就可
        return gps || network
    }

    @SuppressLint("MissingPermission")
    fun getLocationInfo(callback: (Location) -> Unit) {
        // 判断是否开启位置服务
        if (isLocationServiceEnabled()) {
            // 获取所有支持的provider
            val providers = locManager.getProviders(true)
            // 尝试刷新定位
            for (provider in providers) {
                locManager.requestSingleUpdate(provider, object : LocationListenerCompat {
                    override fun onLocationChanged(location: Location) {
                        if (bestLocation == null || location.accuracy < bestLocation!!.accuracy) {
                            bestLocation = location
                            callback(bestLocation!!)
                        }
                    }
                }, null)
                Log.i(TAG, "getLocationInfo: 正在尝试刷新定位")
            }
        } else {
            showErrorToast("请打开定位服务")
        }
    }
}