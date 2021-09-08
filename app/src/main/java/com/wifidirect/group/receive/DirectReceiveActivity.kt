package com.wifidirect.group.receive

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.king.zxing.util.CodeUtils
import com.wifidirect.group.R
import com.wifidirect.group.utils.FileUtil
import com.wifidirect.group.utils.ToolKit
import com.wifidirect.group.utils.ToolKit.dp
import kotlinx.android.synthetic.main.activity_direct_receive.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress

class DirectReceiveActivity: AppCompatActivity() {

    private lateinit var mP2pManager: WifiP2pManager
    private lateinit var mChannel: WifiP2pManager.Channel
    private lateinit var mDirectReceive: DirectReceiveReceiver
    private lateinit var mIntentFilter: IntentFilter

    private var createGroupInitSuccess = false
    private var createGroupSuccess = false

    private var imgPath: String? = null

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_direct_receive)
        title = "WifiDirectGroup接收端"

        initView()
    }

    private fun initView() {
        mP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        mChannel = mP2pManager.initialize(this, mainLooper, null)

        unregisterReceiver()
        mDirectReceive = DirectReceiveReceiver(this, mP2pManager, mChannel)
        mIntentFilter = IntentFilter()
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        registerReceiver(mDirectReceive, mIntentFilter)

        btnSendToClient.setOnClickListener {
            if (serverSocket != null) {
                Thread(Runnable {
                    try {
                        if (clientSocket!!.isConnected) {
                            if (!imgPath.isNullOrEmpty()) {
                                val os = clientSocket!!.getOutputStream()
                                val dos = DataOutputStream(os)
                                val fileIs = FileInputStream(File(imgPath!!))
                                val bis = BufferedInputStream(fileIs)
                                var len: Int
                                val buffer = ByteArray(1024)
                                while ((bis.read(buffer).also { len = it }) != -1) {
                                    Log.d("chenyf", " buffer.size = ${buffer.size}")
                                    if (len > 0) {
                                        dos.write(buffer)
                                    }
                                }
                            } else if (editText.text.isNotEmpty()) {
                                val os = clientSocket!!.getOutputStream()
                                val dos = DataOutputStream(os)
                                dos.writeUTF(editText.text.toString())
                            } else {
                                ToolKit.showToast(this, "请先输入内容..")
                            }
                        } else {
                            ToolKit.showToast(this, "接收端：已断开，请返回重新连接..")
                            finish()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }).start()
            } else {
                ToolKit.showToast(this, "等待发送端连接中..")
            }
        }

        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*";
            startActivityForResult(intent, 0x10)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0x10) {
            data?.data?.let {
                imgPath = FileUtil.getPath(this, it)
                imgPath?.let { path ->
                    val bitmap = BitmapFactory.decodeFile(path)
                    bitmap?.let { bmp ->
                        imageView.setImageBitmap(bmp)
                    }
                }
            }
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
            unregisterReceiver(mDirectReceive)
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
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            clientSocket = null
            serverSocket = null
        }
        createGroupInitSuccess = false
    }

    class DirectReceiveReceiver(
            private val hostActivity: DirectReceiveActivity?,
            private val p2pManager: WifiP2pManager?,
            private val channel: WifiP2pManager.Channel?): BroadcastReceiver() {

        constructor(): this(null, null, null)

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when(intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.d("chenyf", "p2pStateChange, state = $state")
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        p2pManager?.discoverPeers(channel, null)
                        p2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                Log.d("chenyf", "createGroup success..")
                                hostActivity?.createGroupInitSuccess = true
                                hostActivity?.createGroupSuccess = false
                            }

                            override fun onFailure(reason: Int) {
                                Log.d("chenyf", "createGroup onFailure reason = $reason")
                                hostActivity?.createGroupInitSuccess = false
                                hostActivity?.createGroupSuccess = false
                                hostActivity?.connectRest()
                                hostActivity?.initView()
                            }
                        })
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    hostActivity?.let {
                        if (it.createGroupInitSuccess && !it.createGroupSuccess) {
                            val networkInfo: NetworkInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                            networkInfo?.let { info ->
                                Log.d("chenyf", "isConnected = ${info.isConnected}")
                                if (info.isConnected) {
                                    val wifiP2pInfo: WifiP2pInfo? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                                    val serverAddress = wifiP2pInfo?.groupOwnerAddress?.hostAddress
                                    Log.d("chenyf", "$serverAddress")

                                    val wifiP2pGroup: WifiP2pGroup? = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                                    Log.d("chenyf", "isGroupOwner = ${wifiP2pGroup?.isGroupOwner}, " +
                                            "networkName = ${wifiP2pGroup?.networkName}, networkPassword = ${wifiP2pGroup?.passphrase}, " +
                                            "address = ${wifiP2pGroup?.owner?.deviceAddress}")

                                    val bitmap = it.createQRCode(wifiP2pGroup?.networkName, wifiP2pGroup?.passphrase,
                                            wifiP2pGroup?.owner?.deviceAddress, serverAddress)
                                    if (bitmap != null) {
                                        it.ivQRCode.setImageBitmap(bitmap)
                                        it.tvWifiDirectSSid.text = "WifiDirect名称：${wifiP2pGroup?.networkName}"
                                        it.tvPassword.text = "密码：${wifiP2pGroup?.passphrase}"
                                        it.textView.text = "wifiDirect群组创建完成.."
                                        it.progressBar.visibility = View.GONE

                                        it.createListener()

                                        it.createGroupSuccess = true
                                    } else {
                                        Log.d("chenyf", "二维码生成失败")
                                    }
                                } else {
                                    Log.d("chenyf", "please wait..")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createQRCode(ssid: String?, password: String?, deviceAddress: String?, serverAddress: String?): Bitmap? {
        val screenWidth = resources.displayMetrics.widthPixels
        val width = screenWidth - 60.dp * 2
        val map = mutableMapOf<String, String?>()
        map["ssid"] = ssid
        map["password"] = password
        map["deviceAddress"] = deviceAddress
        map["serverAddress"] = serverAddress
        val json = Gson().toJson(map)
        return CodeUtils.createQRCode(json, width)
    }

    private fun createListener() {
        Thread(Runnable {
            try {
                if (serverSocket == null) {
                    serverSocket = ServerSocket()
                    serverSocket!!.reuseAddress = true
                    serverSocket!!.bind(InetSocketAddress(65432))
                    clientSocket = serverSocket!!.accept()
                    if (clientSocket != null) {
                        runOnUiThread {
                            btnSendToClient.text = "向发送端发消息（已连接上）"
                        }
                    }
                    val remoteIP = clientSocket!!.localAddress.hostAddress
                    val remotePort = clientSocket!!.localPort
                    Log.d("chenyf", "remoteIP = $remoteIP, remotePort = $remotePort")
                    while (true) {
                        val stream = clientSocket?.getInputStream()
                        stream?.let { st ->
                            val dis = DataInputStream(st)
                            val s = dis.readUTF()
                            Log.d("chenyf", "server接收到消息 s = $s")
                            runOnUiThread {
                                ToolKit.showToast(this@DirectReceiveActivity, s)
                            }
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
            //btnSendToClient.text = "向接收端发消息（未连接）"
            //connectRest()
            //initView()
            finish()
        }
    }
}