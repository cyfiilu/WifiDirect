package com.wifidirect.group.utils

import android.content.Context
import android.content.res.Resources
import android.location.LocationManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Looper
import android.util.TypedValue
import android.widget.Toast
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method


object ToolKit {

    val Float.dp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this,
            Resources.getSystem().displayMetrics
        )

    val Float.sp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this,
            Resources.getSystem().displayMetrics
        )

    val Int.dp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()

    val Int.sp
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            this.toFloat(),
            Resources.getSystem().displayMetrics
        ).toInt()

    fun showToast(context: Context, resId: Int, duration: Int = Toast.LENGTH_SHORT) {
        if (isMainThread()) {
            Toast.makeText(context, resId, duration).show()
        } else {
            Looper.prepare()
            Toast.makeText(context, resId, duration).show()
            Looper.loop()
        }
    }

    fun showToast(context: Context, msg: String, duration: Int = Toast.LENGTH_SHORT) {
        if (isMainThread()) {
            Toast.makeText(context, msg, duration).show()
        } else {
            Looper.prepare()
            Toast.makeText(context, msg, duration).show()
            Looper.loop()
        }
    }

    private fun isMainThread(): Boolean {
        return Looper.getMainLooper().thread === Thread.currentThread()
    }

    /**
     * 位置服务开关是否打开
     */
    fun isLocationServiceEnable(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * wifi开关是否打开
     */
    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
        if (wifiManager is WifiManager) {
            return wifiManager.isWifiEnabled
        }
        return false
    }

    /**
     * 打开或关闭wifi
     */
    fun openOrCloseWifi(context: Context, isWifiEnabled: Boolean) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
        if (wifiManager is WifiManager) {
            wifiManager.isWifiEnabled = isWifiEnabled
        }
    }

    fun isHotspotOpen(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        try {
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            return method.invoke(wifiManager) as Boolean
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return false
    }

    fun closeHotspot(context: Context) {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val method: Method = wifiManager.javaClass.getMethod("getWifiApConfiguration")
            method.isAccessible = true
            val config = method.invoke(wifiManager) as WifiConfiguration
            val method2: Method = wifiManager.javaClass.getMethod(
                "setWifiApEnabled",
                WifiConfiguration::class.java,
                Boolean::class.javaPrimitiveType
            )
            method2.invoke(wifiManager, config, false)
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        }
    }
}