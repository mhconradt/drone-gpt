package com.example.dronegpt.chat

import android.util.Log
import com.example.dronegpt.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.IOException

object ChatCompletionAPI {
    private val TAG = this::class.java.simpleName
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    private val client = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
    private val gson = GsonBuilder()
        .registerTypeAdapter(ChatMessage::class.java, ChatMessageDeserializer())
        .registerTypeAdapter(ContentPart::class.java, ContentPartDeserializer())
        .create()

    fun create(request: ChatCompletionRequest): ChatCompletionResponse {
        try {
            val jsonRequest = gson.toJson(request)
            val body = jsonRequest.toRequestBody("application/json; charset=utf-8".toMediaType())

            val httpRequest = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body)
                .addHeader(
                    "Authorization",
                    "Bearer ${BuildConfig.OPENAI_API_KEY}"
                )
                .build()


            Log.d(TAG, httpRequest.toString())

            Log.d(TAG, "Sending ${httpRequest.body?.contentLength()} bytes")

            client.newCall(httpRequest).execute().use { response ->

                if (!response.isSuccessful) {
                    throw IOException("Unexpected response ${response.message}")
                }

                val jsonResponse = response.body?.string()

                Log.d(TAG, "Received ${jsonResponse?.length} bytes")

                return gson.fromJson(jsonResponse, ChatCompletionResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling OpenAI API $e")
            throw e
        }
    }
}
