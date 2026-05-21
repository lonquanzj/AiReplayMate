package com.lonquanzj.aireplaymate.demo

enum class DemoAuthor {
    ME,
    FRIEND,
    SYSTEM
}

data class DemoMessage(
    val author: DemoAuthor,
    val content: String
)

data class DemoCandidate(
    val id: String,
    val text: String,
    val tone: String
)
