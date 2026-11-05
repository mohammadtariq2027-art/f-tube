package com.example.util

import java.util.Locale
import java.util.regex.Pattern

object DurationUtils {

    /**
     * Parse spoken duration labels from YouTube accessibility text (e.g. "7 minutes, 36 seconds", "1 hour, 15 minutes")
     */
    fun parseSpokenDuration(text: String): String {
        if (text.isBlank()) return ""
        var h = 0L
        var m = 0L
        var s = 0L

        val hourMatcher = Pattern.compile("(\\d+)\\s+hour", Pattern.CASE_INSENSITIVE).matcher(text)
        if (hourMatcher.find()) h = hourMatcher.group(1)?.toLongOrNull() ?: 0L

        val minMatcher = Pattern.compile("(\\d+)\\s+minute", Pattern.CASE_INSENSITIVE).matcher(text)
        if (minMatcher.find()) m = minMatcher.group(1)?.toLongOrNull() ?: 0L

        val secMatcher = Pattern.compile("(\\d+)\\s+second", Pattern.CASE_INSENSITIVE).matcher(text)
        if (secMatcher.find()) s = secMatcher.group(1)?.toLongOrNull() ?: 0L

        val totalSeconds = h * 3600 + m * 60 + s
        return if (totalSeconds > 0) formatSeconds(totalSeconds) else ""
    }

    /**
     * Parse ISO 8601 duration format (e.g. "PT9M52S", "PT1H2M3S", "PT45S")
     */
    fun parseIsoDuration(iso: String): String? {
        if (!iso.startsWith("PT", ignoreCase = true)) return null
        var hours = 0L
        var minutes = 0L
        var seconds = 0L

        val hourMatcher = Pattern.compile("(\\d+)H", Pattern.CASE_INSENSITIVE).matcher(iso)
        if (hourMatcher.find()) hours = hourMatcher.group(1)?.toLongOrNull() ?: 0L

        val minMatcher = Pattern.compile("(\\d+)M", Pattern.CASE_INSENSITIVE).matcher(iso)
        if (minMatcher.find()) minutes = minMatcher.group(1)?.toLongOrNull() ?: 0L

        val secMatcher = Pattern.compile("(\\d+)S", Pattern.CASE_INSENSITIVE).matcher(iso)
        if (secMatcher.find()) seconds = secMatcher.group(1)?.toLongOrNull() ?: 0L

        val totalSeconds = hours * 3600 + minutes * 60 + seconds
        return if (totalSeconds > 0) formatSeconds(totalSeconds) else null
    }

    /**
     * Formats seconds into HH:MM:SS or MM:SS
     */
    fun formatSeconds(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "00:00"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Cleans video titles so duration or accessibility text is NEVER duplicated in the title.
     * Prevents "Title 7 minutes, 36 seconds by Channel" or "Title - 1:38:36".
     */
    fun cleanVideoTitle(rawTitle: String): String {
        var t = rawTitle.trim()
        // Strip YouTube accessibility suffix: e.g. ' 7 minutes, 36 seconds by Traversy Media'
        t = t.replace(Regex("\\s+\\d+\\s+(?:hours?|hrs?|minutes?|mins?|seconds?|secs?).*$", RegexOption.IGNORE_CASE), "")
        // Strip trailing timestamps like " - 12:34", " (12:34)", " [12:34]", " 12:34"
        t = t.replace(Regex("[\\s\\-|•(\\[]+(?:\\d{1,2}:)?\\d{1,2}:\\d{2}[\\s)\\]]*$"), "")
        // Strip trailing punctuation / separators
        t = t.replace(Regex("[\\s\\-|•:]+$"), "")
        return t.trim().ifBlank { rawTitle.trim() }
    }

    /**
     * Given any video duration string or videoId, returns a clean duration string.
     * Guaranteed to NEVER return "HD", "SHORTS", or fake times.
     */
    fun formatCleanDuration(durationRaw: String?, videoId: String = ""): String {
        val trimmed = durationRaw?.trim().orEmpty()

        // 1. If already valid timestamp like "09:52" or "1:15:30"
        if (trimmed.matches(Regex("^\\d{1,2}:\\d{2}(?::\\d{2})?$")) && !trimmed.equals("00:00")) {
            return trimmed
        }

        // 2. If ISO 8601 duration
        if (trimmed.startsWith("PT", ignoreCase = true)) {
            parseIsoDuration(trimmed)?.let { return it }
        }

        // 3. Spoken duration (e.g. "7 minutes, 36 seconds")
        val spoken = parseSpokenDuration(trimmed)
        if (spoken.isNotBlank()) {
            return spoken
        }

        // 4. If numeric seconds
        val asSeconds = trimmed.toLongOrNull()
        if (asSeconds != null && asSeconds > 0) {
            return formatSeconds(asSeconds)
        }

        return ""
    }

    /**
     * Checks whether a text string represents a relative or absolute upload date.
     */
    fun isDateString(text: String): Boolean {
        val lower = text.lowercase(Locale.US).trim()
        if (lower.isBlank()) return false
        if (lower.contains("view") || lower.contains("subscriber") || lower.contains("watching")) return false
        return lower.contains("ago") ||
                lower.contains("streamed") ||
                lower.contains("premiered") ||
                lower.matches(Regex(".*\\b\\d+\\s*(?:s|m|h|d|w|mo|y|yr)\\b.*")) ||
                lower.contains("yesterday") ||
                lower.contains("today") ||
                lower.contains("hour") ||
                lower.contains("minute") ||
                lower.contains("day") ||
                lower.contains("week") ||
                lower.contains("month") ||
                lower.contains("year") ||
                lower.contains("前")
    }

    /**
     * Parses ISO 8601 published date from YouTube RSS (e.g. "2026-10-12T14:31:51+00:00")
     * into a human-friendly relative time string and epoch millis timestamp.
     */
    fun parseIsoPublishedDate(iso: String): Pair<String, Long> {
        val trimmed = iso.trim()
        if (trimmed.isBlank()) return Pair("", 0L)
        var millis = 0L
        try {
            val cleanIso = if (trimmed.endsWith("Z")) trimmed.replace("Z", "+00:00") else trimmed
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            millis = sdf.parse(cleanIso)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val cleanFallback = trimmed.substringBefore("+").substringBefore("Z")
                val sdfFallback = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                millis = sdfFallback.parse(cleanFallback)?.time ?: 0L
            } catch (_: Exception) {}
        }
        if (millis <= 0L) return Pair("", 0L)
        val relative = formatTimeAgo(millis)
        return Pair(relative, millis)
    }

    /**
     * Parses relative YouTube time labels (e.g. "7 days ago", "7d ago", "Streamed 2 weeks ago")
     * and derives the estimated epoch millis timestamp.
     */
    fun parseRelativeTime(raw: String, fallbackNow: Long = System.currentTimeMillis()): Pair<String, Long> {
        var clean = raw.trim()
        clean = clean.replace(Regex("^(?:Streamed|Premiered)\\s+", RegexOption.IGNORE_CASE), "").trim()
        if (clean.isBlank()) return Pair("", 0L)

        clean = clean
            .replace("年前", " years ago")
            .replace("個月前", " months ago")
            .replace("天前", " days ago")
            .replace("小時前", " hours ago")
            .replace("分鐘前", " minutes ago")

        val m = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)").matcher(clean)
        if (m.find()) {
            val num = m.group(1)?.toLongOrNull() ?: 1L
            val unit = m.group(2)?.lowercase(Locale.US).orEmpty()
            val mult: Long = when {
                unit.startsWith("s") -> 1000L
                unit.startsWith("m") && !unit.startsWith("mo") -> 60 * 1000L
                unit.startsWith("h") -> 3600 * 1000L
                unit.startsWith("d") -> 86400 * 1000L
                unit.startsWith("w") -> 7 * 86400 * 1000L
                unit.startsWith("mo") -> 30L * 86400 * 1000L
                unit.startsWith("y") -> 365L * 86400 * 1000L
                else -> 86400 * 1000L
            }
            val ts = fallbackNow - (num * mult)
            val display = if (clean.endsWith("ago", ignoreCase = true)) clean else "$clean ago"
            return Pair(display, ts)
        }

        return Pair(clean, fallbackNow)
    }

    /**
     * Formats an epoch millisecond timestamp into a human-readable relative time.
     */
    fun formatTimeAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0L) return ""
        val diff = now - timestamp
        if (diff < 0) return "Just now"
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
        val years = days / 365

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes min${if (minutes > 1) "s" else ""} ago"
            hours < 24 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            days < 7 -> "$days day${if (days > 1) "s" else ""} ago"
            weeks < 5 -> "$weeks week${if (weeks > 1) "s" else ""} ago"
            months < 12 -> "$months month${if (months > 1) "s" else ""} ago"
            else -> "$years year${if (years > 1) "s" else ""} ago"
        }
    }
}
