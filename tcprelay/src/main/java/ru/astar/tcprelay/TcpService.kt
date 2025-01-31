package ru.astar.tcprelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException


class TcpService : Service() {

    companion object {
        const val ACTION_OUTPUT = "action_output"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputPort = intent?.getIntExtra("input_port", 0) ?: 0
        val targetPort = intent?.getIntExtra("target_port", 0) ?: 0
        val targetIp = intent?.getStringExtra("target_ip") ?: ""

        if (inputPort > 0 && targetIp.isNotBlank() && targetPort > 0) {
            startRelay(inputPort, targetPort, targetIp)
        }

        val notification = createNotification()
        startForeground(1, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRelay()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startRelay(inputPort: Int, targetPort: Int, targetIp: String) {
        scope.launch {
            try {
                sendOutput("Запуск сервера на порту $inputPort")
                serverSocket = ServerSocket(inputPort).also { isRunning = true }
                sendOutput("Сервер запущен!")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept()
                    clientSocket?.let {
                        handleClient(it, targetPort, targetIp)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                sendOutput("Сервер остановлен ${e.message}")
            } finally {
                stopRelay()
            }
        }
    }

    private fun handleClient(clientSocket: Socket, targetPort: Int, targetIp: String) {
        scope.launch {
            try {
                sendOutput("""Подключение к ${clientSocket.inetAddress}:${clientSocket.port}""")
                val targetSocket = Socket(targetIp, targetPort)

                val clientInput = clientSocket.getInputStream()
                val clientOutput = clientSocket.getOutputStream()
                val targetInput = targetSocket.getInputStream()
                val targetOutput = targetSocket.getOutputStream()

                supervisorScope {
                    val job1 = launch {
                        try {
                            clientInput.copyToWithLogging(targetOutput, "Клиент")
                            // clientInput.copyTo(targetOutput)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            targetOutput.close()
                        }
                    }

                    val job2 = launch {
                        try {
                            targetInput.copyToWithLogging(clientOutput, "Сервер")
                            // targetInput.copyTo(clientOutput)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            clientOutput.close()
                        }
                    }

                    job1.join()
                    job2.join()
                }

                targetSocket.close()
                clientSocket.close()
            } catch (e: IOException) {
                sendOutput("""Ошибка работы сокета: ${e.message}""")
                e.printStackTrace()
            } finally {
                try {
                    clientSocket.close()
                    sendOutput("""Отключение от ${clientSocket.inetAddress}:${clientSocket.port}""")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "tcp_relay_channel"
        val channelName = "TCP Relay Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("TCP Relay работает")
            .setContentText("Служба перенаправления TCP запущена")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopRelay() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        scope.cancel()
    }

    private fun sendOutput(message: String) {
        sendBroadcast(Intent(ACTION_OUTPUT).apply {
            putExtra("output", message)
        })
    }

    private fun InputStream.copyToWithLogging(
        out: OutputStream,
        direction: String,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
    ) {
        val buffer = ByteArray(bufferSize)
        var bytesRead: Int
        while (read(buffer).also { bytesRead = it } >= 0) {
            sendOutput("$direction: ${buffer.copyOfRange(0, bytesRead).hexStr()} ($bytesRead байт)")
            out.write(buffer, 0, bytesRead)
        }
        out.flush()
    }

    private fun ByteArray.hexStr(): String =
        joinToString(separator = " ") { String.format("%02X", it) }
}
