package com.example.dronegpt.chat

class ChatManager(
    val model: String = "gpt-3.5-turbo",
    val messages: MutableList<ChatMessage> = mutableListOf()
) {
    fun add(message: ChatMessage) {
        messages.add(message)
        val response = ChatCompletionAPI.create(ChatCompletionRequest(model, messages))
        messages.add(response.choices[0].message)
    }

    fun getChatMessages(): List<ChatMessage> = messages

    fun lastAssistantMessage(): ChatMessage? = messages.lastOrNull { it.role == "assistant" }
    fun lastUserMessage(): ChatMessage? = messages.lastOrNull { it.role == "user" }
}

fun main() {
    val systemPrompt = ChatMessage(
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
        val userMessage = ChatMessage("user", input)
        chatManager.add(userMessage)
        println(chatManager.lastAssistantMessage()?.content)
    }
}