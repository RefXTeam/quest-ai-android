package com.chroniclequest.domain.model

/**
 * A lightweight, non-trackable nudge produced by the Gemini `sendInsightTip`
 * tool call. Unlike a [Quest] it has no verification or reward — it is shown and
 * dismissed.
 */
data class InsightTip(
    val id: Long,
    val message: String,
    val createdAt: Long,
)
