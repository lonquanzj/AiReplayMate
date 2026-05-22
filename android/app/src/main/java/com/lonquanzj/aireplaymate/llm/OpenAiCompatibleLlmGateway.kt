package com.lonquanzj.aireplaymate.llm

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.LlmSampling
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.settings.AppSettingsValidator
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleLlmGateway(
    private val settings: AppSettings
) : LlmGateway {
    override suspend fun generateReplies(request: LlmRequest): Result<List<ReplyCandidate>> {
        val validation = AppSettingsValidator.validate(settings)
        if (!validation.canRequest) {
            val error = IllegalStateException(validation.errors.joinToString("；"))
            LlmDebugStore.onSkipped(
                baseUrl = settings.baseUrl,
                model = settings.model,
                reason = error.message.orEmpty()
            )
            return Result.failure(error)
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val requestJson = OpenAiCompatibleRequestMapper.toOpenAiJson(request, settings.model)
                LlmDebugStore.onRequestStarted(
                    baseUrl = OpenAiCompatibleRequestMapper.chatCompletionsUrl(settings),
                    model = settings.model,
                    requestPreview = requestJson.toString()
                )
                val connection = (URL(OpenAiCompatibleRequestMapper.chatCompletionsUrl(settings)).openConnection() as HttpURLConnection)
                    .apply {
                        requestMethod = "POST"
                        connectTimeout = CONNECT_TIMEOUT_MS
                        readTimeout = READ_TIMEOUT_MS
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Accept", "application/json")
                    }

                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestJson.toString())
                }

                val responseText = connection.readResponseText()
                LlmDebugStore.onHttpReturned(
                    status = connection.responseCode,
                    responseText = responseText
                )
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("LLM 请求失败：HTTP ${connection.responseCode} ${responseText.take(160)}")
                }

                val content = JSONObject(responseText)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                val candidates = LlmResponseParser.parseCandidates(
                    rawContent = content,
                    requestedCount = request.candidateCount,
                    sourceModel = settings.model
                )
                if (candidates.size < request.candidateCount) {
                    throw IllegalStateException("LLM 返回候选不足")
                }
                LlmDebugStore.onParsed(
                    candidateCount = candidates.size,
                    contentPreview = content
                )
                candidates
            }.onFailure(LlmDebugStore::onFailed)
        }
    }

    private fun HttpURLConnection.readResponseText(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText).orEmpty()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

internal object OpenAiCompatibleRequestMapper {
    fun toOpenAiJson(
        request: LlmRequest,
        model: String
    ): JSONObject {
        return JSONObject()
            .put("model", model)
            .put("temperature", LlmSampling.normalizedTemperatureDouble(request.temperature))
            .put("max_tokens", request.maxTokens)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", request.systemPrompt)
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", request.userPrompt)
                    )
            )
    }

    fun chatCompletionsUrl(settings: AppSettings): String {
        val normalized = settings.baseUrl.trim().trimEnd('/')
        return when {
            normalized.endsWith("/chat/completions") -> normalized
            normalized.endsWith("/v1") -> "$normalized/chat/completions"
            else -> "$normalized/v1/chat/completions"
        }
    }
}
