package com.example.dronegpt.fly

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
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
import com.example.dronegpt.chat.isControl
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.et.get
import dji.v5.et.listen
import dji.v5.et.set
import dji.v5.manager.KeyManager
import dji.v5.manager.aircraft.virtualstick.VirtualStickManager
import dji.v5.manager.datacenter.camera.CameraStreamManager
import dji.v5.manager.interfaces.ICameraStreamManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.lang.reflect.Type
import java.nio.ByteBuffer
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
    var leftStick: StickPosition,
    var rightStick: StickPosition,
)

data class State(
    // KeyAircraftLocation3D: To get the position of the aircraft, including longitude, latitude and altitude.
    var longitude: Double? = null,
    var latitude: Double? = null,
    var altitude: Double? = null,
    // Velocity3D: Current flight speed of the aircraft using NED coordinate system.
    // East-west speed (m/s)
    var xVelocity: Double? = null,
    // North-south speed (m/s)
    var yVelocity: Double? = null,
    // Up-down speed (m/s)
    var zVelocity: Double? = null,
    // KeyCompassHeading: North is 0 degrees, east is 90 degrees. The value range is [-180,180]. Unit: (˚).
    var compassHeading: Double? = null,
    var sticks: Controls? = null,
)


object StateManager {
    val state: State = State()

    fun initialize() {
        // Retrieve initial values

        val location3D = KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D).get()
        val velocity3D = KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity).get()
        val compassHeading = KeyTools.createKey(FlightControllerKey.KeyCompassHeading).get()

        val stickManager = VirtualStickManager.getInstance()
        val controls = Controls(
            StickPosition(
                stickManager.leftStick.verticalPosition,
                stickManager.leftStick.horizontalPosition
            ),
            StickPosition(
                stickManager.rightStick.verticalPosition,
                stickManager.rightStick.horizontalPosition,
            )
        )

        // Set initial values

        state.longitude = location3D?.longitude
        state.latitude = location3D?.latitude
        state.altitude = location3D?.altitude
        state.xVelocity = velocity3D?.x
        state.yVelocity = velocity3D?.y
        state.zVelocity = velocity3D?.z
        state.compassHeading = compassHeading
        state.sticks = controls

        // Listen for updates

        KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D).listen(
            this,
            false
        ) {
            if (it != null) {
                state.longitude = it.longitude
                state.latitude = it.latitude
                state.altitude = it.altitude
                println(state)
            }
        }

        KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity).listen(
            this,
            false
        ) {
            if (it != null) {
                state.xVelocity = it.x
                state.yVelocity = it.y
                state.zVelocity = it.z
                println(state)
            }
        }

        KeyTools.createKey(FlightControllerKey.KeyCompassHeading).listen(
            this,
            false
        ) {
            if (it != null) {
                state.compassHeading = it
                println(state)
            }
        }

        // Controls are set externally
    }
}

fun convertNV21ToJpeg(nv21ImageData: ByteArray, width: Int, height: Int): ByteArray {
    val yuvImage = YuvImage(nv21ImageData, ImageFormat.NV21, width, height, null)
    val outputStream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, outputStream)
    return outputStream.toByteArray()
}

fun convertRGBA8888ToJpeg(rgbaData: ByteArray, width: Int, height: Int): ByteArray {
    // Create a Bitmap from the RGBA byte array
    val buffer = ByteBuffer.wrap(rgbaData)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(buffer)

    // Compress the Bitmap to JPEG
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
    bitmap.recycle()  // Recycle the bitmap to free memory

    // Return the JPEG byte array
    return outputStream.toByteArray()
}

object VisionManager {
    var image: ByteArray? = null

    fun initialize() {
        val cStreamManager = CameraStreamManager.getInstance()

        cStreamManager.addFrameListener(
            ComponentIndexType.LEFT_OR_MAIN, ICameraStreamManager.FrameFormat.NV21
        ) { frameData, offset, length, width, height, format ->
            val relevantData = frameData.copyOfRange(offset, offset + length)
            image = when (format) {
                ICameraStreamManager.FrameFormat.RGBA_8888 -> {
                    convertRGBA8888ToJpeg(relevantData, width, height)
                }

                ICameraStreamManager.FrameFormat.NV21 -> {
                    convertNV21ToJpeg(relevantData, width, height)
                }

                else -> {
                    throw java.lang.IllegalStateException(format.toString())
                }
            }
        }
    }
}

object FlightManager {
    fun execute(instruction: Instruction) {
        when (instruction) {
            is TakeOff -> {
                KeyTools.createKey(
                    FlightControllerKey.KeyHomeLocation,
                ).set(
                    LocationCoordinate2D(
                        StateManager.state.longitude,
                        StateManager.state.latitude
                    )
                )
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
                stickManager.leftStick.verticalPosition =
                    instruction.leftStick.verticalPosition
                stickManager.leftStick.horizontalPosition =
                    instruction.leftStick.horizontalPosition
                stickManager.rightStick.verticalPosition =
                    instruction.rightStick.verticalPosition
                stickManager.rightStick.horizontalPosition =
                    instruction.rightStick.horizontalPosition
                StateManager.state.sticks?.leftStick = instruction.leftStick
                StateManager.state.sticks?.rightStick = instruction.rightStick
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

data class Control(val type: String, val leftStick: StickPosition, val rightStick: StickPosition) :
    Instruction()

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

    fun getChatMessages(): List<ChatMessage> {
        return messages.filter { it is ChatUserMessage || (it is ChatAssistantMessage && !it.isControl()) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun run(command: ChatUserMessage) {
        messages.add(command)
        println(command)

        val gson = GsonBuilder()
            .registerTypeAdapter(Instruction::class.java, InstructionDeserializer())
            .create()

        while (true) {
            val targetLoopMs = 5000
            val startTime = System.currentTimeMillis()
            val imageSnapshot = VisionManager.image
            val state = gson.toJson(StateManager.state)
            val contentParts = mutableListOf<ContentPart>(
                TextContentPart("text", state)
            )
            if (imageSnapshot != null) {
                contentParts.add(
                    ImageUrlContentPart(
                        "image_url", RawImageUrl(
                            "data:image/jpeg;base64,${Base64.encode(imageSnapshot)}",
                            "An image captured from the drone's camera."
                        )
                    )
                )
            }
            val imageMessage = ChatImageMessage("system", contentParts)
            messages.add(imageMessage)
            println(imageMessage)

            val filteredMessages = getAgentMessages()
            val request = ChatCompletionRequest(model, filteredMessages, 512)
            println(request)
            val response = ChatCompletionAPI.create(request)
            val completionMessage = response.choices[0].message
            messages.add(completionMessage)
            println(completionMessage)
            if (completionMessage is ChatAssistantMessage) {
                val instructionJson = extractJson(completionMessage.content)
                if (instructionJson != null) {
                    val instruction = gson.fromJson(instructionJson, Instruction::class.java)
                    println("Got instruction $instruction")
                    FlightManager.execute(instruction)
                } else {
                    val nullInstruction = Control("control", StickPosition(0, 0), StickPosition(0, 0))
                    FlightManager.execute(nullInstruction)
                    return
                }
            }
            val duration = System.currentTimeMillis() - startTime
            println("Loop took $duration ms to run")
            val residual = targetLoopMs - duration
            if (residual > 0) {
                runBlocking {
                    delay(residual)
                }
            } else {
                println("Loop took ${-residual} ms longer than expected")
            }
        }
    }

    private fun getAgentMessages(): List<ChatMessage> {
        /*
            Types of messages:

            SYSTEM PROMPT

            USER MESSAGE
            [
            SYSTEM MESSAGE: STATE
            SYSTEM MESSAGE: IMAGE (BASE64)
            TOOL CALL
            ]+
            SYSTEM MESSAGE: STATE
            SYSTEM MESSAGE: IMAGE (BASE64)
            ASSISTANT MESSAGE
        */
        // System message
        // All user + non-control assistant messages
        val lastUserMessage = messages.indexOfLast { it is ChatUserMessage }
        val anchorImageMessage = lastUserMessage + 1

        val filtered = mutableListOf(
            messages[0],
            messages[lastUserMessage],
            messages[anchorImageMessage]
        )
        if (anchorImageMessage != messages.size - 1) {
            // First assistant response
            filtered.add(messages[anchorImageMessage + 1])
            // Most recent image message
            filtered.add(messages[messages.size - 1])
        }
        return messages
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