package com.example.adb

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.data.PreferencesManager
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * AdbManager manages Wireless ADB pairing, persistent RSA key storage,
 * dynamic mDNS port discovery, and resilient shell execution for:
 * `adb shell cmd statusbar collapse`
 *
 * Implements persistent key storage (like Brevent/黑阈) so that the device
 * needs to be paired only ONCE and remains authorized semi-permanently.
 */
class AdbManager private constructor(private val appContext: Context) {
    private val prefs = PreferencesManager.getInstance(appContext)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val certFile = File(appContext.filesDir, "adb_cert.bin")
    private val keyFile = File(appContext.filesDir, "adb_key.bin")

    private val _isPaired = MutableStateFlow(prefs.config.value.adbPaired || hasSavedKey())
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isSearchingPairing = MutableStateFlow(false)
    val isSearchingPairing: StateFlow<Boolean> = _isSearchingPairing.asStateFlow()

    private val _discoveredPairingPort = MutableStateFlow<Int?>(null)
    val discoveredPairingPort: StateFlow<Int?> = _discoveredPairingPort.asStateFlow()

    private val _discoveredConnectPort = MutableStateFlow<Int?>(null)
    val discoveredConnectPort: StateFlow<Int?> = _discoveredConnectPort.asStateFlow()

    private val _statusMessage = MutableStateFlow("待命")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private var activeKadb: Kadb? = null
    private var activeConnectPort: Int = prefs.config.value.adbConnectPort

    private val nsdManager by lazy {
        appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        // 1. Initialize permanent RSA key pair on disk
        initPersistentKey()

        // 2. Register Wi-Fi / Network callback for continuous reconnection
        registerNetworkCallback()

        // 3. If previously paired or key exists, start connect discovery and auto-authorize
        if (_isPaired.value) {
            scope.launch {
                startConnectDiscovery()
                autoAuthorize()
            }
        }
    }

    /**
     * Permanent RSA Key Pair Storage (Semi-permanent authorization like Brevent)
     * Loads existing key from app private storage, or generates and saves it permanently.
     */
    private fun initPersistentKey() {
        try {
            if (certFile.exists() && keyFile.exists() && certFile.length() > 0 && keyFile.length() > 0) {
                val certBytes = certFile.readBytes()
                val keyBytes = keyFile.readBytes()
                KadbCert.set(certBytes, keyBytes)
                Log.i(TAG, "Loaded persistent ADB key pair (${keyBytes.size} bytes)")
            } else {
                // Generate permanent key pair and write to disk
                val pair = KadbCert.get()
                certFile.writeBytes(pair.first)
                keyFile.writeBytes(pair.second)
                Log.i(TAG, "Generated and saved new persistent ADB key pair")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing persistent ADB key", e)
        }
    }

    fun hasSavedKey(): Boolean {
        return certFile.exists() && keyFile.exists() && certFile.length() > 0 && keyFile.length() > 0
    }

    private fun registerNetworkCallback() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "Network available, ensuring ADB connect discovery")
                    if (_isPaired.value) {
                        startConnectDiscovery()
                        scope.launch {
                            tryAutoConnect()
                        }
                    }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    /**
     * Start mDNS discovery for Wireless Debugging pairing (_adb-tls-pairing._tcp)
     */
    fun startPairingDiscovery(onPortFound: ((Int) -> Unit)? = null) {
        if (_isSearchingPairing.value) return
        stopPairingDiscovery()
        _isSearchingPairing.value = true
        _statusMessage.value = "正在搜索无线调试配对端口…"

        pairingDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Pairing discovery started: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Pairing service found: ${serviceInfo.serviceName}, type: ${serviceInfo.serviceType}")
                resolvePairingService(serviceInfo, onPortFound)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Pairing service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Pairing discovery stopped: $serviceType")
                _isSearchingPairing.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Pairing discovery start failed: $errorCode")
                _isSearchingPairing.value = false
                _statusMessage.value = "搜索服务失败(错误码:$errorCode)"
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Pairing discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE_PAIRING,
                NsdManager.PROTOCOL_DNS_SD,
                pairingDiscoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting pairing discovery", e)
            _isSearchingPairing.value = false
        }
    }

    private fun resolvePairingService(serviceInfo: NsdServiceInfo, onPortFound: ((Int) -> Unit)?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                nsdManager.registerServiceInfoCallback(
                    serviceInfo,
                    { it.run() },
                    object : NsdManager.ServiceInfoCallback {
                        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                            Log.w(TAG, "Service info callback failed: $errorCode, fallback resolve")
                            legacyResolvePairing(serviceInfo, onPortFound)
                        }

                        override fun onServiceUpdated(info: NsdServiceInfo) {
                            val port = info.port
                            if (port > 0) {
                                handlePairingPortFound(port, onPortFound)
                            }
                        }

                        override fun onServiceLost() {}
                        override fun onServiceInfoCallbackUnregistered() {}
                    }
                )
            } else {
                legacyResolvePairing(serviceInfo, onPortFound)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving pairing service", e)
            legacyResolvePairing(serviceInfo, onPortFound)
        }
    }

    private fun legacyResolvePairing(serviceInfo: NsdServiceInfo, onPortFound: ((Int) -> Unit)?) {
        try {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Legacy resolve failed: $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo) {
                    val port = info.port
                    if (port > 0) {
                        handlePairingPortFound(port, onPortFound)
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "ResolveService exception", e)
        }
    }

    private fun handlePairingPortFound(port: Int, onPortFound: ((Int) -> Unit)?) {
        _discoveredPairingPort.value = port
        _statusMessage.value = "已发现配对端口: $port"
        Log.i(TAG, "Successfully found pairing port: $port")
        onPortFound?.invoke(port)
    }

    fun stopPairingDiscovery() {
        pairingDiscoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping pairing discovery", e)
            }
            pairingDiscoveryListener = null
        }
        _isSearchingPairing.value = false
    }

    /**
     * Start mDNS discovery for Wireless Debugging connection (_adb-tls-connect._tcp)
     * Keeps listening to handle dynamic port reassignments.
     */
    fun startConnectDiscovery() {
        if (connectDiscoveryListener != null) return
        connectDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Connect discovery started: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Connect service found: ${serviceInfo.serviceName}")
                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "Connect resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val port = info.port
                            if (port > 0) {
                                _discoveredConnectPort.value = port
                                activeConnectPort = port
                                prefs.setAdbConnectPort(port)
                                Log.i(TAG, "Discovered active connect port: $port")
                                scope.launch {
                                    connect(port)
                                }
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving connect service", e)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Connect service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Connect discovery stopped: $serviceType")
                connectDiscoveryListener = null
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Connect discovery start failed: $errorCode")
                connectDiscoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Connect discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE_CONNECT,
                NsdManager.PROTOCOL_DNS_SD,
                connectDiscoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting connect discovery", e)
        }
    }

    /**
     * Pair with device using wireless debugging pairing code (SPAKE2 + TLS)
     */
    suspend fun pair(port: Int, code: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            _statusMessage.value = "正在配对端口 $port …"
            Log.i(TAG, "Starting Kadb.pair on 127.0.0.1:$port with code: $code")
            Kadb.pair("127.0.0.1", port, code.trim())

            // Ensure key is saved permanently
            try {
                val pair = KadbCert.getOrError()
                certFile.writeBytes(pair.first)
                keyFile.writeBytes(pair.second)
            } catch (ignored: Exception) {}

            prefs.setAdbPaired(true)
            _isPaired.value = true
            _statusMessage.value = "配对成功！已获半永久ADB授权"
            Log.i(TAG, "Pairing successful! Key permanently saved.")
            stopPairingDiscovery()

            // After pairing, connect immediately using connect discovery
            startConnectDiscovery()
            tryAutoConnect()
            Result.success("配对成功")
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed", e)
            _statusMessage.value = "配对失败: ${e.localizedMessage ?: "未知错误"}"
            Result.failure(e)
        }
    }

    /**
     * Connect to the local adbd daemon using the permanent saved RSA key
     */
    suspend fun connect(port: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            _statusMessage.value = "正在连接 127.0.0.1:$port …"
            val kadb = Kadb.create("127.0.0.1", port)
            val res = kadb.shell("echo ok")
            if (res.output.contains("ok")) {
                activeKadb?.close()
                activeKadb = kadb
                activeConnectPort = port
                prefs.setAdbConnectPort(port)
                _isConnected.value = true
                _statusMessage.value = "ADB已连接 (127.0.0.1:$port)"
                Log.i(TAG, "Connected to ADB on port $port")
                Result.success("连接成功 (端口 $port)")
            } else {
                kadb.close()
                _isConnected.value = false
                _statusMessage.value = "ADB回显失败，等待端口重新就绪"
                Result.failure(Exception("ADB 回显验证失败"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Connection failed on port $port: ${e.message}")
            _isConnected.value = false
            Result.failure(e)
        }
    }

    /**
     * Auto-acquire authorization using the saved persistent key without needing a pairing code.
     * Tries: discovered port -> saved port -> 5555 fallback -> triggers discovery.
     */
    suspend fun autoAuthorize(): Result<String> = withContext(Dispatchers.IO) {
        _statusMessage.value = "正在使用已有Key自动获取授权…"
        startConnectDiscovery()

        // 1. Try discovered port if available
        val discovered = _discoveredConnectPort.value
        if (discovered != null && discovered > 0) {
            val res = connect(discovered)
            if (res.isSuccess) return@withContext res
        }

        // 2. Try saved port
        if (activeConnectPort > 0 && activeConnectPort != discovered) {
            val res = connect(activeConnectPort)
            if (res.isSuccess) return@withContext res
        }

        // 3. Try standard ADB port 5555
        val fallback = connect(5555)
        if (fallback.isSuccess) return@withContext fallback

        _statusMessage.value = "已启动端口监听，开启无线调试后将自动连接"
        Result.failure(Exception("未检测到运行中的无线调试端口，请确保无线调试已开启"))
    }

    private suspend fun tryAutoConnect() {
        val connectPort = _discoveredConnectPort.value ?: activeConnectPort
        if (connectPort > 0) {
            connect(connectPort)
        } else {
            connect(5555)
        }
    }

    /**
     * Pre-warms the ADB connection when QS tile is expanded
     */
    fun ensureConnected() {
        if (!_isConnected.value) {
            scope.launch {
                autoAuthorize()
            }
        }
    }

    /**
     * Execute `adb shell cmd statusbar collapse` with self-healing connection retry.
     * User requirement:
     * "ADB权限授权成功后，只执行点按熄屏磁贴后的收起控制中心的指令：adb shell cmd statusbar collapse。"
     */
    fun executeCollapseStatusBar(): Boolean {
        scope.launch(Dispatchers.IO) {
            try {
                var kadb = activeKadb
                if (kadb == null || !kadb.connectionCheck()) {
                    val port = _discoveredConnectPort.value ?: activeConnectPort
                    if (port > 0) {
                        connect(port)
                    } else {
                        connect(5555)
                    }
                    kadb = activeKadb
                }

                if (kadb != null && kadb.connectionCheck()) {
                    val response = kadb.shell("cmd statusbar collapse")
                    Log.i(TAG, "Executed [cmd statusbar collapse], result: ${response.output}")
                    _statusMessage.value = "已通过无线ADB收起控制中心"
                    _isConnected.value = true
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "First attempt failed, re-establishing fresh socket connection...", e)
            }

            // Fallback retry with fresh socket
            try {
                activeKadb?.close()
                activeKadb = null
                val port = _discoveredConnectPort.value ?: activeConnectPort
                if (port > 0) {
                    val freshKadb = Kadb.create("127.0.0.1", port)
                    val res = freshKadb.shell("cmd statusbar collapse")
                    Log.i(TAG, "Fresh socket executed [cmd statusbar collapse]: ${res.output}")
                    activeKadb = freshKadb
                    _isConnected.value = true
                    _statusMessage.value = "已通过无线ADB收起控制中心"
                    return@launch
                }
            } catch (retryE: Exception) {
                Log.e(TAG, "Fresh socket retry failed", retryE)
            }

            // Ultimate fallback
            fallbackCollapseNotificationShade(appContext)
        }
        return true
    }

    companion object {
        private const val TAG = "FakeStandbyAdb"
        const val SERVICE_TYPE_PAIRING = "_adb-tls-pairing._tcp."
        const val SERVICE_TYPE_CONNECT = "_adb-tls-connect._tcp."

        @Volatile
        private var instance: AdbManager? = null

        fun getInstance(context: Context): AdbManager {
            return instance ?: synchronized(this) {
                instance ?: AdbManager(context.applicationContext).also { instance = it }
            }
        }

        fun fallbackCollapseNotificationShade(context: Context) {
            try {
                val statusBarService = context.getSystemService("statusbar")
                if (statusBarService != null) {
                    val statusBarClass = Class.forName("android.app.StatusBarManager")
                    val collapseMethod = statusBarClass.getMethod("collapsePanels")
                    collapseMethod.isAccessible = true
                    collapseMethod.invoke(statusBarService)
                    return
                }
            } catch (e: Throwable) {
                Log.d(TAG, "collapsePanels reflection fallback failed", e)
            }
            try {
                @Suppress("DEPRECATION")
                val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                context.sendBroadcast(closeDialogs)
            } catch (ignored: Throwable) {}
        }
    }
}
