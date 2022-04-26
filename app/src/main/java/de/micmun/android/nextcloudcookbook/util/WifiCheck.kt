package de.micmun.android.nextcloudcookbook.util

import android.content.Context
import android.net.wifi.WifiManager

class WifiCheck {

    companion object {
        fun isConnectedToWifi(context: Context): Boolean {
            val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            if (wifiMgr!!.isWifiEnabled) {
                val wifiInfo = wifiMgr.connectionInfo
                return wifiInfo.networkId != -1
            } else {
                return false
            }
        }
    }
}