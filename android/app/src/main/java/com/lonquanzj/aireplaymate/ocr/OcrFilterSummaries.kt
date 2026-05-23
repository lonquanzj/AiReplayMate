package com.lonquanzj.aireplaymate.ocr

internal fun List<OcrDroppedLine>.toFilterSummaries(): List<OcrFilterSummary> {
    return groupBy { it.reason }
        .map { (reason, lines) ->
            OcrFilterSummary(
                reason = reason,
                count = lines.size,
                samples = lines
                    .map { it.text.toSampleText() }
                    .distinct()
                    .take(MAX_FILTER_SAMPLES_PER_REASON)
            )
        }
        .sortedWith(compareByDescending<OcrFilterSummary> { it.count }.thenBy { it.reason.ordinal })
}

private fun String.toSampleText(): String {
    val normalized = normalizeText(this)
    if (normalized.isBlank()) return "(空)"
    return normalized.take(MAX_FILTER_SAMPLE_LENGTH)
}
