package com.example.dronegpt.chat

import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException

object ChatCompletionAPI {
    private val TAG = this::class.simpleName

    private val client = OkHttpClient()
    private val gson = Gson()

    fun create(request: ChatCompletionRequest): ChatCompletionResponse {
        try {
            val jsonRequest = gson.toJson(request)
            Log.i(TAG, jsonRequest)
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
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val jsonResponse = response.body?.string()
                if (jsonResponse != null) {
                    Log.i(TAG, jsonResponse)
                }
                return gson.fromJson(jsonResponse, ChatCompletionResponse::class.java)
            }
        } catch (e: Exception) {
            TODO("Not yet implemented")
        }
    }
}

fun main() {
    val systemPrompt = ChatMessage(
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
        val userMessage = ChatMessage("user", input)
        messages.add(userMessage)
        val response = ChatCompletionAPI.create(ChatCompletionRequest("gpt-3.5-turbo", messages))
        messages.add(response.choices[0].message)
        println(messages.last().content)
    }
}