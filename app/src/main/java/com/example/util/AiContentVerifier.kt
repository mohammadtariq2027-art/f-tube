package com.example.util

import android.util.Log
import com.example.data.remote.YouTubeDataFetcher
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class AiVerdict(
    val isProductive: Boolean,
    val confidencePercent: Int,
    val detectedCategory: String,
    val reason: String,
    val verifiedByGemini: Boolean = true
)

/**
 * Gemini AI-powered semantic content analyzer for FocusTube using Firebase AI Logic (`Firebase.ai`).
 *
 * Solves two major problems with simple keyword matching:
 * 1. False Positives (Useful videos blocked): Educational, farming, business, coding, or tutorial videos
 *    that happen to include words like "4K", "song", "video", "comedy", "game", or "roast" in their title/description
 *    are recognized by Gemini AI as genuinely productive and ALLOWED.
 * 2. False Negatives (Distractions slipping through): Pure entertainment, pranks, music videos, comedy skits,
 *    or celebrity gossip that use clickbait "study/motivation/business" keywords are recognized by Gemini AI
 *    as distractions and BLOCKED.
 */
object AiContentVerifier {

    private const val TAG = "AiContentVerifier"
    private const val MODEL_NAME = "gemini-3.8-flash"

    private val singleItemSchema = Schema.obj(
        mapOf(
            "isProductive" to Schema.boolean(
                "True if the content is educational, informative, skill-building, farming/agriculture, manufacturing/machinery, business, finance, coding, science, career, spiritual/moral learning, or practical work; false if it is pure entertainment distraction such as music videos, songs, movies, comedy skits, pranks, roasting, casual gaming, or celebrity gossip."
            ),
            "confidencePercent" to Schema.integer("Confidence score from 0 to 100"),
            "detectedCategory" to Schema.string("Short English category name, e.g. Farming & Agriculture, Business & Strategy, Coding & Tech, Academic Study, Music & Entertainment, Comedy & Pranks"),
            "reason" to Schema.string("Concise 1-sentence explanation in English of why this content is productive or a distraction, explicitly noting if misleading keywords like 4K/song/comedy/study were evaluated in context.")
        )
    )

    private val batchFilterSchema = Schema.obj(
        mapOf(
            "allowedIndices" to Schema.array(
                Schema.integer("0-based index of each video from the input list that is genuinely productive, educational, business, farming, technical, or informative.")
            ),
            "blockedCount" to Schema.integer("Number of distraction videos filtered out"),
            "summary" to Schema.string("Brief 1-sentence English summary of the AI filtering result")
        )
    )

    private val singleItemModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleItemSchema
            }
        )
    }

    private val batchFilterModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = batchFilterSchema
            }
        )
    }

    /**
     * Evaluates a single YouTube video using Gemini AI semantic understanding.
     */
    suspend fun analyzeVideoWithAi(
        title: String,
        channelName: String,
        description: String = ""
    ): AiVerdict {
        val cleanTitle = title.trim()
        val cleanChannel = channelName.trim()
        val cleanDesc = description.trim().take(600)

        val prompt = """
            You are the AI Focus Shield for FocusTube, a distraction-free learning and productivity app.
            Analyze the following YouTube video metadata based on its TRUE intent and context, NOT superficial keywords:
            - Video Title: "$cleanTitle"
            - Channel Name: "$cleanChannel"
            - Description Snippet: "$cleanDesc"

            CRITICAL CONTEXT RULES:
            1. ALLOW (isProductive = true) all genuine educational, academic study, competitive exam, farming/agriculture, dairy/livestock, manufacturing/machinery, business, finance, marketing, e-commerce, coding/software, science, DIY repair, health/fitness, spiritual/moral lectures, and skill-building videos — EVEN IF their title contains words like "4K", "8K", "video", "song", "music", "comedy", "funny", "game", or "roast" used harmlessly or metaphorically (for example: "4K Drone Rice Harvesting Video", "How Music Algorithms Work in Python", "Funny Mistakes in Business Marketing", "Game Theory Explained").
            2. BLOCK (isProductive = false) pure entertainment distractions such as actual music videos/songs, movie trailers/full movies, stand-up comedy/prank skits, roasting videos, casual gaming streams (PUBG/GTA/Free Fire gameplay), and celebrity gossip/drama — EVEN IF they include clickbait words like "study", "class", "important", "motivation", or "lesson" to disguise a distraction.
            3. Respond strictly in English matching the JSON schema.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                singleItemModel.generateContent(prompt)
            }
            parseSingleVerdict(response.text, fallbackTitle = cleanTitle, fallbackChannel = cleanChannel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini AI video analysis fallback for '$cleanTitle': ${e.message}")
            fallbackVideoVerdict(cleanTitle, cleanChannel, cleanDesc)
        }
    }

    /**
     * Evaluates a YouTube playlist using Gemini AI semantic understanding.
     */
    suspend fun analyzePlaylistWithAi(
        playlistTitle: String,
        sampleVideoTitles: List<String>,
        sampleChannelNames: List<String>
    ): AiVerdict {
        val sampleLines = sampleVideoTitles.take(20).mapIndexed { index, vidTitle ->
            val ch = sampleChannelNames.getOrNull(index).orEmpty()
            "${index + 1}. \"$vidTitle\" by $ch"
        }.joinToString("\n")

        val prompt = """
            You are the AI Focus Shield for FocusTube, a distraction-free learning and productivity app.
            Analyze the following YouTube Playlist based on the overall meaning and intent of its title and sample videos:
            - Playlist Title: "${playlistTitle.trim()}"
            - Sample Videos in Playlist:
            $sampleLines

            CRITICAL CONTEXT RULES:
            1. ALLOW (isProductive = true) if this playlist is primarily for learning, studying, farming/agriculture, manufacturing/machinery, business, coding, career growth, science, or practical skills — even if some titles mention "4K", "video", "song", or "comedy" in an educational/documentary context.
            2. BLOCK (isProductive = false) if this playlist is primarily a collection of entertainment songs, music videos, movies, comedy skits, pranks, roasting, or casual gaming streams — even if the playlist title uses a misleading label like "Study" or "Important".
            3. Respond strictly in English matching the JSON schema.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                singleItemModel.generateContent(prompt)
            }
            parseSingleVerdict(response.text, fallbackTitle = playlistTitle, fallbackChannel = sampleChannelNames.firstOrNull().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini AI playlist analysis fallback for '$playlistTitle': ${e.message}")
            val heuristic = FocusShield.validatePlaylist(playlistTitle, sampleVideoTitles, sampleChannelNames)
            when (heuristic) {
                is ValidationResult.Allowed -> AiVerdict(
                    isProductive = true,
                    confidencePercent = 85,
                    detectedCategory = "Productive & Learning",
                    reason = "Verified as productive learning/work playlist.",
                    verifiedByGemini = false
                )
                is ValidationResult.Blocked -> AiVerdict(
                    isProductive = false,
                    confidencePercent = 88,
                    detectedCategory = "Entertainment Distraction",
                    reason = heuristic.reason,
                    verifiedByGemini = false
                )
            }
        }
    }

    /**
     * Evaluates a YouTube channel using Gemini AI semantic understanding.
     */
    suspend fun analyzeChannelWithAi(
        channelName: String,
        handle: String,
        description: String,
        sampleVideoTitles: List<String>
    ): AiVerdict {
        val sampleLines = sampleVideoTitles.take(20).mapIndexed { idx, t -> "${idx + 1}. \"$t\"" }.joinToString("\n")
        val prompt = """
            You are the AI Focus Shield for FocusTube, a distraction-free learning and productivity app.
            Analyze the following YouTube Channel based on its real subject matter and sample uploads:
            - Channel Name: "${channelName.trim()}"
            - Handle: "${handle.trim()}"
            - Channel Description: "${description.trim().take(500)}"
            - Recent Video Titles:
            $sampleLines

            CRITICAL CONTEXT RULES:
            1. ALLOW (isProductive = true) if the channel teaches or shares knowledge about farming/agriculture, machinery/manufacturing, business/finance, marketing/e-commerce, coding/technology, academics/exams, science, health, spiritual/moral teachings, or practical skills — even if titles include "4K", "vlog", "song", or "comedy" in a harmless context.
            2. BLOCK (isProductive = false) if the channel is a music label, singer channel, movie clip channel, stand-up comedy/prank channel, roasting channel, casual gaming channel, or celebrity gossip channel.
            3. Respond strictly in English matching the JSON schema.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                singleItemModel.generateContent(prompt)
            }
            parseSingleVerdict(response.text, fallbackTitle = channelName, fallbackChannel = handle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini AI channel analysis fallback for '$channelName': ${e.message}")
            val heuristic = FocusShield.validateChannel(channelName, handle, description, sampleVideoTitles)
            when (heuristic) {
                is ValidationResult.Allowed -> AiVerdict(
                    isProductive = true,
                    confidencePercent = 85,
                    detectedCategory = "Productive Channel",
                    reason = "Verified as a productive work/education channel.",
                    verifiedByGemini = false
                )
                is ValidationResult.Blocked -> AiVerdict(
                    isProductive = false,
                    confidencePercent = 88,
                    detectedCategory = "Entertainment Distraction",
                    reason = heuristic.reason,
                    verifiedByGemini = false
                )
            }
        }
    }

    /**
     * Filters a list of fetched videos using a smart 2-stage pipeline:
     * - Clearly safe educational/work videos without any ambiguous distraction words pass immediately.
     * - Any video that triggers a keyword flag OR lacks clear work keywords is sent to Gemini AI in a batch
     *   so useful videos with words like "4K", "song", "comedy", "video" are rescued and disguised distractions are blocked!
     */
    suspend fun filterVideosWithAiContext(
        videos: List<YouTubeDataFetcher.FetchedVideo>,
        contextTitle: String = ""
    ): List<YouTubeDataFetcher.FetchedVideo> {
        if (videos.isEmpty()) return emptyList()

        // Identify candidates that need Gemini AI contextual inspection (up to 40 per batch)
        // We inspect any video that either triggered the keyword distraction filter OR doesn't have an obvious work keyword
        val indexedVideos = videos.withIndex().toList()
        val needsAiReview = indexedVideos.filter { (_, v) ->
            FocusShield.isDistractionVideo(v.title, v.channelName) ||
                !FocusShield.isWorkOrProductiveContent("${v.title} ${v.channelName}")
        }.take(40)

        if (needsAiReview.isEmpty()) {
            return videos
        }

        val itemsPrompt = needsAiReview.mapIndexed { batchIdx, (_, v) ->
            "[$batchIdx] Title: \"${v.title.replace("\"", "'")}\" | Channel: \"${v.channelName.replace("\"", "'")}\""
        }.joinToString("\n")

        val prompt = """
            You are the AI Focus Shield for FocusTube (Context: "$contextTitle").
            Review this numbered list of YouTube videos and return the 0-based indices (`allowedIndices`) of ALL videos that are genuinely useful, educational, informative, farming/agriculture, manufacturing/machinery, business, finance, coding, science, career, or skill-building.
            
            IMPORTANT:
            - KEEP (include in `allowedIndices`) useful videos even if their title contains words like "4K", "song", "video", "comedy", "game", or "vlog".
            - EXCLUDE from `allowedIndices` any video that is actually a music song, movie clip, comedy/prank skit, roasting video, casual gameplay stream, or celebrity gossip — even if it uses clickbait study words.

            Videos to review:
            $itemsPrompt
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                batchFilterModel.generateContent(prompt)
            }
            val rawText = response.text.orEmpty()
            val json = JSONObject(rawText)
            val allowedArray: JSONArray = json.optJSONArray("allowedIndices") ?: JSONArray()
            val allowedBatchIndices = mutableSetOf<Int>()
            for (i in 0 until allowedArray.length()) {
                allowedBatchIndices.add(allowedArray.optInt(i, -1))
            }

            // Map batch indices back to original video indices
            val reviewedOriginalIndices = needsAiReview.map { it.index }.toSet()
            val approvedOriginalIndices = needsAiReview.mapIndexedNotNull { batchIdx, indexedValue ->
                if (batchIdx in allowedBatchIndices) indexedValue.index else null
            }.toSet()

            videos.filterIndexed { originalIndex, video ->
                if (originalIndex in reviewedOriginalIndices) {
                    originalIndex in approvedOriginalIndices
                } else {
                    !FocusShield.isDistractionVideo(video.title, video.channelName)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Gemini AI batch filter fallback: ${e.message}")
            videos.filterNot { FocusShield.isDistractionVideo(it.title, it.channelName) }
        }
    }

    private fun parseSingleVerdict(
        rawJson: String?,
        fallbackTitle: String,
        fallbackChannel: String
    ): AiVerdict {
        if (rawJson.isNullOrBlank()) {
            return fallbackVideoVerdict(fallbackTitle, fallbackChannel, "")
        }
        val json = JSONObject(rawJson)
        val isProductive = json.optBoolean("isProductive", true)
        val confidence = json.optInt("confidencePercent", 90).coerceIn(1, 100)
        val category = json.optString("detectedCategory", if (isProductive) "Productive & Learning" else "Entertainment Distraction")
        val reason = json.optString(
            "reason",
            if (isProductive) "Gemini AI verified this content as useful and educational in context."
            else "Gemini AI identified this content as entertainment distraction."
        )
        return AiVerdict(
            isProductive = isProductive,
            confidencePercent = confidence,
            detectedCategory = category,
            reason = reason,
            verifiedByGemini = true
        )
    }

    private fun fallbackVideoVerdict(title: String, channelName: String, description: String): AiVerdict {
        val isDistraction = FocusShield.isDistractionVideo(title, channelName) &&
            !FocusShield.isWorkOrProductiveContent("$title $channelName $description")
        return if (!isDistraction) {
            AiVerdict(
                isProductive = true,
                confidencePercent = 84,
                detectedCategory = "Productive Content",
                reason = "Verified as productive/educational content.",
                verifiedByGemini = false
            )
        } else {
            AiVerdict(
                isProductive = false,
                confidencePercent = 88,
                detectedCategory = "Entertainment Distraction",
                reason = "Blocked because title/channel matches entertainment distraction patterns.",
                verifiedByGemini = false
            )
        }
    }
}
