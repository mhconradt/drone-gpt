package com.example.dronegpt.chat

data class ChatMessage(val role: String, val content: String)
data class ChatCompletionRequest(val model: String, val messages: List<ChatMessage>)
data class ChatCompletionResponse(val choices: List<ChatCompletionResponseChoice>)
data class ChatCompletionResponseChoice(val message: ChatMessage)