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
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.callback.CommonCallbacks.CompletionCallback
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.SDKManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.diagnostic.DeviceStatusManager
import dji.v5.manager.interfaces.SDKManagerCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                    "You're a helpful assistant"
                )
            )
        )

        setContent {
            DroneGPTTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column {
                        ConversationHistory(manager.getChatMessages())

                        var text by remember { mutableStateOf("Hello") }
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
                                                manager.add(ChatMessage("user", text))
                                                // Switch back to the Main thread for UI operations
                                                withContext(Dispatchers.Main) {
                                                    text = "Placeholder"
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

    private fun registerApp()
    {
        SDKManager.getInstance().init(this,object: SDKManagerCallback {
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
            override fun onProductConnect(productId: Int)
            {
                Log.i(TAG, "onProductConnect: ")
                val statusManager = DeviceStatusManager.getInstance()
                Log.i(TAG, "Current status: ${statusManager.currentDJIDeviceStatus}")
                VirtualStickManager.getInstance().enableVirtualStick(
                    object: CompletionCallback {
                        override fun onSuccess() {
                            Log.i(TAG, "enableVirtualStick() succeeded")
                        }
                        override fun onFailure(error: IDJIError) {
                            Log.e(TAG, "enableVirtualStick() failed: $error")
                        }
                    }
                )
                Log.i(TAG, "Called enableVirtualStick()")
            }
            override fun onProductDisconnect(productId: Int) {
                Log.i(TAG, "onProductDisconnect: ")
            }
            override fun onProductChanged(productId: Int)
            {
                Log.i(TAG, "onProductChanged: ")
            }
            override fun
                    onDatabaseDownloadProgress(current: Long, total: Long) {
                Log.i(TAG, "onDatabaseDownloadProgress: ${current/total}")
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