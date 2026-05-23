package com.lonquanzj.aireplaymate.ocr

import com.lonquanzj.aireplaymate.accessibility.ChatRole

object OcrTextPostProcessor {
    fun toChatMessages(
        lines: List<OcrRecognizedLine>,
        screenWidth: Int,
        screenHeight: Int
    ): OcrPostProcessResult {
        val cleanedResults = lines.map { it.clean(screenWidth, screenHeight) }
        val dropped = cleanedResults.mapNotNull { it.dropped }.toMutableList()
        val cleanedLines = cleanedResults
            .mapNotNull { it.candidate }
            .resolveImagePlaceholderRoles(screenWidth, screenHeight)
            .sortedWith(compareBy<OcrLineCandidate> { it.bounds.top }.thenBy { it.bounds.left })

        val grouped = cleanedLines
            .fold(mutableListOf<OcrBubbleCandidate>()) { groups, line ->
                val last = groups.lastOrNull()
                if (last != null && last.canMerge(line, screenHeight)) {
                    last.add(line)
                } else {
                    groups += OcrBubbleCandidate(line)
                }
                groups
            }

        val messages = grouped
            .mapNotNull { it.toChatMessage() }
            .takeLast(MAX_OCR_MESSAGES)

        return OcrPostProcessResult(
            messages = messages,
            rawLineCount = lines.size,
            keptLineCount = cleanedLines.size,
            droppedLineCount = dropped.size,
            filterSummaries = dropped.toFilterSummaries()
        )
    }
}

private fun List<OcrLineCandidate>.resolveImagePlaceholderRoles(
    screenWidth: Int,
    screenHeight: Int
): List<OcrLineCandidate> {
    if (isEmpty()) return this
    val known = filter { it.role != ChatRole.UNKNOWN }
    val maxNearbyDistance = (screenHeight * MAX_NEARBY_ROLE_DISTANCE_RATIO).toInt().coerceAtLeast(160)

    return map { candidate ->
        if (candidate.text != IMAGE_PLACEHOLDER_TEXT || candidate.role != ChatRole.UNKNOWN) {
            return@map candidate
        }

        val nearestKnownRole = known
            .map { knownLine ->
                val dy = kotlin.math.abs(knownLine.bounds.centerY() - candidate.bounds.centerY())
                val dx = kotlin.math.abs(knownLine.bounds.centerX() - candidate.bounds.centerX())
                knownLine.role to (dy * 2 + dx)
            }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= maxNearbyDistance * 2 }
            ?.first

        val inferredRole = nearestKnownRole ?: if (candidate.bounds.centerX() >= screenWidth / 2) {
            ChatRole.ME
        } else {
            ChatRole.FRIEND
        }

        candidate.copy(role = inferredRole)
    }
}
