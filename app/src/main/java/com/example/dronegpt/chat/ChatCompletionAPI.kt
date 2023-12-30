package com.example.dronegpt.chat

import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException

object ChatCompletionAPI {
    private val TAG = this::class.simpleName

    private val client = OkHttpClient()
    private val gson = GsonBuilder()
        .registerTypeAdapter(ChatMessage::class.java, ChatMessageDeserializer())
        .registerTypeAdapter(ContentPart::class.java, ContentPartDeserializer())
        .create()

    fun create(request: ChatCompletionRequest): ChatCompletionResponse {
        try {
            val jsonRequest = gson.toJson(request)
            println("hi")
            val body = jsonRequest.toRequestBody("application/json; charset=utf-8".toMediaType())
            val httpRequest = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader(
                    "Authorization",
                    "Bearer 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"
                )
                .build()

            client.newCall(httpRequest).execute().use { response ->

                if (!response.isSuccessful) {
                    println(response.body?.string())
                    throw IOException("Unexpected response ${response.message}")
                }

                val jsonResponse = response.body?.string()


                if (jsonResponse != null) {
                    println(jsonResponse)
                }
                return gson.fromJson(jsonResponse, ChatCompletionResponse::class.java)
            }
        } catch (e: Exception) {
            println(e)
            TODO("Not yet implemented")
        }
    }
}

fun main() {
    val systemPrompt = ChatSystemMessage(
        "system",
        "You're a helpful assistant being used in a command line interface (CLI)."
    )
    val messages = mutableListOf<ChatMessage>(systemPrompt);
    while (true) {
        val input = readlnOrNull();
        if (input.isNullOrEmpty()) {
            println("Goodbye")
            return
        }
        val userMessage = ChatUserMessage("user", input)
        messages.add(userMessage)
        val response = ChatCompletionAPI.create(ChatCompletionRequest("gpt-3.5-turbo", messages))
        messages.add(response.choices[0].message)
        println(messages.last())
    }
}