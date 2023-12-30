package com.example.dronegpt.chat

import android.os.StrictMode

class ChatManager(
    val model: String = "gpt-3.5-turbo",
    val messages: MutableList<ChatMessage> = mutableListOf()
) {
    fun add(message: ChatMessage) {
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        messages.add(message)
        val response = ChatCompletionAPI.create(ChatCompletionRequest(model, messages))
        messages.add(response.choices[0].message)
    }

    fun getChatMessages(): List<ChatMessage> = messages

    fun lastAssistantMessage(): ChatAssistantMessage? = messages.lastOrNull { it is ChatAssistantMessage } as? ChatAssistantMessage
    fun lastUserMessage(): ChatMessage? = messages.lastOrNull { it is ChatUserMessage } as? ChatUserMessage
}

fun main() {
    val systemPrompt = ChatSystemMessage(
        "system",
        "You're a helpful assistant being used in a command line interface (CLI)."
    )
    val messages = mutableListOf<ChatMessage>(systemPrompt)
    val chatManager = ChatManager(messages = mutableListOf(systemPrompt))
    while (true) {
        val input = readlnOrNull();
        if (input.isNullOrEmpty()) {
            println("Goodbye")
            return
        }
        val userMessage = ChatUserMessage("user", input)
        chatManager.add(userMessage)
        println(chatManager.lastAssistantMessage()?.content)
    }
}