package com.lonquanzj.aireplaymate.ocr

object OcrTextPostProcessor {
    fun toChatMessages(
        lines: List<OcrRecognizedLine>,
        screenWidth: Int,
        screenHeight: Int
    ): OcrPostProcessResult {
        val cleanedResults = lines.map { it.clean(screenWidth, screenHeight) }
        val initiallyKept = cleanedResults.mapNotNull { it.candidate }
        val dropped = cleanedResults.mapNotNull { it.dropped }.toMutableList()
        val seenTexts = mutableSetOf<String>()
        val cleanedLines = initiallyKept
            .filter { candidate ->
                val normalized = normalizeText(candidate.text)
                val isNew = seenTexts.add(normalized)
                if (!isNew) {
                    dropped += OcrDroppedLine(
                        reason = OcrFilterReason.DUPLICATE,
                        text = candidate.text
                    )
                }
                isNew
            }
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
