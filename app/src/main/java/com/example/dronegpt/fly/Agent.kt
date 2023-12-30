package com.example.dronegpt.fly

import com.example.dronegpt.chat.ChatAssistantMessage
import com.example.dronegpt.chat.ChatCompletionAPI
import com.example.dronegpt.chat.ChatCompletionRequest
import com.example.dronegpt.chat.ChatImageMessage
import com.example.dronegpt.chat.ChatMessage
import com.example.dronegpt.chat.ChatUserMessage
import com.example.dronegpt.chat.ContentPart
import com.example.dronegpt.chat.Function
import com.example.dronegpt.chat.ImageUrlContentPart
import com.example.dronegpt.chat.RawImageUrl
import com.example.dronegpt.chat.TextContentPart
import com.example.dronegpt.chat.Tool
import com.google.gson.Gson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


const val SYSTEM_PROMPT = """
You are DroneGPT, an assistant that helps users fly their drones.
When the user sends a command, you send instructions to fly
the drone based on the user's command, the current view from the camera, and sensor data from the
drone, such as position, velocity, and the current control positions. 
These instructions MUST be syntactically valid JSON objects, for example, they must not have comments or else the drone will crash.
Keep in mind, you will only be able to adjust the controls once every 5-10 seconds, so fly slowly and avoid collision courses with nearby objects.

Here are some examples:
### Taking off (Signals the drone to take off and hover at 1.2m, required if altitude is zero) ###
User: Take off
DroneGPT: 
{
    "type": "take_off",
    "message": "Ok, I'm taking off!"
}
###Landing (only do this if the surrounding area is flat and clear of obstacles) ###
User: Land
DroneGPT: 
{
    "type": "land",
    "message": "I'm landing"
}
### Left Stick Horizontal Position (- rotates left, + rotates right) ###
User: Turn right
DroneGPT: 
{
    "type": "control",
    "leftStick": {
        "horizontalPosition": 150,
        "verticalPosition": 0
    },
    "rightStick": {
        "horizontalPosition": 0,
        "verticalPosition": 0
    }
}
### Left Stick Vertical Position (- decreases altitude, + increases altitude) ###
User: Go higher
DroneGPT: 
{
    "type": "control",
    "leftStick": {
        "horizontalPosition": 0,
        "verticalPosition": 132
    },
    "rightStick": {
        "horizontalPosition": 0,
        "verticalPosition": 0
    }
}
### Right Stick Horizontal Position (- moves left, + moves right) ###
User: Move to the right
DroneGPT:
{
    "type": "control",
    "leftStick": {
        "horizontalPosition": 0,
        "verticalPosition": 0
    },
    "rightStick": {
        "horizontalPosition": 284,
        "verticalPosition": 0
    }
}
### Right Stick Vertical Position (- moves backwards, + moves forward) ###
User: Move backwards
DroneGPT: 
{
    "type": "control",
    "leftStick": {
        "horizontalPosition": 0,
        "verticalPosition": 0
    },
    "rightStick": {
        "horizontalPosition": 0,
        "verticalPosition": -243
    }
}
"""

data class StickPosition(
    val verticalPosition: Int,
    val horizontalPosition: Int,
)

data class Controls(
    val leftStick: StickPosition,
    val rightStick: StickPosition,
)

data class State(
    // KeyAircraftLocation3D: To get the position of the aircraft, including longitude, latitude and altitude.
    val longitude: Double,
    val latitude: Double,
    val altitude: Double,
    // Velocity3D: Current flight speed of the aircraft using NED coordinate system.
    val xVelocity: Double, // East-west speed (m/s)
    val yVelocity: Double, // North-south speed (m/s)
    val zVelocity: Double, // Up-down speed (m/s)
    // KeyCompassHeading: North is 0 degrees, east is 90 degrees. The value range is [-180,180]. Unit: (˚).
    val compassHeading: Double,
    val sticks: Controls,
)

// sealed class AgentMessage {
//     abstract fun toOpenAI()
// }
//
// data class SystemMessage(val role: String, val content: String)
// data class UserMessage(val role: String, val content: String)
// data class ImageMessage(val text: String, val image_url: String)
// data class ControlMessage(val controls: Controls)
// data class AssistantMessage(val content: String)


object VisionManager {
    val image: ByteArray? = null
}

object StateManager {
    val state: State? = null
}

object FlightManager {

}

object Agent {
    val model: String = "gpt-4-vision-preview"
    val messages: MutableList<ChatMessage> = mutableListOf()

    val tools: List<Tool> = listOf(
        Tool(
            "function",
            Function(
                "take_off",
                "Signals the aircraft to take off. " +
                        "It will climb to an altitude of 1.2m and then hover until it receives further instructions."
            ),
        ),
        Tool(
            "function",
            Function(
                "land",
                "Signals the aircraft to land. " +
                        "The surrounding volume must be clear of obstacles and should always be safe for takeoff."
            )
        ),
        Tool(
            "function",
            Function(
                "adjust_controls",
                "Adjust the drone's controls.",
                mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "leftStick" to mapOf(
                            "description" to "Left stick controls the yaw axis and throttle of the aircraft. " +
                                    "Negative horizontalPosition rotates the aircraft counterclockwise, " +
                                    "positive rotates it clockwise. " +
                                    "Positive verticalPosition increases altitude, negative lowers it.",
                            "properties" to mapOf(
                                "horizontalPosition" to mapOf(
                                    "type" to "number",
                                    "description" to "Negative for left, positive for right movement."
                                ),
                                "verticalPosition" to mapOf(
                                    "type" to "number",
                                    "description" to "Positive for upward, negative for downward movement."
                                )
                            ),
                            "type" to "object"
                        ),
                        "rightStick" to mapOf(
                            "description" to "Right stick controls the roll axis and pitch axis of the aircraft. " +
                                    "Negative horizontalPosition makes the aircraft fly left, " +
                                    "positive makes it fly right. " +
                                    "Positive verticalPosition makes the aircraft fly forward, negative flies it backward.",
                            "properties" to mapOf(
                                "horizontalPosition" to mapOf(
                                    "type" to "number",
                                    "description" to "Negative for left, positive for right movement."
                                ),
                                "verticalPosition" to mapOf(
                                    "type" to "number",
                                    "description" to "Positive for forward, negative for backward movement."
                                )
                            ),
                            "type" to "object"
                        )
                    )
                )
            )
        )
    )

    @OptIn(ExperimentalEncodingApi::class)
    fun run(command: ChatUserMessage) {
        messages.add(command)

        val gson = Gson()

        while (true) {
            if (VisionManager.image != null && StateManager.state != null) {
                val state = gson.toJson(StateManager.state)
                val contentParts = listOf<ContentPart>(
                    TextContentPart("text", state),
                    ImageUrlContentPart(
                        "image_url", RawImageUrl(
                            "data:image/jpeg;base64,${Base64.encode(VisionManager.image)}",
                            "An image captured from the drone's camera."
                        )
                    )
                )
                val imageMessage = ChatImageMessage("user", contentParts)
                messages.add(imageMessage)
            }
            val request = ChatCompletionRequest(model, messages, 256)
            val response = ChatCompletionAPI.create(request)
            val completionMessage = response.choices[0].message
            messages.add(completionMessage)
            if (completionMessage is ChatAssistantMessage) {
                val toolCalls = completionMessage.tool_calls
                if (toolCalls.isNullOrEmpty()) {
                    return
                }
            }
        }
    }
}


@OptIn(ExperimentalEncodingApi::class)
fun main() {
    val gson = Gson()

    val encoded = Base64.encode(
        java.io.File("/Users/maxwellconradt/Documents/sandzenPainting1.jpg").readBytes()
    )

    val request = ChatCompletionRequest(
        "gpt-4",
        listOf(
            ChatImageMessage(
                "user",
                listOf(
                    TextContentPart(
                        "text",
                        "What's in this image?"
                    ),
                    ImageUrlContentPart(
                        "image_url",
                        RawImageUrl(
                            "data:image/jpeg;base64,${encoded}",
                            "An image"
                        )
                    )
                )
            )
        ),
        1024
    )

    val response = ChatCompletionAPI.create(request)

    println(response.choices[0])
}