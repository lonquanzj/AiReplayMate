package com.lonquanzj.aireplaymate.llm

import com.lonquanzj.aireplaymate.prompt.LlmRequest
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate

interface LlmGateway {
    suspend fun generateReplies(request: LlmRequest): Result<List<ReplyCandidate>>
}
