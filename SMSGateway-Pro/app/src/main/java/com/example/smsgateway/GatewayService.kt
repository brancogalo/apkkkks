package com.example.smsgateway

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GatewayService : Service() {

    private var wsConnection: WebSocket? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var serverUrl = ""
    private var phoneNumber = ""
    private var deviceId = ""

    override fun onCreate() {
        super.onCreate()
        Log.d("GatewayService", "Service criado")
        
        // Obter informações do dispositivo
        phoneNumber = getPhoneNumber()
        deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        
        // Descobrir servidor (UDP broadcast)
        discoverServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "SEND_SMS_TO_SERVER") {
            val sender = intent.getStringExtra("sender")
            val body = intent.getStringExtra("body")
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            
            sendSMSToServer(sender, body, timestamp)
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getPhoneNumber(): String {
        return try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.line1Number ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    private fun discoverServer() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Tentar descobrir via UDP broadcast
                val socket = java.net.DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 5000
                
                val broadcastAddress = java.net.InetAddress.getByName("255.255.255.255")
                val data = "DISCOVER_SMS_GATEWAY".toByteArray()
                val packet = java.net.DatagramPacket(data, data.size, broadcastAddress, 3000)
                
                socket.send(packet)
                
                // Aguardar resposta
                val receiveData = ByteArray(1024)
                val receivePacket = java.net.DatagramPacket(receiveData, receiveData.size)
                
                try {
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    serverUrl = response.trim()
                    Log.d("GatewayService", "Servidor descoberto: $serverUrl")
                    
                    registerDevice()
                } catch (e: Exception) {
                    Log.e("GatewayService", "Timeout na descoberta", e)
                    // Tentar localhost como fallback
                    serverUrl = "ws://192.168.1.100:3000"
                    registerDevice()
                }
                
                socket.close()
            } catch (e: Exception) {
                Log.e("GatewayService", "Erro na descoberta", e)
                serverUrl = "ws://192.168.1.100:3000"
                registerDevice()
            }
        }
    }

    private fun registerDevice() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Converter HTTP para WS se necessário
                val wsUrl = serverUrl
                    .replace("http://", "ws://")
                    .replace("https://", "wss://")
                    .replace(":80", ":3000")
                    .replace(":443", ":3000")
                
                val request = Request.Builder()
                    .url(wsUrl)
                    .build()
                
                val webSocketListener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d("GatewayService", "Conectado ao servidor")
                        wsConnection = webSocket
                        
                        // Registrar dispositivo
                        val registerMsg = JSONObject().apply {
                            put("action", "register")
                            put("phoneNumber", phoneNumber)
                            put("deviceId", deviceId)
                            put("deviceName", Build.MODEL)
                        }
                        
                        webSocket.send(registerMsg.toString())
                    }
                    
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d("GatewayService", "Mensagem recebida: $text")
                    }
                    
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d("GatewayService", "Conexão fechada: $reason")
                        // Reconectar após 5 segundos
                        serviceScope.launch(Dispatchers.IO) {
                            delay(5000)
                            registerDevice()
                        }
                    }
                    
                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        Log.e("GatewayService", "Erro na conexão", t)
                        serviceScope.launch(Dispatchers.IO) {
                            delay(5000)
                            registerDevice()
                        }
                    }
                }
                
                httpClient.newWebSocket(request, webSocketListener)
            } catch (e: Exception) {
                Log.e("GatewayService", "Erro ao conectar", e)
                serviceScope.launch(Dispatchers.IO) {
                    delay(5000)
                    registerDevice()
                }
            }
        }
    }

    private fun sendSMSToServer(sender: String?, body: String?, timestamp: Long) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                if (wsConnection != null) {
                    val smsMsg = JSONObject().apply {
                        put("action", "sms")
                        put("deviceId", deviceId)
                        put("phoneNumber", phoneNumber)
                        put("sender", sender ?: "UNKNOWN")
                        put("body", body ?: "")
                        put("timestamp", timestamp)
                    }
                    
                    wsConnection?.send(smsMsg.toString())
                    Log.d("GatewayService", "SMS enviado para servidor")
                } else {
                    Log.w("GatewayService", "WebSocket não conectado, enfileirando SMS")
                    // Salvar para enviar depois
                    saveSMSLocally(sender, body, timestamp)
                }
            } catch (e: Exception) {
                Log.e("GatewayService", "Erro ao enviar SMS", e)
                saveSMSLocally(sender, body, timestamp)
            }
        }
    }

    private fun saveSMSLocally(sender: String?, body: String?, timestamp: Long) {
        // Salvar em SharedPreferences para enviar depois
        val prefs = getSharedPreferences("sms_queue", Context.MODE_PRIVATE)
        val queue = prefs.getString("queue", "[]")
        val queueArray = org.json.JSONArray(queue)
        
        val smsObject = JSONObject().apply {
            put("sender", sender)
            put("body", body)
            put("timestamp", timestamp)
        }
        
        queueArray.put(smsObject)
        prefs.edit().putString("queue", queueArray.toString()).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        wsConnection?.close(1000, "Service destroyed")
        serviceScope.cancel()
    }
}
