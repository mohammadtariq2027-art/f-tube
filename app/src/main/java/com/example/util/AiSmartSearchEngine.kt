package com.example.util

import com.example.data.local.entity.VideoEntity
import com.example.ui.player.SceneNoteEntry
import com.example.ui.player.parseSceneNotes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class AiSearchMatchResult(
    val video: VideoEntity,
    val score: Double,
    val matchConfidencePercent: Int,
    val matchReasonLabel: String,
    val bestMatchedSceneNotes: List<SceneNoteEntry>
) {
    val confidencePercent: Int get() = matchConfidencePercent
    val matchedSceneNotes: List<SceneNoteEntry> get() = bestMatchedSceneNotes
}

/**
 * AI-Powered Semantic, Phonetic (Hindi/Hinglish/English), Fuzzy & Approximate-Timestamp Search Engine.
 * Allows users to type a rough idea, approximate phrase, spelling mistake, Hinglish/Hindi description,
 * or approximate minute ("6 minute ke aas paas") and still find the right video and scene note.
 */
object AiSmartSearchEngine {

    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "in", "on", "at", "to", "for", "of", "with",
        "by", "from", "about", "how", "what", "why", "when", "where", "which", "who", "this", "that",
        "it", "and", "or", "but", "if", "so", "video", "videos", "note", "notes", "mera", "meri",
        "mere", "ka", "ki", "ke", "ko", "se", "me", "mein", "par", "pe", "hai", "hain", "tha", "thi",
        "the", "ho", "hota", "hoti", "kya", "kaise", "kahan", "kab", "kyu", "kyun", "wala", "wali",
        "wale", "aur", "ya", "bhi", "toh", "jo", "vo", "woh", "ye", "yeh", "kuch", "koi", "ek",
        "का", "की", "के", "को", "से", "में", "पर", "पे", "है", "हैं", "था", "थी", "थे", "हो", "होता",
        "होती", "क्या", "कैसे", "कहाँ", "कब", "क्यों", "वाला", "वाली", "वाले", "और", "या", "भी",
        "तो", "जो", "वो", "वह", "ये", "यह", "कुछ", "कोई", "एक", "वीडियो", "नोट", "नोट्स", "बताया",
        "गया", "करना", "करने", "लगाने", "बढ़ाने", "जिसमें", "बारे"
    )

    // Bilingual Concept Knowledge Graph (Hindi + Hinglish + English semantic clusters)
    private val SEMANTIC_CLUSTERS: List<Set<String>> = listOf(
        setOf(
            "study", "studying", "learn", "learning", "education", "educational", "padhai", "padhna",
            "seekhna", "sikhe", "exam", "exams", "revision", "student", "lecture", "class", "course",
            "tutorial", "lesson", "syllabus", "preparation", "taiyari", "पढ़ाई", "पढ़ना", "सीखना",
            "परीक्षा", "तैयारी", "शिक्षा", "क्लास", "लेक्चर", "कोर्स", "छात्र"
        ),
        setOf(
            "focus", "concentration", "attention", "mind", "brain", "memory", "remember", "yaad",
            "yaaddasht", "dhyan", "man", "dimag", "distraction", "meditation", "deepwork", "productive",
            "productivity", "ध्यान", "दिमाग", "मन", "याद", "याददाश्त", "एकाग्रता", "फोकस", "मेमोरी"
        ),
        setOf(
            "time", "management", "routine", "habit", "habits", "schedule", "timetable", "discipline",
            "morning", "night", "samay", "waqt", "aadat", "dincharya", "समय", "वक्त", "आदत",
            "आदतें", "दिनचर्या", "अनुशासन", "रूटीन", "टाइम"
        ),
        setOf(
            "money", "finance", "financial", "invest", "investing", "investment", "share", "stock",
            "market", "trading", "mutual", "fund", "sip", "compounding", "wealth", "rich", "income",
            "earning", "paisa", "paise", "kamai", "nivesh", "bazar", "byaj", "profit", "loss", "tax",
            "budget", "saving", "पैसा", "पैसे", "कमाई", "निवेश", "शेयर", "बाजार", "मार्केट", "ब्याज",
            "मुनाफा", "बचत", "टैक्स", "फाइनेंस"
        ),
        setOf(
            "business", "startup", "entrepreneur", "company", "marketing", "sales", "customer",
            "brand", "agency", "freelance", "freelancing", "vyapar", "dhandha", "व्यापार", "बिजनेस",
            "स्टार्टअप", "मार्केटिंग", "ग्राहक"
        ),
        setOf(
            "code", "coding", "programming", "programmer", "developer", "software", "web", "app",
            "android", "kotlin", "java", "python", "javascript", "react", "html", "css", "sql",
            "database", "algorithm", "dsa", "computer", "tech", "technology", "ai", "artificial",
            "intelligence", "machine", "learning", "कोडिंग", "प्रोग्रामिंग", "कंप्यूटर", "सॉफ्टवेयर",
            "मोबाइल", "टेक्नोलॉजी", "एआई", "वेबसाइट"
        ),
        setOf(
            "health", "healthy", "fitness", "workout", "exercise", "gym", "yoga", "diet", "nutrition",
            "food", "weight", "fat", "muscle", "sleep", "body", "disease", "doctor", "medicine",
            "sehat", "swasthya", "kasrat", "vyayam", "khana", "neend", "bimari", "ilaj", "vajan",
            "सेहत", "स्वास्थ्य", "योग", "कसरत", "व्यायाम", "खाना", "डाइट", "नींद", "वजन", "शरीर", "बीमारी"
        ),
        setOf(
            "career", "job", "jobs", "interview", "resume", "cv", "salary", "placement", "hiring",
            "skill", "skills", "communication", "speaking", "english", "confidence", "personality",
            "naukri", "safalta", "success", "नौकरी", "करियर", "इंटरव्यू", "सफलता", "कौशल", "अंग्रेजी",
            "आत्मविश्वास", "पर्सनालिटी"
        ),
        setOf(
            "math", "maths", "mathematics", "formula", "calculus", "algebra", "geometry", "number",
            "calculation", "ganit", "sawal", "गणित", "मैथ्स", "फॉर्मूला", "सवाल", "हिसाब"
        ),
        setOf(
            "science", "physics", "chemistry", "biology", "space", "universe", "atom", "energy",
            "gravity", "vigyan", "विज्ञान", "फिजिक्स", "केमिस्ट्री", "बायोलॉजी", "अंतरिक्ष"
        ),
        setOf(
            "history", "geography", "polity", "constitution", "economics", "gk", "general", "knowledge",
            "upsc", "ias", "ssc", "railway", "govt", "government", "itihas", "bhugol", "samvidhan",
            "इतिहास", "भूगोल", "संविधान", "अर्थव्यवस्था", "सरकारी", "यूपीएससी"
        ),
        setOf(
            "important", "key", "main", "point", "summary", "conclusion", "secret", "trick", "tricks",
            "tip", "tips", "hack", "idea", "concept", "rule", "kaam", "baat", "jaruri", "zaroori",
            "khas", "mukhya", "nishkarsh", "tarika", "तरीका", "ट्रिक", "काम", "बात", "जरूरी", "महत्वपूर्ण",
            "खास", "मुख्य", "निष्कर्ष", "सारांश", "नियम"
        ),
        setOf(
            "motivation", "motivational", "inspire", "inspiration", "life", "mindset", "positive",
            "thinking", "goal", "goals", "dream", "lakshya", "prerna", "zindagi", "jeevan", "soch",
            "प्रेरणा", "मोटिवेशन", "लक्ष्य", "जिंदगी", "जीवन", "सोच", "सपने"
        )
    )

    private val APPROX_MINUTES_REGEX = Regex("""(\d{1,3})\s*(?:min|mins|minute|minutes|मिनट|मि)""", RegexOption.IGNORE_CASE)
    private val APPROX_SECONDS_REGEX = Regex("""(\d{1,2})\s*(?:sec|secs|second|seconds|सेकंड|सेकेंड)""", RegexOption.IGNORE_CASE)
    private val COLON_TIME_REGEX = Regex("""(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?""")

    /**
     * Converts Devanagari text into a phonetic Roman representation so Hindi & Hinglish match seamlessly.
     */
    fun toPhoneticRoman(input: String): String {
        val sb = StringBuilder(input.length * 2)
        for (ch in input.lowercase()) {
            when (ch) {
                'अ', 'आ', 'ा' -> sb.append('a')
                'इ', 'ई', 'ि', 'ी' -> sb.append('i')
                'उ', 'ऊ', 'ु', 'ू' -> sb.append('u')
                'ए', 'ऐ', 'े', 'ै' -> sb.append('e')
                'ओ', 'औ', 'ो', 'ौ' -> sb.append('o')
                'ऋ', 'ृ' -> sb.append("ri")
                'ं', 'ँ' -> sb.append('n')
                'ः' -> sb.append('h')
                'क' -> sb.append('k')
                'ख' -> sb.append("kh")
                'ग' -> sb.append('g')
                'घ' -> sb.append("gh")
                'च' -> sb.append("ch")
                'छ' -> sb.append("chh")
                'ज' -> sb.append('j')
                'झ' -> sb.append("jh")
                'ट', 'त' -> sb.append('t')
                'ठ', 'थ' -> sb.append("th")
                'ड', 'द' -> sb.append('d')
                'ढ', 'ध' -> sb.append("dh")
                'ण', 'न' -> sb.append('n')
                'प' -> sb.append('p')
                'फ' -> sb.append('f')
                'ब' -> sb.append('b')
                'भ' -> sb.append("bh")
                'म' -> sb.append('m')
                'य' -> sb.append('y')
                'र' -> sb.append('r')
                'ल' -> sb.append('l')
                'व' -> sb.append('v')
                'श', 'ष' -> sb.append("sh")
                'स' -> sb.append('s')
                'ह' -> sb.append('h')
                '्', '़' -> { /* halant / nukta */ }
                else -> sb.append(ch)
            }
        }
        // Collapse repeated vowels/consonants for smoother fuzzy phonetic comparison
        return sb.toString()
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("oo", "u")
            .replace("w", "v")
            .replace("z", "j")
            .replace("ph", "f")
    }

    /**
     * Extracts approximate target seconds if the user typed a time expression like
     * "6:02", "6 minute", "6 min 2 sec", "6 मिनट पर".
     */
    fun extractTargetTimestampSeconds(query: String): Int? {
        val colonMatch = COLON_TIME_REGEX.find(query)
        if (colonMatch != null) {
            val p1 = colonMatch.groupValues[1].toIntOrNull() ?: return null
            val p2 = colonMatch.groupValues[2].toIntOrNull() ?: return null
            val p3 = colonMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            return if (p3 != null) p1 * 3600 + p2 * 60 + p3 else p1 * 60 + p2
        }
        val minMatch = APPROX_MINUTES_REGEX.find(query)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toIntOrNull() ?: return null
            val secs = APPROX_SECONDS_REGEX.find(query)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            return mins * 60 + secs
        }
        return null
    }

    fun tokenizeMeaningful(text: String): List<String> {
        return text.lowercase()
            .split(Regex("""[^\p{L}\p{M}\p{N}:]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in STOP_WORDS }
    }

    fun expandTokenSynonyms(token: String): Set<String> {
        val phonetic = toPhoneticRoman(token)
        val expanded = mutableSetOf(token, phonetic)
        for (cluster in SEMANTIC_CLUSTERS) {
            val matchesCluster = cluster.any { member ->
                val memberPhonetic = toPhoneticRoman(member)
                member == token || memberPhonetic == phonetic ||
                    (token.length >= 4 && (member.startsWith(token) || token.startsWith(member))) ||
                    (phonetic.length >= 4 && (memberPhonetic.startsWith(phonetic) || phonetic.startsWith(memberPhonetic)))
            }
            if (matchesCluster) {
                expanded.addAll(cluster)
                cluster.forEach { expanded.add(toPhoneticRoman(it)) }
            }
        }
        return expanded
    }

    /**
     * Computes normalized similarity (0.0 to 1.0) between two words using character bigrams (Dice) + Levenshtein.
     */
    fun wordSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a.contains(b) || b.contains(a)) {
            val shorter = min(a.length, b.length).toDouble()
            val longer = max(a.length, b.length).toDouble()
            if (shorter >= 3) return (0.78 + 0.22 * (shorter / longer)).coerceAtMost(0.98)
        }
        val pa = toPhoneticRoman(a)
        val pb = toPhoneticRoman(b)
        if (pa == pb && pa.length >= 3) return 0.95
        if (pa.length >= 3 && pb.length >= 3 && (pa.contains(pb) || pb.contains(pa))) {
            return 0.85
        }

        // Bigram Dice coefficient on phonetic forms
        val bigramsA = (0 until pa.length - 1).map { pa.substring(it, it + 2) }.toSet()
        val bigramsB = (0 until pb.length - 1).map { pb.substring(it, it + 2) }.toSet()
        val dice = if (bigramsA.isNotEmpty() && bigramsB.isNotEmpty()) {
            val intersection = bigramsA.intersect(bigramsB).size
            (2.0 * intersection) / (bigramsA.size + bigramsB.size)
        } else 0.0

        // Levenshtein ratio for typo tolerance
        val maxLen = max(pa.length, pb.length)
        val editDist = levenshtein(pa, pb)
        val levRatio = if (maxLen > 0) 1.0 - (editDist.toDouble() / maxLen.toDouble()) else 0.0

        return max(dice, levRatio)
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        if (lhsLen == 0) return rhsLen
        if (rhsLen == 0) return lhsLen
        if (abs(lhsLen - rhsLen) > 5) return max(lhsLen, rhsLen)

        var cost = IntArray(lhsLen + 1) { it }
        var newCost = IntArray(lhsLen + 1)

        for (i in 1..rhsLen) {
            newCost[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }

    /**
     * Scores a single text string (such as a playlist title or scene note line) against a query (0.0f..1.0f).
     */
    fun scoreTextAgainstQuery(text: String, rawQuery: String): Float {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return 1.0f
        val t = text.trim().lowercase()
        if (t.isEmpty()) return 0.0f
        if (t.contains(q)) return 1.0f
        val qp = toPhoneticRoman(q)
        val tp = toPhoneticRoman(t)
        if (qp.length >= 2 && tp.contains(qp)) return 0.95f

        val qTokens = tokenizeMeaningful(q).ifEmpty { q.split(Regex("""\s+""")).filter { it.isNotBlank() } }
        val tTokens = tokenizeMeaningful(t).ifEmpty { t.split(Regex("""\s+""")).filter { it.isNotBlank() } }
        if (qTokens.isEmpty() || tTokens.isEmpty()) return 0.0f

        var sum = 0.0
        for (qt in qTokens) {
            val syns = expandTokenSynonyms(qt)
            var best = 0.0
            for (tw in tTokens) {
                val twp = toPhoneticRoman(tw)
                if (tw == qt || twp == toPhoneticRoman(qt)) {
                    best = 1.0
                    break
                } else if (tw in syns || twp in syns) {
                    best = max(best, 0.85)
                } else {
                    best = max(best, wordSimilarity(qt, tw))
                }
            }
            sum += best
        }
        return (sum / qTokens.size.toDouble()).toFloat()
    }

    /**
     * Ranks and filters videos + notes using AI semantic, phonetic, fuzzy, and timestamp matching.
     *
     * @param searchTargetMode "BOTH", "NOTES_ONLY", or "VIDEOS_ONLY"
     */
    fun searchVideosAndNotes(
        videos: List<VideoEntity>,
        rawQuery: String,
        searchTargetMode: String = "BOTH",
        videoToPlaylistsNames: Map<String, String> = emptyMap(),
        videoToNotesMap: Map<String, String> = emptyMap()
    ): List<AiSearchMatchResult> {
        val query = rawQuery.trim()
        if (query.isBlank()) {
            return videos.mapNotNull { v ->
                val noteText = videoToNotesMap[v.videoId] ?: v.notes
                if (searchTargetMode == "NOTES_ONLY" && noteText.isBlank()) return@mapNotNull null
                val parsed = parseSceneNotes(noteText)
                AiSearchMatchResult(
                    video = v,
                    score = 1.0,
                    matchConfidencePercent = 100,
                    matchReasonLabel = "",
                    bestMatchedSceneNotes = parsed.take(2)
                )
            }
        }

        val queryLower = query.lowercase()
        val queryPhonetic = toPhoneticRoman(queryLower)
        val queryTokens = tokenizeMeaningful(queryLower).ifEmpty {
            queryLower.split(Regex("""\s+""")).filter { it.isNotBlank() }
        }
        val expandedQueryMap: Map<String, Set<String>> = queryTokens.associateWith { expandTokenSynonyms(it) }
        val targetTimestampSec: Int? = extractTargetTimestampSeconds(queryLower)

        val results = mutableListOf<AiSearchMatchResult>()

        for (video in videos) {
            val noteText = (videoToNotesMap[video.videoId] ?: video.notes).trim()
            if (searchTargetMode == "NOTES_ONLY" && noteText.isBlank()) continue

            val plNames = videoToPlaylistsNames[video.videoId].orEmpty()
            val videoCorpus = "${video.title} ${video.channelName} $plNames ${video.description}".lowercase()
            val videoPhonetic = toPhoneticRoman(videoCorpus)
            val videoWords = tokenizeMeaningful(videoCorpus)

            val noteCorpus = noteText.lowercase()
            val notePhonetic = toPhoneticRoman(noteCorpus)
            val noteWords = tokenizeMeaningful(noteCorpus)
            val parsedNotes = parseSceneNotes(noteText)

            var videoScore = 0.0
            var noteScore = 0.0
            var reason = ""
            val matchedSceneNotes = mutableListOf<Pair<SceneNoteEntry, Double>>()

            // 1. Direct Substring or Phonetic Substring Match
            if (videoCorpus.contains(queryLower) || (queryPhonetic.length >= 3 && videoPhonetic.contains(queryPhonetic))) {
                videoScore += 1.0
                reason = "✨ सटीक वीडियो मैच"
            }
            if (noteCorpus.isNotBlank() && (noteCorpus.contains(queryLower) || (queryPhonetic.length >= 3 && notePhonetic.contains(queryPhonetic)))) {
                noteScore += 1.15
                reason = "✨ आपके लिखे नोट में मिला"
            }

            // 2. Approximate Timestamp Match (e.g. "6:02", "6 min", "6 मिनट")
            if (targetTimestampSec != null && parsedNotes.isNotEmpty()) {
                for (entry in parsedNotes) {
                    val sec = entry.timestampSeconds ?: continue
                    val diff = abs(sec - targetTimestampSec)
                    if (diff == 0) {
                        noteScore = max(noteScore, 1.3)
                        matchedSceneNotes.add(entry to 1.5)
                        reason = "✨ सीन टाइम [${entry.timestampLabel}] से सीधा मैच"
                    } else if (diff <= 45) {
                        val proximityBoost = 0.95 - (diff.toDouble() / 90.0)
                        noteScore = max(noteScore, proximityBoost)
                        matchedSceneNotes.add(entry to proximityBoost + 0.3)
                        if (reason.isBlank()) {
                            reason = "✨ AI Time Match: ~${entry.timestampLabel} के आस-पास का सीन"
                        }
                    }
                }
            }

            // 3. Semantic Concept + Synonym + Fuzzy Token Matching
            if (queryTokens.isNotEmpty()) {
                var matchedVideoConceptCount = 0.0
                var matchedNoteConceptCount = 0.0
                var usedSynonymOrFuzzyInVideo = false
                var usedSynonymOrFuzzyInNote = false

                for (qToken in queryTokens) {
                    val synonyms = expandedQueryMap[qToken].orEmpty()

                    // Evaluate against Video words
                    var bestTokenVideoSim = 0.0
                    for (vw in videoWords) {
                        val vwPhonetic = toPhoneticRoman(vw)
                        if (vw == qToken || vwPhonetic == toPhoneticRoman(qToken)) {
                            bestTokenVideoSim = 1.0
                            break
                        }
                        if (vw in synonyms || vwPhonetic in synonyms) {
                            bestTokenVideoSim = max(bestTokenVideoSim, 0.86)
                            usedSynonymOrFuzzyInVideo = true
                        } else {
                            val sim = wordSimilarity(qToken, vw)
                            if (sim >= 0.68) {
                                bestTokenVideoSim = max(bestTokenVideoSim, sim * 0.9)
                                usedSynonymOrFuzzyInVideo = true
                            }
                        }
                    }
                    matchedVideoConceptCount += bestTokenVideoSim

                    // Evaluate against Note words & individual Scene Note lines
                    var bestTokenNoteSim = 0.0
                    for (nw in noteWords) {
                        val nwPhonetic = toPhoneticRoman(nw)
                        if (nw == qToken || nwPhonetic == toPhoneticRoman(qToken)) {
                            bestTokenNoteSim = 1.0
                            break
                        }
                        if (nw in synonyms || nwPhonetic in synonyms) {
                            bestTokenNoteSim = max(bestTokenNoteSim, 0.88)
                            usedSynonymOrFuzzyInNote = true
                        } else {
                            val sim = wordSimilarity(qToken, nw)
                            if (sim >= 0.66) {
                                bestTokenNoteSim = max(bestTokenNoteSim, sim * 0.92)
                                usedSynonymOrFuzzyInNote = true
                            }
                        }
                    }
                    matchedNoteConceptCount += bestTokenNoteSim
                }

                val videoConceptRatio = matchedVideoConceptCount / queryTokens.size.toDouble()
                val noteConceptRatio = matchedNoteConceptCount / queryTokens.size.toDouble()

                // Also allow partial concept overlap when user types a multi-word rough idea!
                val videoPartialBoost = if (matchedVideoConceptCount >= 0.72) 0.55 + (0.40 * videoConceptRatio) else 0.0
                val notePartialBoost = if (matchedNoteConceptCount >= 0.70) 0.60 + (0.45 * noteConceptRatio) else 0.0

                if (videoPartialBoost > videoScore) {
                    videoScore = videoPartialBoost
                    if (reason.isBlank()) {
                        reason = if (usedSynonymOrFuzzyInVideo) "🧠 AI Smart Match: मिलते-जुलते अर्थ/विषय से मिला" else "✨ वीडियो के शब्दों से मिला"
                    }
                }
                if (notePartialBoost > noteScore) {
                    noteScore = notePartialBoost
                    if (reason.isBlank() || noteScore >= videoScore) {
                        reason = if (usedSynonymOrFuzzyInNote) "🧠 AI Note Match: आपके नोट के भाव/अर्थ से मिला" else "📝 आपके नोट के शब्दों से मिला"
                    }
                }
            }

            // 4. Score individual Scene Note entries so the most relevant scene note is highlighted first
            for (entry in parsedNotes) {
                if (matchedSceneNotes.any { it.first.lineIndex == entry.lineIndex }) continue
                val lineLower = entry.rawLine.lowercase()
                val linePhonetic = toPhoneticRoman(lineLower)
                val lineWords = tokenizeMeaningful(lineLower)

                var lineSim = 0.0
                if (lineLower.contains(queryLower) || (queryPhonetic.length >= 3 && linePhonetic.contains(queryPhonetic))) {
                    lineSim = 1.2
                } else if (queryTokens.isNotEmpty()) {
                    var sum = 0.0
                    for (qt in queryTokens) {
                        val syns = expandedQueryMap[qt].orEmpty()
                        var bestW = 0.0
                        for (lw in lineWords) {
                            val lwp = toPhoneticRoman(lw)
                            if (lw == qt || lwp == toPhoneticRoman(qt)) {
                                bestW = 1.0
                                break
                            } else if (lw in syns || lwp in syns) {
                                bestW = max(bestW, 0.88)
                            } else {
                                val s = wordSimilarity(qt, lw)
                                if (s >= 0.66) bestW = max(bestW, s)
                            }
                        }
                        sum += bestW
                    }
                    lineSim = sum / queryTokens.size.toDouble()
                }
                if (lineSim >= 0.45) {
                    matchedSceneNotes.add(entry to lineSim)
                }
            }

            val finalScore = when (searchTargetMode) {
                "VIDEOS_ONLY" -> videoScore
                "NOTES_ONLY" -> noteScore
                else -> max(videoScore, noteScore)
            }

            if (finalScore >= 0.48) {
                val sortedSceneNotes = matchedSceneNotes
                    .sortedByDescending { it.second }
                    .map { it.first }
                    .distinctBy { it.lineIndex }
                    .ifEmpty { parsedNotes.take(2) }

                val confidence = (finalScore.coerceIn(0.50, 1.0) * 100).toInt()
                results.add(
                    AiSearchMatchResult(
                        video = video,
                        score = finalScore,
                        matchConfidencePercent = confidence,
                        matchReasonLabel = reason.ifBlank { "🧠 AI Smart Match" },
                        bestMatchedSceneNotes = sortedSceneNotes.take(3)
                    )
                )
            }
        }

        return results.sortedByDescending { it.score }
    }
}
