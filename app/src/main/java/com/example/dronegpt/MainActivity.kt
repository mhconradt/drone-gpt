package com.example.dronegpt

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.compose.jetchat.theme.DroneGPTTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import com.example.dronegpt.chat.ChatManager
import com.example.dronegpt.chat.ChatMessage
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.RemoteControllerKey
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.common.callback.CommonCallbacks.CompletionCallback
import dji.v5.common.callback.CommonCallbacks.CompletionCallbackWithParam
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.KeyManager
import dji.v5.manager.SDKManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.diagnostic.DeviceStatusManager
import dji.v5.manager.interfaces.SDKManagerCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val TAG = this::class.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = ChatManager(
            messages = mutableListOf(
                ChatMessage(
                    "system",
                    "You're a helpful assistant in charge of flying a drone on behalf of the " +
                            "user."
                )
            )
        )

        setContent {
            DroneGPTTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column {
                        ConversationHistory(manager.getChatMessages())

                        var text by remember { mutableStateOf("Take off") }
                        TextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            trailingIcon = {
                                Button(
                                    onClick = {
                                        println(text)
                                        Log.i(TAG, "Launching coroutine...")
                                        // Launching a coroutine
                                        CoroutineScope(Dispatchers.IO).launch {
                                            Log.i(TAG, "Launched coroutine...")
                                            try {
                                                if (text === "Take off") {
                                                    Log.i(TAG, "Attempting takeoff")
                                                    val motorsOn = KeyManager.getInstance().getValue(
                                                        KeyTools.createKey(FlightControllerKey.KeyAreMotorsOn)
                                                    )

                                                    Log.i(TAG, "Are motors on? $motorsOn")

                                                    val signalLevel = KeyManager.getInstance().getValue(
                                                        KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel)
                                                    )

                                                    Log.i(TAG, "Signal Level: $signalLevel")

                                                    val connected = KeyManager.getInstance().getValue(
                                                        KeyTools.createKey(FlightControllerKey.KeyConnection)
                                                    )

                                                    Log.i(TAG, "Connected? $connected")

                                                    val flightMode = KeyManager.getInstance().getValue(
                                                        KeyTools.createKey(FlightControllerKey.KeyRemoteControllerFlightMode)
                                                    )

                                                    Log.i(TAG, "Flight Mode: $flightMode")

                                                    val rcConnected = KeyManager.getInstance().getValue(
                                                        KeyTools.createKey(RemoteControllerKey.KeyConnection)
                                                    )

                                                    Log.i(TAG, "RC Connected? $rcConnected")

                                                    val location = KeyManager.getInstance().getValue(
                                                        KeyTools.createKey(FlightControllerKey.KeyAircraftLocation)
                                                    )

                                                    Log.i(TAG, "Location $location")

                                                    KeyManager.getInstance().setValue(
                                                        KeyTools.createKey(
                                                            FlightControllerKey.KeyHomeLocation,
                                                        ),
                                                        location,
                                                        object :
                                                            CompletionCallback {
                                                            override fun onSuccess() {
                                                                Log.i(TAG, "Set home location")

                                                                Log.i(TAG, "ATTEMPTING TAKEOFF")

                                                                KeyManager.getInstance().performAction(
                                                                    KeyTools.createKey(FlightControllerKey.KeyStartTakeoff),
                                                                    object :
                                                                        CompletionCallbackWithParam<EmptyMsg> {
                                                                        override fun onSuccess(nil: EmptyMsg) {
                                                                            Log.i(TAG, "Taking off")
                                                                        }

                                                                        override fun onFailure(error: IDJIError) {
                                                                            Log.e(TAG, "Takeoff failed $error")
                                                                        }
                                                                    }
                                                                )
                                                            }

                                                            override fun onFailure(error: IDJIError) {
                                                                Log.e(TAG, "Set home location failed $error")
                                                            }
                                                        }
                                                    )

                                                    Log.i(TAG, "Setting home location")

                                                    val serialNumber = KeyManager.getInstance().getValue(
                                                        KeyTools.createKey(FlightControllerKey.KeySerialNumber)
                                                    )

                                                    Log.i(TAG, "Serial number $serialNumber")
                                                }

                                                if (text === "Land") {
                                                    Log.i(TAG, "Attempting landing")
                                                    KeyManager.getInstance().performAction(
                                                        KeyTools.createKey(FlightControllerKey.KeyStartAutoLanding),
                                                        object :
                                                            CompletionCallbackWithParam<EmptyMsg> {
                                                            override fun onSuccess(nil: EmptyMsg) {
                                                                Log.i(TAG, "Landing...")
                                                            }

                                                            override fun onFailure(error: IDJIError) {
                                                                Log.e(TAG, "Landing failed $error")
                                                            }
                                                        }
                                                    )
                                                }

                                                manager.add(ChatMessage("user", text))
                                                // Switch back to the Main thread for UI operations
                                                withContext(Dispatchers.Main) {
                                                    text = "Take off"
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, e.stackTraceToString())
                                                TODO("Not yet implemented")
                                            }
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

        registerApp()
    }

    private fun registerApp() {
        SDKManager.getInstance().init(this, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) {
                Log.i(TAG, "onInitProcess: ")
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    Log.i(TAG, "registerApp()")
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onRegisterSuccess() {
                Log.i(TAG, "onRegisterSuccess: ")
            }

            override fun onRegisterFailure(error: IDJIError?) {
                Log.i(TAG, "onRegisterFailure: ")
            }

            override fun onProductConnect(productId: Int) {
                Log.i(TAG, "onProductConnect: ")
                val statusManager = DeviceStatusManager.getInstance()
                Log.i(TAG, "Current status: ${statusManager.currentDJIDeviceStatus}")
                if (false) {
                    VirtualStickManager.getInstance().enableVirtualStick(
                        object : CompletionCallback {
                            override fun onSuccess() {
                                Log.i(TAG, "enableVirtualStick() succeeded")
                                val stickManager = VirtualStickManager.getInstance()
                                stickManager.leftStick.verticalPosition = 1
                            }

                            override fun onFailure(error: IDJIError) {
                                Log.e(TAG, "enableVirtualStick() failed: $error")
                            }
                        }
                    )
                    Log.i(TAG, "Called enableVirtualStick()")
                }
            }

            override fun onProductDisconnect(productId: Int) {
                Log.i(TAG, "onProductDisconnect: ")
            }

            override fun onProductChanged(productId: Int) {
                Log.i(TAG, "onProductChanged: ")
            }

            override fun
                    onDatabaseDownloadProgress(current: Long, total: Long) {
                Log.i(TAG, "onDatabaseDownloadProgress: ${current / total}")
            }
        })
    }
}

data class Message(val author: String, val body: String)

@Composable
fun MessageCard(msg: ChatMessage) {
    if (msg.role === "system") {
        return
    }

    Row(modifier = Modifier.padding(all = 8.dp)) {
        var isExpanded by remember { mutableStateOf(false) }
        val surfaceColor by animateColorAsState(
            if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        )

        Column(modifier = Modifier.clickable { isExpanded = !isExpanded }) {
            Text(
                text = msg.role,
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

@Preview(name = "Light Mode")
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark Mode"
)
@Composable
fun PreviewMessageCard() {
    MessageCard(ChatMessage("assistant", "Hi, how can I help you today?"))
}

/**
 * SampleData for Jetpack Compose Tutorial
 */

object SampleData {
    // Sample conversation data
    val conversationSample = listOf(
        ChatMessage(
            "user",
            "Hello",
        ),
        ChatMessage(
            "assistant",
            "Hi, how can I help you today?"
        ),
        ChatMessage(
            "user",
            "I want to fly my drone"
        )
    )
}

@Composable
fun ConversationHistory(messages: List<ChatMessage>) {
    LazyColumn {
        items(messages) { message -> MessageCard(msg = message) }
    }
}

@Preview
@Composable
fun PreviewConversation() {
    DroneGPTTheme {
        ConversationHistory(messages = SampleData.conversationSample)
    }
}