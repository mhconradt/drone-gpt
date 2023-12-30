package com.example.dronegpt.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type

sealed class ContentPart
data class TextContentPart(val type: String, val text: String) : ContentPart()
data class RawImageUrl(val url: String, val detail: String)
data class ImageUrlContentPart(val type: String, val image_url: RawImageUrl) : ContentPart()
data class OpenAIFunction(val name: String, val arguments: String)
data class ToolCall(val id: String, val type: String, val function: OpenAIFunction)

sealed class ChatMessage

// No difference here
data class ChatSystemMessage(val role: String, val content: String) : ChatMessage()

// No difference here either
data class ChatUserMessage(val role: String, val content: String) : ChatMessage()

// Most of the headache comes from this
data class ChatImageMessage(val role: String, val content: List<ContentPart>) : ChatMessage()
data class ChatAssistantMessage(
    val role: String,
    val content: String,
    val tool_calls: List<ToolCall>? = null
) : ChatMessage()

data class ChatToolMessage(val role: String, val content: String, val tool_call_id: String) :
    ChatMessage()


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


class ChatMessageDeserializer : JsonDeserializer<ChatMessage> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChatMessage {
        val jsonObject = json.asJsonObject

        val role: String = jsonObject.get("role").asString

        return if (role == "system") {
            context.deserialize<ChatSystemMessage>(json, ChatSystemMessage::class.java)
        } else if (role == "assistant") {

            context.deserialize<ChatAssistantMessage>(json, ChatAssistantMessage::class.java)
        } else if (role == "user" && jsonObject.get("content").isJsonArray) {
            context.deserialize<ChatImageMessage>(json, ChatImageMessage::class.java)
        } else if (role == "user") {
            context.deserialize<ChatUserMessage>(json, ChatUserMessage::class.java)
        } else if (role == "tool") {
            context.deserialize<ChatToolMessage>(json, ChatToolMessage::class.java)
        } else {
            throw IllegalStateException(json.toString())
        }
    }
}


class ContentPartDeserializer: JsonDeserializer<ContentPart> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ContentPart {
        val jsonObject = json.asJsonObject

        return when (jsonObject.get("type").asString) {
            "text" -> context.deserialize<TextContentPart>(json, TextContentPart::class.java)
            "image_url" -> context.deserialize<ImageUrlContentPart>(
                json,
                ImageUrlContentPart::class.java
            )

            else -> {
                throw java.lang.IllegalStateException(json.toString())
            }
        }
    }
}

data class ChatCompletionResponseChoice(val message: ChatMessage)

data class ChatCompletionResponse(val choices: List<ChatCompletionResponseChoice>)

data class ChatCompletionRequest(val model: String, val messages: List<ChatMessage>, val max_tokens: Int? = null)