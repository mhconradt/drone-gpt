package com.example.dronegpt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.compose.jetchat.theme.DroneGPTTheme
import com.example.dronegpt.chat.ChatAssistantMessage
import com.example.dronegpt.chat.ChatUserMessage
import com.example.dronegpt.chat.PlainTextMessage
import com.example.dronegpt.chat.isControl
import com.example.dronegpt.fly.Agent
import com.example.dronegpt.fly.StateManager
import com.example.dronegpt.fly.VisionManager
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.RemoteControllerKey
import dji.sdk.keyvalue.value.flightcontroller.FlightControlAuthorityChangeReason
import dji.v5.common.callback.CommonCallbacks.CompletionCallback
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.et.get
import dji.v5.et.listen
import dji.v5.manager.SDKManager
import dji.v5.manager.aircraft.uas.AreaStrategy
import dji.v5.manager.aircraft.uas.UASRemoteIDManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickState
import dji.v5.manager.aircraft.virtualstick.VirtualStickStateListener
import dji.v5.manager.datacenter.camera.CameraStreamManager
import dji.v5.manager.diagnostic.DeviceStatusManager
import dji.v5.manager.interfaces.ICameraStreamManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.utils.common.LocationUtil.getLastLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


enum class Status {
    HEALTHY,
    WARNING,
    UNHEALTHY,
    LOADING
}

data class StatusInfo(
    val status: Status,
    val statusReason: String = "",
    val updatedAt: Instant = Instant.now()
)

private data class InternalStatusInfo(
    val connectedToDrone: Boolean? = null,
    val virtualStickEnabled: Boolean? = null,
) {
    fun deriveStatusInfo(): StatusInfo {
        return if (connectedToDrone === null || virtualStickEnabled === null) {
            StatusInfo(
                Status.LOADING,
                "Waiting to connect..."
            )
        } else if (connectedToDrone && virtualStickEnabled) {
            StatusInfo(
                Status.HEALTHY,
                "All systems go!"
            )
        } else if (connectedToDrone) {
            StatusInfo(
                Status.WARNING,
                "Virtual stick mode disabled."
            )
        } else if (virtualStickEnabled) {
            StatusInfo(
                Status.WARNING,
                "Not connected to drone."
            )
        } else {
            StatusInfo(
                Status.UNHEALTHY,
                "Not able to fly."
            )
        }

    }
}


class HealthViewModel : ViewModel() {
    private val TAG = this::class.java.simpleName

    private var internalStatusInfo = InternalStatusInfo(
        null,
        null
    )

    private val _statusInfo = MutableStateFlow(
        StatusInfo(
            Status.LOADING,
            "Loading..."
        )
    )

    val statusInfo: StateFlow<StatusInfo> = _statusInfo

    fun initialize() {
        VirtualStickManager.getInstance().setVirtualStickStateListener(
            object : VirtualStickStateListener {
                override fun onVirtualStickStateUpdate(stickState: VirtualStickState) {
                    Log.i(
                        TAG,
                        "virtualStickEnabled (new): ${stickState.isVirtualStickEnable}"
                    )
                    internalStatusInfo = internalStatusInfo.copy(
                        virtualStickEnabled = stickState.isVirtualStickEnable
                    )
                    val statusInfoUpdate = internalStatusInfo.deriveStatusInfo()
                    Log.i(
                        TAG,
                        "statusInfo (new): $statusInfoUpdate"
                    )
                    _statusInfo.value = statusInfoUpdate
                }

                override fun onChangeReasonUpdate(reason: FlightControlAuthorityChangeReason) {
                    Log.i(
                        TAG,
                        ""
                    )
                }

            }
        )
        KeyTools.createKey(FlightControllerKey.KeyConnection).listen(
            this,
            false
        ) {
            internalStatusInfo = internalStatusInfo.copy(connectedToDrone = it)
            val statusInfoUpdate = internalStatusInfo.deriveStatusInfo()
            Log.i(
                TAG,
                "statusInfo (new): $statusInfoUpdate"
            )
            _statusInfo.value = statusInfoUpdate
        }
    }
}

data class Size(val width: Int, val height: Int)

@Composable
fun DroneCameraView(modifier: Modifier = Modifier) {
    var surfaceSize by remember {
        mutableStateOf(
            Size(
                0,
                0
            )
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        surfaceSize = Size(
                            width,
                            height
                        )
                        // Now surfaceSize holds the width and height of the surface
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {}

                    override fun surfaceCreated(holder: SurfaceHolder) {}
                })
            }
        },
        update = { surfaceView ->
            // This is where you can update the SurfaceView if needed, based on state changes
            // Pass the Surface to the DJI SDK here
            val surface = surfaceView.holder.surface
            // Example: droneCamera.startStream(surface)
            val cameraStreamManager = CameraStreamManager.getInstance()
            cameraStreamManager.addAvailableCameraUpdatedListener {
                if (it.isNotEmpty()) {
                    cameraStreamManager.putCameraStreamSurface(
                        it[0],
                        surface,
                        surfaceSize.width,
                        surfaceSize.height,
                        ICameraStreamManager.ScaleType.CENTER_INSIDE
                    )
                }
            }
        }
    )
}

@Composable
fun ChatScreen(viewModel: Agent) {
    val messages by viewModel.chatMessages.collectAsState()

    println("ChatScreen rendering")

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(messages.filterIsInstance(PlainTextMessage::class.java)) { message ->
                    MessageCard(
                        msg = message
                    )
                }
            }

            var text by remember { mutableStateOf("") }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Message DroneGPT...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    trailingIcon = {
                        Button(
                            modifier = Modifier.padding(8.dp),
                            onClick = {
                                Log.i(
                                    "ChatScreen",
                                    "Launching coroutine..."
                                )
                                // Launching a coroutine
                                Log.i(
                                    "ChatScreen",
                                    "Launched coroutine..."
                                )
                                try {
                                    viewModel.run(
                                        ChatUserMessage(
                                            "user",
                                            text
                                        )
                                    )
                                    // Switch back to the Main thread for UI operations
                                    text = ""
                                } catch (e: Exception) {
                                    Log.e(
                                        "ChatScreen",
                                        e.stackTraceToString()
                                    )
                                    TODO("Not yet implemented")
                                }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Send,
                                contentDescription = "Send"
                            )
                        }
                    }
                )
            }
        }
    }
}


class MainActivity : ComponentActivity() {
    private val agentViewModel: Agent by viewModels()
    private val healthViewModel: HealthViewModel by viewModels()
    private val TAG = this::class.simpleName
    private val MY_PERMISSIONS_REQUEST_LOCATION = 1

    override fun onStart() {
        super.onStart()
        Log.d(
            "MainActivity",
            "onStart"
        )
    }

    override fun onResume() {
        super.onResume()
        Log.d(
            "MainActivity",
            "onResume"
        )
    }

    override fun onPause() {
        super.onPause()
        Log.d(
            "MainActivity",
            "onPause"
        )
    }

    override fun onStop() {
        super.onStop()
        Log.d(
            "MainActivity",
            "onStop"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(
            "MainActivity",
            "onDestroy"
        )
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION
            )
        } else {
            // Permission is already granted, you can use location services
            getLastLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DroneGPTTheme {

                Column {
                    StatusBar(healthViewModel.statusInfo)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopEnd // Aligns children to the bottom start (bottom left)
                    ) {
                        ChatScreen(agentViewModel)
                        DroneCameraView(
                            modifier = Modifier
                                .padding(16.dp)
                                .size(
                                    200.dp,
                                    150.dp
                                )
                        )
                    }
                }
            }
        }
        registerApp()
        checkLocationPermission()
    }


    private fun registerApp() {
        SDKManager.getInstance().init(
            this,
            object : SDKManagerCallback {
                override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                    Log.i(
                        TAG,
                        "onInitProcess: "
                    )
                    if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                        Log.i(
                            TAG,
                            "registerApp()"
                        )
                        SDKManager.getInstance().registerApp()
                    }
                }

                override fun onRegisterSuccess() {
                    Log.i(
                        TAG,
                        "onRegisterSuccess: "
                    )
                }

                override fun onRegisterFailure(error: IDJIError?) {
                    Log.i(
                        TAG,
                        "onRegisterFailure: "
                    )
                }

                override fun onProductConnect(productId: Int) {
                    Log.i(
                        TAG,
                        "onProductConnect: "
                    )
                    val statusManager = DeviceStatusManager.getInstance()
                    Log.i(
                        TAG,
                        "Current status: ${statusManager.currentDJIDeviceStatus}"
                    )

                    val error = UASRemoteIDManager.getInstance()
                        .setUASRemoteIDAreaStrategy(AreaStrategy.US_STRATEGY)

                    if (error != null) {
                        Log.e(
                            TAG,
                            "setUASRemoteIDAreaStrategy error: $error"
                        )
                    }

                    val stickManager = VirtualStickManager.getInstance()

                    stickManager.setVirtualStickStateListener(
                        object : VirtualStickStateListener {
                            override fun onVirtualStickStateUpdate(stickState: VirtualStickState) {
                                println(
                                    "onVirtualStickStateUpdate ${stickState.isVirtualStickEnable} ${stickState.isVirtualStickAdvancedModeEnabled}"
                                )
                            }

                            override fun onChangeReasonUpdate(
                                reason: FlightControlAuthorityChangeReason
                            ) {
                                println("onChangeReasonUpdate $reason")
                            }
                        }
                    )

                    val rcConnected = KeyTools.createKey(RemoteControllerKey.KeyConnection).get()
                    val rcFlightMode =
                        KeyTools.createKey(FlightControllerKey.KeyRemoteControllerFlightMode).get()
                    val flightMode = KeyTools.createKey(FlightControllerKey.KeyFlightMode).get()
                    val multiControl =
                        KeyTools.createKey(RemoteControllerKey.KeyMultiControlIsSupported).get()
                    println("virtualStick $multiControl $rcConnected $rcFlightMode $flightMode")

                    stickManager.enableVirtualStick(
                        object : CompletionCallback {
                            override fun onSuccess() {
                                Log.i(
                                    TAG,
                                    "enableVirtualStick() succeeded"
                                )
                            }

                            override fun onFailure(error: IDJIError) {
                                Log.e(
                                    TAG,
                                    "enableVirtualStick() failed: $error"
                                )
                            }
                        }
                    )
                    Log.i(
                        TAG,
                        "Called enableVirtualStick()"
                    )
                    healthViewModel.initialize()
                    VisionManager.initialize()
                    StateManager.initialize()
                }

                override fun onProductDisconnect(productId: Int) {
                    Log.i(
                        TAG,
                        "onProductDisconnect: "
                    )
                }

                override fun onProductChanged(productId: Int) {
                    Log.i(
                        TAG,
                        "onProductChanged: "
                    )
                }

                override fun
                        onDatabaseDownloadProgress(current: Long, total: Long) {
                    Log.i(
                        TAG,
                        "onDatabaseDownloadProgress: ${current / total}"
                    )
                }
            })
    }
}

fun formatInstantToPattern(instant: Instant, zoneId: ZoneId): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a") // "a" is the AM/PM marker
    val zonedDateTime = instant.atZone(zoneId)
    return formatter.format(zonedDateTime)
}

@Composable
fun StatusBar(state: StateFlow<StatusInfo>) {
    val statusInfo by state.collectAsState()
    val userTimeZone = ZoneId.systemDefault()
    val formattedTime = formatInstantToPattern(
        statusInfo.updatedAt,
        userTimeZone
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.LightGray)
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (statusInfo.status) {
                Status.HEALTHY -> Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "Status ok icon",
                    tint = Color.Green,
                )

                Status.WARNING -> Icon(
                    Icons.Rounded.Warning,
                    contentDescription = "Warning icon",
                    tint = Color.Yellow
                )

                Status.UNHEALTHY -> Icon(
                    Icons.Rounded.Warning,
                    contentDescription = "Unhealthy icon",
                    tint = Color.Red
                )

                Status.LOADING -> Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Loading icon",
                    tint = Color.DarkGray,
                )
            }
            Column(
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(statusInfo.statusReason)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedTime",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    
                    )
            }
        }
    }
}

@Composable
fun MessageCard(msg: PlainTextMessage) {
    if (msg is ChatAssistantMessage && msg.isControl()) {
        return
    }
    Log.d(
        "MessageCard",
        "Rendering message: $msg"
    )
    Row(modifier = Modifier.padding(all = 8.dp)) {
        var isExpanded by remember { mutableStateOf(false) }
        val surfaceColor by animateColorAsState(
            if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            label = "Color",
        )

        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            Text(
                text = msg.role.replaceFirstChar { it.titlecase() },
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 1.dp,
                color = surfaceColor,
                modifier = Modifier
                    .animateContentSize()
                    .padding(1.dp)
            ) {
                Text(
                    text = msg.content,
                    modifier = Modifier.padding(all = 4.dp),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}