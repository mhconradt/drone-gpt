package com.example.dronegpt.fly

import com.example.dronegpt.chat.ChatAssistantMessage
import com.example.dronegpt.chat.ChatCompletionAPI
import com.example.dronegpt.chat.ChatCompletionRequest
import com.example.dronegpt.chat.ChatImageMessage
import com.example.dronegpt.chat.ChatMessage
import com.example.dronegpt.chat.ChatSystemMessage
import com.example.dronegpt.chat.ChatUserMessage
import com.example.dronegpt.chat.ContentPart
import com.example.dronegpt.chat.ImageUrlContentPart
import com.example.dronegpt.chat.RawImageUrl
import com.example.dronegpt.chat.TextContentPart
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.KeyManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import java.lang.reflect.Type
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
    var longitude: Double,
    var latitude: Double,
    var altitude: Double,
    // Velocity3D: Current flight speed of the aircraft using NED coordinate system.
    var xVelocity: Double, // East-west speed (m/s)
    var yVelocity: Double, // North-south speed (m/s)
    var zVelocity: Double, // Up-down speed (m/s)
    // KeyCompassHeading: North is 0 degrees, east is 90 degrees. The value range is [-180,180]. Unit: (˚).
    var compassHeading: Double,
    var sticks: Controls,
)


object VisionManager {
    val image: ByteArray? = null

    fun initialize() {

    }
}

object StateManager {
    val state: State? = null

    fun initialize() {

    }
}

object FlightManager {
    fun execute(instruction: Instruction) {
        when (instruction) {
            is TakeOff -> {
                KeyManager.getInstance()
                    .performAction(
                        KeyTools.createKey(
                            FlightControllerKey.KeyStartTakeoff
                        ),
                        object :
                            CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
                            override fun onSuccess(
                                nil: EmptyMsg
                            ) {
                                println(
                                    "Taking off"
                                )
                            }

                            override fun onFailure(
                                error: IDJIError
                            ) {
                                println(
                                    "Takeoff failed $error"
                                )
                            }
                        }
                    )
            }

            is Control -> {
                val stickManager = VirtualStickManager.getInstance()
                stickManager.leftStick.verticalPosition = instruction.controls.leftStick.verticalPosition
                stickManager.leftStick.horizontalPosition = instruction.controls.leftStick.horizontalPosition
                stickManager.rightStick.verticalPosition = instruction.controls.rightStick.verticalPosition
                stickManager.rightStick.horizontalPosition = instruction.controls.rightStick.horizontalPosition
                StateManager.state?.sticks = instruction.controls
            }
            is Land -> {
                KeyManager.getInstance()
                    .performAction(
                        KeyTools.createKey(
                            FlightControllerKey.KeyStartAutoLanding
                        ),
                        object :
                            CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
                            override fun onSuccess(
                                nil: EmptyMsg
                            ) {
                                println(
                                    "Taking off"
                                )
                            }

                            override fun onFailure(
                                error: IDJIError
                            ) {
                                println(
                                    "Takeoff failed $error"
                                )
                            }
                        }
                    )
            }
        }
    }
}

sealed class Instruction

data class TakeOff(val type: String) : Instruction()
data class Land(val type: String) : Instruction()

data class Control(val type: String, val controls: Controls) : Instruction()

class InstructionDeserializer : JsonDeserializer<Instruction> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Instruction {
        val jsonObject = json.asJsonObject

        return when (jsonObject.get("type").asString) {
            "control" -> context.deserialize<Control>(json, Control::class.java)
            "take_off" -> context.deserialize<TakeOff>(json, TakeOff::class.java)
            "land" -> context.deserialize<Land>(json, Land::class.java)
            else -> {
                throw IllegalStateException(json.toString())
            }
        }
    }

}


object Agent {
    val model: String = "gpt-4-vision-preview"
    val messages: MutableList<ChatMessage> = mutableListOf(
        ChatSystemMessage(
            "system",
            SYSTEM_PROMPT,
        )
    )

    @OptIn(ExperimentalEncodingApi::class)
    fun run(command: ChatUserMessage) {
        messages.add(command)

        val gson = GsonBuilder()
            .registerTypeAdapter(Instruction::class.java, InstructionDeserializer())
            .create()

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
                val instructionJson = extractJson(completionMessage.content)
                if (instructionJson != null) {
                    val instruction = gson.fromJson(instructionJson, Instruction::class.java)
                    FlightManager.execute(instruction)
                }
            }
        }
    }
}


fun extractJson(text: String): String? {
    val left = text.indexOfFirst { it == '{' }
    val right = text.indexOfLast { it == '}' }
    if (left == -1 || right == -1) {
        return null
    }
    return text.substring(left, right + 1)
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