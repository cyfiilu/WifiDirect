package com.wifidirect.group.send

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.*
import android.net.wifi.*
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.*
import android.text.format.Formatter
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.gson.Gson
import com.king.zxing.Intents
import com.wifidirect.group.R
import com.wifidirect.group.utils.ToolKit
import kotlinx.android.synthetic.main.actvity_direct_send.*
import kotlinx.android.synthetic.main.actvity_direct_send.editText
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.Socket

class DirectSendActivity: AppCompatActivity() {

    private lateinit var mWifiManager: WifiManager
    private lateinit var mConnManager: ConnectivityManager
    private lateinit var mP2pManager: WifiP2pManager
    private lateinit var mChannel: WifiP2pManager.Channel

    private lateinit var mDirectSend: DirectSendReceiver
    private lateinit var mIntentFilter: IntentFilter

    private var isWifiP2pEnable = false
    private var connectWifiDirect = false

    private var ssid = ""
    private var serverAddress = ""
    private var connectGroupCount = 0

    private var clientSocket: Socket? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.actvity_direct_send)
        title = "WifiDirectGroup发送端"

        initView()
    }

    private fun initView() {
        mConnManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        mWifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        mP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        mChannel = mP2pManager.initialize(this, mainLooper, null)

        unregisterReceiver()
        mDirectSend = DirectSendReceiver(this, mP2pManager, mChannel, mWifiManager)
        mIntentFilter = IntentFilter()
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        registerReceiver(mDirectSend, mIntentFilter)

        btnScanCodeConnect.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0x03)
                } else {
                    startQRScan()
                }
            } else {
                startQRScan()
            }
        }

        btnSendToServer.setOnClickListener {
            if (clientSocket != null) {
                Thread(Runnable {
                    try {
                        val isConnected = clientSocket!!.isConnected
                        if (isConnected) {
                            if (editText.text.isNotEmpty()) {
                                val os = clientSocket!!.getOutputStream()
                                val dos = DataOutputStream(os)
                                dos.writeUTF(editText.text.toString())
                            } else {
                                ToolKit.showToast(this, "请先输入内容..")
                            }
                        } else {
                            ToolKit.showToast(this, "发送端：已断开，请返回重新连接..")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }).start()
            } else {
                ToolKit.showToast(this, "请先扫码连接到接收端..")
            }
        }
    }

    private fun startQRScan() {
        if (isWifiP2pEnable) {
            startActivityForResult(Intent(this, ScanCodeActivity::class.java), 0x02)
        } else {
            ToolKit.showToast(this, "please wait..")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val dialog = AlertDialog.Builder(this)
                    .setTitle("退出将断开连接..")
                    .setPositiveButton("确定") { dialog, which ->
                        dialog.dismiss()
                        finish()
                    }
                    .setNegativeButton("取消") { dialog, which ->
                        dialog.dismiss()
                    }
                    .create()
            dialog.show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectRest()
    }

    private fun unregisterReceiver() {
        try {
            unregisterReceiver(mDirectSend)
        } catch (e: Exception) {
        }
    }

    private fun connectRest() {
        unregisterReceiver()
        mP2pManager.cancelConnect(mChannel, null)
        mP2pManager.removeGroup(mChannel, null)
        mChannel.close()
        try {
            clientSocket?.shutdownInput()
            clientSocket?.shutdownOutput()
            clientSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            clientSocket = null
        }
    }

    class DirectSendReceiver(
            private val hostActivity: DirectSendActivity?,
            private val p2pManager: WifiP2pManager?,
            private val channel: WifiP2pManager.Channel?,
            private val wifiManager: WifiManager?): BroadcastReceiver() {

        constructor(): this(null, null, null, null)

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.d("chenyf", "p2pStateChange, state = $state")
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        hostActivity?.isWifiP2pEnable = true
                        p2pManager?.discoverPeers(channel, null)
                    } else if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        hostActivity?.isWifiP2pEnable = false
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                    Log.d("chenyf", "isConnected = ${networkInfo?.isConnected}")
                    networkInfo?.let {
                        if (it.isConnected) {
                            val wifiP2pInfo: WifiP2pInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                            val serverAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
                            Log.d("chenyf", "$serverAddress")

                            val wifiP2pGroup: WifiP2pGroup? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                            Log.d("chenyf", "isGroupOwner = ${wifiP2pGroup?.isGroupOwner}, " +
                                    "networkName = ${wifiP2pGroup?.networkName}, networkPassword = ${wifiP2pGroup?.passphrase}")

                            serverAddress?.let { address ->
                                hostActivity?.startConnect(address)
                            }
                        }
                    }
                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    hostActivity?.let { act ->
                        val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
                        networkInfo?.let { info ->
                            if (info.type == ConnectivityManager.TYPE_WIFI) {
                                when (info.state) {
                                    NetworkInfo.State.CONNECTED -> {
                                        wifiManager?.connectionInfo?.let {
                                            if (!act.connectWifiDirect) {
                                                val address = Formatter.formatIpAddress(it.ipAddress)
                                                Log.d("chenyf", "wifiDirect 连接上 address = $address, ${it.ssid}, ${it.networkId}")
                                                act.connectWifiDirect = true
                                                if (it.ssid.contains(act.ssid) && !act.serverAddress.isNullOrBlank()) {
                                                    act.startConnect(act.serverAddress)
                                                }
                                            }
                                        }
                                    }
                                    NetworkInfo.State.DISCONNECTED -> {
                                        if (act.connectWifiDirect) {
                                            Log.d("chenyf", "wifiDirect 断开连接")
                                            act.connectWifiDirect = false
                                        }
                                    }
                                    else -> {
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0x02 && resultCode == RESULT_OK) {
            try {
                val result = data?.getStringExtra(Intents.Scan.RESULT)
                result?.let {
                    val map = Gson().fromJson(it, MutableMap::class.java)
                    ssid = map["ssid"].toString()
                    val password = map["password"].toString()
                    val deviceAddress = map["deviceAddress"].toString()
                    serverAddress = map["serverAddress"].toString()
                    if (deviceAddress == "02:00:00:00:00:00") {
                        connectWifiDirect(ssid, password)
                    } else {
                        connectGroupCount = 0
                        connectDirectWifi(ssid, password, deviceAddress)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDirectWifi(ssid: String, pass: String, address: String) {
        val wifiP2pConfig = WifiP2pConfig()
        wifiP2pConfig.deviceAddress = address
        mP2pManager.connect(mChannel, wifiP2pConfig, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("chenyf", "connectGroup success")

            }

            override fun onFailure(reason: Int) {
                Log.d("chenyf", "connectGroup fail reason = $reason")
                if (connectGroupCount < 3) {
                    connectGroupCount++
                    connectRest()
                    initView()
                    Handler(Looper.getMainLooper()).postDelayed({
                        connectDirectWifi(ssid, pass, address)
                    }, 2000)
                }
            }
        })
    }

    private fun startConnect(address: String) {
        Thread(Runnable {
            try {
                Thread.sleep(2000)
                if (clientSocket == null) {
                    clientSocket = Socket(address, 65432)
                    Log.d("chenyf", "isConnected = ${clientSocket!!.isConnected}")
                    if (clientSocket!!.isConnected) {
                        runOnUiThread {
                            btnSendToServer.text = "向接收端发消息（已连接上）"
                        }
                    }

                    val buffer = ByteArray(1024)
                    val byteList = mutableListOf<Byte>()
                    while (true) {
                        val stream = clientSocket?.getInputStream()
                        stream?.let { st ->
                            val dis = DataInputStream(st)
                            val value = dis.read(buffer)
                            Log.d("chenyf", "client接收到消息 flag = $value")
                            if (value > 0) {
                                byteList.addAll(buffer.toMutableList())
                                Log.d("chenyf", "client接收到消息 flag = ${buffer.size}")
                            } else {
                                val allBytes = byteList.toByteArray()
                                Log.d("chenyf", "client接收到消息 flag = ${allBytes.size}")
                                val root = Environment.getExternalStorageDirectory().absolutePath
                                val file = File("$root/test.jpeg")
                                val fos = FileOutputStream(file)
                                fos.write(allBytes)
                                fos.flush()
                            }

//                            val s = dis.readUTF()
//                            Log.d("chenyf", "client接收到消息 s = ")
//                            runOnUiThread {
//                                ToolKit.showToast(this@DirectSendActivity, s)
//                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                socketDisconnect()
            }
        }).start()
    }

    private fun socketDisconnect() {
        runOnUiThread {
            //btnSendToServer.text = "向接收端发消息（未连接）"
            //connectRest()
            //initView()
            finish()
        }
    }

    private fun connectWifiDirect(ssid: String, pass: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier: NetworkSpecifier = WifiNetworkSpecifier.Builder()
                    .setSsidPattern(PatternMatcher(ssid, PatternMatcher.PATTERN_PREFIX))
                    .setWpa2Passphrase(pass)
                    .build()
            val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI) //创建的是WIFI网络。
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) //网络不受限
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED) //信任网络，增加这个连个参数让设备连接wifi之后还联网。
                    .setNetworkSpecifier(specifier)
                    .build()
            mConnManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("chenyf", "连接hotspot成功")
                    val address = getWifiRouteIPAddress()
                    if (!serverAddress.isNullOrBlank()) {
                        startConnect(serverAddress)
                    }
                }

                override fun onUnavailable() {
                    Log.d("chenyf", "连接hotspot失败")
                }
            })
        } else {
            val capabilities = getWifiDirectType(ssid)
            Log.d("chenyf", "capabilities = $capabilities")
            val config = createWifiCfg(ssid, pass, capabilities)
            val netId = mWifiManager.addNetwork(config)
            Log.d("chenyf", "netId = $netId")
            if (netId > 0) {
                val enable = mWifiManager.enableNetwork(netId, true)
                Log.d("chenyf", "enable = $enable")
                val address = getWifiRouteIPAddress()
                if (!serverAddress.isNullOrBlank()) {
                    startConnect(serverAddress)
                }
            }
        }
    }

    private fun getWifiDirectType(ssid: String): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val networkList: List<ScanResult>? = wifiManager.scanResults
        if (networkList != null) {
            for (network in networkList) {
                if (ssid == network.SSID) {
                    Log.d("chenyf", network.SSID.toString() + " capabilities : " + network.capabilities)
                    return network.capabilities
                }
            }
        }
        return ""
    }

    private fun createWifiCfg(ssid: String, password: String, capabilities: String): WifiConfiguration {
        val wifiConfig = WifiConfiguration()
        wifiConfig.allowedAuthAlgorithms.clear()
        wifiConfig.allowedGroupCiphers.clear()
        wifiConfig.allowedKeyManagement.clear()
        wifiConfig.allowedPairwiseCiphers.clear()
        wifiConfig.allowedProtocols.clear()
        wifiConfig.SSID = "\"" + ssid + "\""

        val tempConfig: WifiConfiguration? = isExists(ssid)
        tempConfig?.let {
            val removeNetwork = mWifiManager.removeNetwork(it.networkId)
            Log.d("chenyf", "existNetworkId = ${it.networkId}, removeNetwork = $removeNetwork")
        }

        if (capabilities.contains("WPA")) {
            wifiConfig.preSharedKey = "\"" + password + "\""
            wifiConfig.hiddenSSID = true
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
            //wifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
            wifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
            wifiConfig.status = WifiConfiguration.Status.ENABLED
        } else if (capabilities.contains("WEP")) {
            wifiConfig.hiddenSSID = true
            wifiConfig.wepKeys[0] = "\"" + password + "\""
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED)
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40)
            wifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104)
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            wifiConfig.wepTxKeyIndex = 0
        } else {
            wifiConfig.wepKeys[0] = ""
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
            wifiConfig.wepTxKeyIndex = 0
        }
        return wifiConfig
    }

    @SuppressLint("MissingPermission")
    private fun isExists(SSid: String): WifiConfiguration? {
        val existingConfigs: List<WifiConfiguration> = mWifiManager.configuredNetworks
        for (existingConfig in existingConfigs) {
            if (existingConfig.SSID == "\"" + SSid + "\"") {
                return existingConfig
            }
        }
        return null
    }

    private fun getWifiRouteIPAddress(): String {
        val dhcpInfo = mWifiManager.dhcpInfo
        val routeIp: String = Formatter.formatIpAddress(dhcpInfo.gateway)
        Log.i("chenyf", "routeIp = $routeIp")
        return routeIp
    }
}