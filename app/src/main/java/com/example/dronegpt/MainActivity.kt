package com.example.dronegpt

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.compose.jetchat.theme.DroneGPTTheme
import com.example.dronegpt.chat.ChatAssistantMessage
import com.example.dronegpt.chat.ChatMessage
import com.example.dronegpt.chat.ChatUserMessage
import com.example.dronegpt.fly.Agent
import com.example.dronegpt.fly.StateManager
import com.example.dronegpt.fly.VisionManager
import dji.v5.common.callback.CommonCallbacks.CompletionCallback
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.aircraft.uas.AreaStrategy
import dji.v5.manager.aircraft.uas.UASRemoteIDManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.diagnostic.DeviceStatusManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.utils.common.LocationUtil.getLastLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    private val TAG = this::class.simpleName
    private val MY_PERMISSIONS_REQUEST_LOCATION = 1
    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION)
        } else {
            // Permission is already granted, you can use location services
            getLastLocation()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DroneGPTTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column {
                        ConversationHistory(Agent.getChatMessages())

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
                                                Agent.run(ChatUserMessage("user", text))
                                                // Switch back to the Main thread for UI operations
                                                withContext(Dispatchers.Main) {
                                                    text = "Type a message"
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
        checkLocationPermission()
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

                // SimulatorManager.getInstance().enableSimulator(
                //     InitializationSettings.createInstance(
                //         LocationCoordinate2D(37.44601560124988, -122.15639375795153),
                //         5,
                //     ),
                //     object: CompletionCallback {
                //         override fun onSuccess() {
                //             Log.i(TAG, "Simulator enabled")
                //         }
                //
                //         override fun onFailure(error: IDJIError) {
                //             Log.e(TAG, "enableSimulator failed: $error")
                //         }
                //
                //     }
                // )

                val error = UASRemoteIDManager.getInstance()
                    .setUASRemoteIDAreaStrategy(AreaStrategy.US_STRATEGY)

                if (error != null) {
                    Log.e(TAG, "setUASRemoteIDAreaStrategy error: $error")
                }

                VirtualStickManager.getInstance().enableVirtualStick(
                    object : CompletionCallback {
                        override fun onSuccess() {
                            Log.i(TAG, "enableVirtualStick() succeeded")
                        }

                        override fun onFailure(error: IDJIError) {
                            Log.e(TAG, "enableVirtualStick() failed: $error")
                        }
                    }
                )
                Log.i(TAG, "Called enableVirtualStick()")

                VisionManager.initialize()
                StateManager.initialize()
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
    if (msg is ChatAssistantMessage) {
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
    } else if (msg is ChatAssistantMessage) {
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
}

@Preview(name = "Light Mode")
@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    name = "Dark Mode"
)
@Composable
fun PreviewMessageCard() {
    MessageCard(ChatAssistantMessage("assistant", "Hi, how can I help you today?", listOf()))
}

/**
 * SampleData for Jetpack Compose Tutorial
 */

object SampleData {
    // Sample conversation data
    val conversationSample = listOf(
        ChatUserMessage("user",
            "Hello",
        ),
        ChatAssistantMessage("assistant",
            "Hi, how can I help you today?",
            listOf()
        ),
        ChatUserMessage("user",
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