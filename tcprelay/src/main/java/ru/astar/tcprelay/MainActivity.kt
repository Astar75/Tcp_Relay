package ru.astar.tcprelay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import ru.astar.tcprelay.ui.theme.TabsFragmentsTheme
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {

    private val preferences by lazy {
        getSharedPreferences("tcprelay_settings", Context.MODE_PRIVATE)
    }

    private var permissionResult: (Boolean) -> Unit = {}
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted ->
        permissionResult(permissionGranted)
    }

    private var outputCallback: (String) -> Unit = {}

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == TcpService.ACTION_OUTPUT) {
                outputCallback.invoke("${intent.getStringExtra("output")}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        outputCallback = { message ->
            viewModel.addLog(message)
        }

        setContent {
            TabsFragmentsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var notificationPermissionGranted by remember { mutableStateOf(false) }
                    val connected = rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        notificationPermissionGranted = checkNotificationPermission()
                    }

                    if (notificationPermissionGranted) {
                        val params = readParams()

                        MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            inputPortInit = params[0].toIntOrNull() ?: 0,
                            targetPortInit = params[1].toIntOrNull() ?: 0,
                            ipAddressInit = params[2],
                            connected = connected,
                            viewModel = viewModel,
                            onClickConnect = { inputPort, targetPort, ipAddress ->
                                saveParams(inputPort, targetPort, ipAddress)
                                if (!connected.value) {
                                    val intent =
                                        Intent(this@MainActivity, TcpService::class.java).apply {
                                            putExtra("input_port", inputPort)
                                            putExtra("target_port", targetPort)
                                            putExtra("target_ip", ipAddress)
                                        }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(intent)
                                        connected.value = true
                                    }
                                } else {
                                    stopService(Intent(this@MainActivity, TcpService::class.java))
                                    connected.value = false
                                }
                            }
                        )
                    } else {
                        Text(
                            modifier = Modifier.padding(innerPadding),
                            text = "Предоставьте разрешение на отправку уведомлений"
                        )
                    }
                }
            }
        }
    }

    private fun saveParams(inputPort: Int, targetPort: Int, ipAddress: String) {
        preferences.edit()
            .putInt("input_port", inputPort)
            .putInt("target_port", targetPort)
            .putString("target_ip", ipAddress)
            .apply()
    }

    private fun readParams(): Array<String> {
        val array = Array(3) { "" }
        array[0] = preferences.getInt("input_port", 0).toString()
        array[1] = preferences.getInt("target_port", 0).toString()
        array[2] = preferences.getString("target_ip", "") ?: ""
        return array
    }

    private suspend fun checkNotificationPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        return suspendCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionResult = { continuation.resume(it) }
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                continuation.resume(true)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(TcpService.ACTION_OUTPUT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    connected: State<Boolean>,
    onClickConnect: ((Int, Int, String) -> Unit)? = null,
    viewModel: MainViewModel,
    inputPortInit: Int,
    targetPortInit: Int,
    ipAddressInit: String,
) {
    var inputPort by remember { mutableIntStateOf(inputPortInit) }
    var targetPort by remember { mutableIntStateOf(targetPortInit) }
    var ipAddress by remember { mutableStateOf(ipAddressInit) }

    val logs by viewModel.logsFlow.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TextField(
            label = { Text(text = "Входящий порт") },
            modifier = Modifier.fillMaxWidth(),
            value = inputPort.toString(), onValueChange = { value ->
                inputPort = value.toIntOrNull() ?: 0
            }
        )
        TextField(
            label = { Text(text = "Порт для переадресации") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            value = targetPort.toString(), onValueChange = { value ->
                targetPort = value.toIntOrNull() ?: 0
            }
        )
        IpAddressInput(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp), value = ipAddress
        ) {
            ipAddress = it
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            onClick = {
                onClickConnect?.invoke(inputPort, targetPort, ipAddress)
            }
        ) {
            Text(if (!connected.value) "Запустить" else "Остановить")
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            items(logs.size) { index ->
                Text(text = logs[index], fontSize = 10.sp)
            }
        }

        // Text(text = "Вывод ${output.value}")
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    //MainScreen()
}