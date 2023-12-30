package com.example.dronegpt.fly

import com.example.dronegpt.chat.ChatCompletionAPI
import com.example.dronegpt.chat.ChatCompletionRequest
import com.example.dronegpt.chat.ChatImageMessage
import com.example.dronegpt.chat.ImageUrlContentPart
import com.example.dronegpt.chat.RawImageUrl
import com.example.dronegpt.chat.TextContentPart
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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


@OptIn(ExperimentalEncodingApi::class)
fun main() {
    val encoded = Base64.encode(java.io.File("/Users/maxwellconradt/Documents/sandzenPainting1.jpg").readBytes())

    val request = ChatCompletionRequest(
        "gpt-4-vision-preview",
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
        )
    )

    val response = ChatCompletionAPI.create(request)

    println(response.choices[0])
}