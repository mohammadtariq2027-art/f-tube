package com.example.util

data class CuratedChannel(
    val name: String,
    val handle: String,
    val category: String,
    val description: String
)

sealed class ValidationResult {
    object Allowed : ValidationResult()
    data class Blocked(val reason: String) : ValidationResult()
}

/**
 * FocusShield provides distraction blocking for FocusTube.
 * Blocks entertainment, movies, songs/music, comedy, roasting, casual gaming,
 * and useless/sensational news/gossip.
 *
 * Fully allows and prioritizes productive work, farming (खेती), manufacturing (उद्योग),
 * fabric/textiles, machinery, business, skills, trades, tutorials, and education channels.
 */
object FocusShield {

    const val SHIELD_STATUS_LABEL = "Strict Focus Shield"
    const val SHIELD_PERMANENT_NOTE = "Permanently Active [Distraction Blocker]"

    // Curated Work, Farming, Manufacturing, Business & Growth Channels
    val CURATED_GROWTH_CHANNELS = listOf(
        // Farming & Agriculture (खेती, कृषि, पशुपालन)
        CuratedChannel("AgroMoney Mindset", "@agromoneymindset", "Farming", "Commercial farming, livestock & high-income farm systems"),
        CuratedChannel("Indian Farmer", "@indianfarmer", "Farming", "Modern agriculture, crops, smart farming techniques in India"),
        CuratedChannel("Farming Leader", "@farmingleader", "Farming", "Dairy farming, agricultural machinery, silage & kisan business"),
        CuratedChannel("Discover Agriculture", "@discoveragriculture", "Farming", "Global smart farming equipment, tractors & crop cultivation"),
        CuratedChannel("Organic Kheti Guru", "@organickhetiguru", "Farming", "Organic fertilizers, vermicompost, seeds & sustainable kheti"),

        // Manufacturing, Machinery & Factory (मैन्युफैक्चरिंग, मशीनरी, उद्योग)
        CuratedChannel("Abhishek Goswami Vlogs", "@abhishekgoswamivlogs", "Manufacturing", "Small business machinery, paper plate, packaging & startup setup"),
        CuratedChannel("Technical Machine", "@technicalmachine", "Manufacturing", "Industrial machine manufacturing, production & factory setups"),
        CuratedChannel("Textile Hub", "@textilehub", "Manufacturing", "Fabric manufacturing, weaving, looms & Surat textile mills"),
        CuratedChannel("How It's Made", "@howitsmade", "Manufacturing", "Industrial factory manufacturing processes & production lines"),
        CuratedChannel("Mechanical Engineering Work", "@mechwork", "Manufacturing", "Lathe machines, CNC, welding, metal fabrication & workshop tools"),

        // Business Strategy (बिज़नेस स्ट्रेटेजी व केस स्टडीज़)
        CuratedChannel("Think School", "@thethinkschoool", "Business Strategy", "Business strategy, geopolitics & company case studies in Hindi"),
        CuratedChannel("Harvard Business Review", "@harvardbusinessreview", "Business Strategy", "World-class business strategy, leadership & management"),
        CuratedChannel("Y Combinator", "@ycombinator", "Business Strategy", "Startup business strategy, product-market fit & scaling"),
        CuratedChannel("Slidebean", "@slidebean", "Business Strategy", "Startup strategy, business models & financial modeling"),

        // Marketing (मार्केटिंग व ग्रोथ)
        CuratedChannel("HubSpot Marketing", "@hubspot", "Marketing", "Digital marketing strategies, inbound growth & campaigns"),
        CuratedChannel("Neil Patel", "@neilpatel", "Marketing", "SEO, digital marketing, organic traffic & social ads"),
        CuratedChannel("Umar Tazkeer", "@umartazkeer", "Marketing", "Complete digital marketing, performance ads & PPC in Hindi"),
        CuratedChannel("Simplilearn Marketing", "@simplilearn", "Marketing", "Growth marketing, SEO, Google Ads & marketing analytics"),

        // Branding (ब्रांडिंग व ब्रांड पोज़िशनिंग)
        CuratedChannel("The Futur", "@thefuturisishere", "Branding", "Brand strategy, identity design & premium positioning"),
        CuratedChannel("Charisma on Command", "@charismaoncommand", "Branding", "Personal branding, communication & leadership presence"),
        CuratedChannel("Brand New Wave", "@brandnewwave", "Branding", "Brand packaging, identity building & consumer perception"),

        // E-Commerce (ई-कॉमर्स, D2C, ऑनलाइन स्टोर)
        CuratedChannel("Wholesale Ted", "@wholesaleted", "E-Commerce", "Shopify store setup, e-commerce sourcing & sales"),
        CuratedChannel("The Ecom King", "@theecomking", "E-Commerce", "E-commerce scaling, Shopify store building & product launches"),
        CuratedChannel("Ankur Aggarwal", "@ankuraggarwal", "E-Commerce", "E-commerce business in India, sourcing & online sales in Hindi"),

        // Product Rank (प्रोडक्ट रैंकिंग, Amazon/Flipkart SEO & लिस्टिंग ऑप्टिमाइजेशन)
        CuratedChannel("Helium 10", "@helium10", "Product Rank", "Amazon product ranking, keyword research, SEO & listing optimization"),
        CuratedChannel("Jungle Scout", "@junglescout", "Product Rank", "Amazon FBA ranking algorithm, keyword rank & bestseller optimization"),
        CuratedChannel("Ecommerce Helpline", "@ecommercehelpline", "Product Rank", "Amazon & Flipkart product ranking, seller SEO & sales booster in Hindi"),

        // Business, Trade & Finance (व्यापार, बिज़नेस, कमाई)
        CuratedChannel("Pranjal Kamra", "@pranjalkamra", "Business", "Value investing, stock analysis & personal finance"),
        CuratedChannel("Ankur Warikoo", "@warikoo", "Business", "Career, entrepreneurship, finance, books & life skills"),
        CuratedChannel("Ali Abdaal", "@aliabdaal", "Productivity", "Productivity, deep work, systems & building digital businesses"),

        // Skills & Practical Work (हुनर, काम, ट्रेनिंग)
        CuratedChannel("freeCodeCamp", "@freecodecamp", "Coding", "Full-stack software engineering & programming free"),
        CuratedChannel("Philipp Lackner", "@philipplackner", "Coding", "Android, Kotlin, Architecture & Jetpack Compose"),
        CuratedChannel("CodeWithHarry", "@codewithharry", "Coding", "Practical coding, web development & Python in Hindi"),
        CuratedChannel("Apna College", "@apnacollegeofficial", "Coding", "DSA, Java, Web Development & placement skills"),

        // Science & Knowledge
        CuratedChannel("3Blue1Brown", "@3blue1brown", "Science", "Visual Mathematics & Neural Networks"),
        CuratedChannel("Veritasium", "@veritasium", "Science", "Science, physics experiments and engineering"),
        CuratedChannel("Kurzgesagt – In a Nutshell", "@kurzgesagt", "Science", "Science, biology & philosophy explained"),
        CuratedChannel("Khan Academy", "@khanacademy", "Science", "World-class education for anyone, anywhere"),

        // Mindset & Productivity
        CuratedChannel("Huberman Lab", "@hubermanlab", "Productivity", "Neuroscience, focus protocols, sleep & health optimization"),
        CuratedChannel("TED-Ed", "@teded", "Productivity", "Lessons worth sharing across disciplines"),
        CuratedChannel("Better Ideas", "@betterideas", "Mindset", "Overcoming procrastination & building work discipline")
    )

    // Banned Categories per User Request:
    // 1. Music & Songs (म्यूजिक/गाने, एल्बम, सिंगर्स, लेबल्स)
    private val MUSIC_KEYWORDS = listOf(
        // Core music & song identifiers (explicit compound phrases to avoid colliding with educational topics like "Animal Kingdom", "Beats in Waves", "Acoustic Waves")
        "video song", "audio song", "music video", "official music video", "official video song",
        "full video song", "full audio song", "remix song", "lyrical video", "lyric video", "full song",
        "jukebox", "ost song", "soundtrack song", "lofi beats", "slowed+reverb", "slowed and reverb",
        "bass boosted beats", "dj remix", "dholki mix", "hook step dance", "playback singer",
        "ghazal song", "qawwali song", "bhajan song", "aarti song", "kirtan song",
        "bhojpuri song", "punjabi song", "hindi song", "bollywood song", "haryanvi song",
        "item song", "hip hop song", "rap song", "pop song", "romantic song",
        "sad song", "dance song", "party song", "love song", "hit song", "top songs",
        "8k video song", "4k video song", "hd video song", "8k video",

        // Music Record Labels & Distribution Channels
        "t-series", "tseries", "zee music", "speed records", "sony music", "saregama",
        "desi music factory", "tips official", "tips music", "yrf music", "yrf records", "times music",
        "white hill music", "white hill records", "geet mp3", "desi melodies", "panj-aab records", "vyrl originals",
        "aditya music", "lahari music", "anand audio", "wave music", "state studio", "onima records",
        "onima live", "onima", "vevo", "sound recording", "t-series classics",

        // Prominent Artists / Singers / Bands (Hindi, Punjabi, Bhojpuri, English, International)
        "arijit singh", "badshah rapper", "sidhu moose wala", "sidhu moosewala", "diljit dosanjh",
        "yo yo honey singh", "honey singh", "karan aujla", "shreya ghoshal", "sonu nigam",
        "atif aslam", "jubin nautiyal", "neha kakkar", "tony kakkar", "mc stan", "emiway bantai",
        "raftaar rapper", "divine rapper", "anuv jain", "prateek kuhad", "darshan raval",
        "armaan malik", "sunidhi chauhan", "kumar sanu", "alka yagnik", "udit narayan", "khesari lal",
        "pawan singh", "shilpi raj", "pramod premi", "b praak", "jaani music", "guru randhawa",
        "veet baljit", "zack knight", "jasmin walia", "savanna", "sonu ke titu", "bom diggy",
        "bom diggy diggy", "bhain nanki", "mos ma luj", "mos ma luj djal synin o",
        "justin bieber", "taylor swift", "eminem", "bts songs", "blackpink", "ed sheeran",
        "billie eilish", "the weeknd", "ariana grande", "dua lipa", "alan walker"
    )

    // 2. Movies & Cinema (फिल्म, मूवी, ट्रेलर, सीरियल)
    private val MOVIE_KEYWORDS = listOf(
        "full movie hd", "south movie hindi dubbed", "hollywood movie hindi dubbed", "bollywood movie hd",
        "tollywood movie hd", "blockbuster movie hd", "official movie trailer", "teaser trailer movie",
        "box office collection", "cinema review", "film review", "web series full episode",
        "daily soap serial", "serial full episode", "drama serial episode"
    )

    // 3. Comedy & Pranks (कॉमेडी, प्रैंक, चुटकुले)
    private val COMEDY_KEYWORDS = listOf(
        "standup comedy", "stand-up comedy", "funny comedy vines", "hasya kavi",
        "public prank gone wrong", "funny prank video", "comedy vines",
        "bb ki vines", "ashish chanchlani", "round2hell", "round 2 hell", "mythpat comedy",
        "samay raina standup", "munawar faruqui comedy", "zakir khan standup", "harsh beniwal comedy",
        "amit bhadana comedy", "memes compilation", "troll video compilation"
    )

    // 4. Roasting (रोस्टिंग)
    private val ROASTING_KEYWORDS = listOf(
        "carryminati roast", "roast video", "triggered insaan roast",
        "fukra insaan roast", "elvish yadav roast", "thara bhai joginder roast", "cringe roast video",
        "tiktok roast", "instagram roast"
    )

    // 5. Gaming (गेमिंग / कैजुअल गेमप्ले)
    private val GAMING_KEYWORDS = listOf(
        "pubg", "bgmi", "free fire", "freefire", "gta 5", "gta v",
        "gameplay", "gaming", "minecraft gameplay", "roblox gameplay", "fortnite",
        "valorant", "total gaming", "techno gamerz", "mortal gaming", "dynamo gaming",
        "live stream gameplay", "games live stream"
    )

    // 6. Useless / Sensational News & Gossip (फालतू की न्यूज, मसाला, गॉसिप)
    private val SENSATIONAL_NEWS_GOSSIP_KEYWORDS = listOf(
        "masala news", "celebrity gossip news", "bollywood gossip news", "paparazzi viral",
        "fake news viral", "khulasa news drama", "bigg boss drama", "biggboss full episode",
        "shouting debate drama", "news debate drama", "viral scandal", "trending dating show"
    )

    // Combined Blocked Keywords for distraction filtering
    private val ALL_BLOCKED_KEYWORDS = MUSIC_KEYWORDS + MOVIE_KEYWORDS + COMEDY_KEYWORDS +
            ROASTING_KEYWORDS + GAMING_KEYWORDS + SENSATIONAL_NEWS_GOSSIP_KEYWORDS

    // Allowed Work, Farming, Manufacturing, Business & Learning Keywords (Protected from blocking)
    private val WORK_AND_PRODUCTIVE_KEYWORDS = listOf(
        // Education & Academics (शिक्षा, पढ़ाई, तैयारी, लेक्चर्स, नोट्स)
        "education", "educational", "edtech", "shiksha", "padhai", "padhai likhai", "vidya",
        "study", "studies", "student", "students", "teacher", "professor", "lecture", "lectures",
        "class", "classes", "batch", "course", "courses", "syllabus", "revision", "notes",
        "chapter", "concept", "concepts", "theory", "lesson", "lessons", "curriculum",
        "coaching", "institution", "institute", "school", "college", "university", "academy",
        "board", "cbse", "icse", "ncert", "state board", "exam", "exams", "examination",
        "pariksha", "test", "mock test", "test series", "question paper", "sample paper",
        "answer key", "pyq", "previous year", "solved paper", "solution", "solutions",
        "formula", "formulas", "derivation", "mcq", "quiz", "degree", "diploma", "phd", "research",

        // Priority & User Importance (महत्वपूर्ण प्लेलिस्ट व स्टडी लिस्ट)
        "important", "imp", "essential", "priority", "key", "must watch", "playlist", "my playlist",
        "my study", "my videos", "saved", "collection", "knowledge", "information", "gyan",
        "vigyan", "informative", "documentary", "facts", "how it works", "science explained",

        // Academic Subjects & Competitive Exam Topics
        "math", "maths", "mathematics", "algebra", "calculus", "geometry", "trigonometry",
        "arithmetic", "statistics", "probability", "matrix", "matrices", "integration", "differentiation",
        "physics", "chemistry", "biology", "botany", "zoology", "science", "social science",
        "history", "itihas", "geography", "bhugol", "polity", "political science", "civics",
        "constitution", "samvidhan", "economics", "arthshastra", "commerce", "accountancy", "accounts",
        "english", "hindi", "sanskrit", "grammar", "vyakaran", "vocabulary", "vocab",
        "spoken english", "english speaking", "communication", "writing", "essay", "literature", "phonics",
        "reasoning", "aptitude", "puzzle", "syllogism", "coding decoding", "blood relation",

        // Competitive Exams & Coaching
        "upsc", "ias", "ips", "ifs", "civil services", "prelims", "mains", "ssc", "cgl", "chsl",
        "mts", "cpo", "gd", "banking", "ibps", "sbi", "railway", "rrb", "ntpc", "group d",
        "jee", "iit", "neet", "aiims", "gate", "cat", "clat", "nda", "cds", "afcat", "defence",
        "police", "si", "constable", "daroga", "patwari", "lekhpal", "ctet", "uptet", "reet", "tet",
        "bpsc", "uppcs", "mppsc", "ras", "jpsc", "state pcs", "pcs", "current affairs", "gk",
        "general knowledge", "gs", "general studies", "daily news analysis", "dna", "editorial",
        "khan sir", "khan gs", "physics wallah", "alakh pandey", "pw", "drishti ias", "vikas divyakirti",
        "unacademy", "byjus", "vedantu", "adda247", "exampur", "wifistudy", "rojgar with ankit",
        "rwa", "utkarsh classes", "freecodecamp",

        // Farming & Agriculture (खेती, किसानी, पशुपालन)
        "khet", "kheti", "farming", "farm", "farmer", "agriculture", "krishi", "kisan",
        "crop", "crops", "dairy", "dairy farming", "poultry", "pashupalan", "goat farming",
        "bakri", "rabbit farming", "murgi", "polyhouse", "tractor", "organic farming",
        "gardening", "harvest", "soil", "fertilizer", "seed", "beekeeping", "fish farming",
        "machinery", "fodder", "chaff cutter", "pulverizer", "kheti badi", "bagwani",
        "animal husbandry", "horticulture", "irrigation", "pesticide", "cultivation",
        "shed", "greenhouse", "mulching",

        // Manufacturing, Factory, Machinery, Textiles & Trade (मैन्युफैक्चरिंग, मशीन, उद्योग, फैब्रिक)
        "manufacturing", "manufacturer", "fabric", "textile", "textiles", "weaving",
        "spinning", "loom", "stitching", "garment", "factory", "machine", "machinery",
        "industry", "industrial", "production", "making", "workshop", "process", "plant",
        "small business", "laghu udyog", "udyog", "karobar", "business idea", "raw material",
        "wholesale", "supplier", "packaging", "printing", "paper plate", "plastic",
        "welding", "carpentry", "woodworking", "metalwork", "repair", "mechanic",
        "automotive", "plumbing", "construction", "civil", "diy", "engineering",
        "craftsmanship", "lathe", "cnc", "tool", "tools", "milling", "die casting",
        "assembly", "fabrication", "saree", "yarn", "cotton", "surat mill",

        // Business, Commerce, Trade & Income (व्यापार, बिज़नेस, कमाई)
        "business", "business ideas", "vyapar", "trade", "trading", "retail",
        "import", "export", "franchise", "income", "earning", "kamai", "profit",
        "shop", "dukaan", "startup", "entrepreneur", "finance", "investing",
        "economy", "stocks", "career",

        // Business Strategy, Marketing, Branding, E-Commerce & Product Ranking
        "business strategy", "strategy", "case study", "strategic", "business model", "growth strategy",
        "marketing", "digital marketing", "inbound marketing", "performance marketing", "growth marketing",
        "branding", "brand", "brand identity", "brand strategy", "brand positioning", "personal branding",
        "ecom", "ecommerce", "e-commerce", "shopify", "dropshipping", "online store", "d2c", "sourcing",
        "product rank", "product ranking", "listing rank", "amazon fba", "flipkart seller", "amazon seo",
        "listing optimization", "keyword research", "bestseller rank", "helium 10", "jungle scout",
        "ppc", "sales funnel", "conversion rate", "lead generation", "advertising", "ad campaign",

        // Practical Skills, How-to, Demonstration & Learning (काम सिखाना, समझना, इंफॉर्मेशन)
        "how to", "how to make", "how to start", "kaise banaye", "kaise kare", "kaise start kare",
        "sikhe", "sikho", "tutorial", "guide", "training", "practical", "demo", "demonstration",
        "explanation", "explained", "technique", "method", "system", "working", "work", "craft", "skill",

        // Tech, Programming & Coding
        "code", "coding", "program", "programming", "software", "developer", "development",
        "kotlin", "android", "java", "python", "javascript", "react", "html", "css", "sql",
        "data science", "machine learning", "artificial intelligence", "generative ai", "ai tools",
        "ai tutorial", "chatgpt", "cloud", "aws", "docker", "cybersecurity", "networking",
        "dsa", "algorithms", "system design"
    )

    /**
     * Determines whether a channel or video has explicit work, farming, manufacturing,
     * business, practical trade, or educational signals.
     * Uses regex word boundaries for short words (length <= 4) to prevent false substring matches (e.g. 'ai' in 'Bhain').
     */
    fun isWorkOrProductiveContent(text: String): Boolean {
        val lower = text.lowercase()
        return WORK_AND_PRODUCTIVE_KEYWORDS.any { keyword ->
            if (keyword.length <= 4) {
                Regex("\\b" + Regex.escape(keyword) + "\\b").containsMatchIn(lower)
            } else {
                lower.contains(keyword)
            }
        }
    }

    /**
     * Validates whether a channel qualifies for addition to groups.
     * All work, farming, manufacturing, business, skill demonstration, and educational channels are ALLOWED.
     * Useless distraction channels (movies, music, comedy, roasting, gaming, sensational news/gossip) are BLOCKED.
     */
    fun validateChannel(
        channelName: String,
        handle: String,
        description: String = "",
        sampleVideoTitles: List<String> = emptyList()
    ): ValidationResult {
        val cleanName = channelName.trim().lowercase()
        val cleanHandle = handle.trim().lowercase().removePrefix("@")
        val combinedText = "$cleanName $cleanHandle ${description.lowercase()} ${sampleVideoTitles.joinToString(" ").lowercase()}"

        // 1. Whitelist Check: Curated channels are immediately approved
        val isCurated = CURATED_GROWTH_CHANNELS.any {
            it.name.equals(channelName.trim(), ignoreCase = true) ||
            it.handle.removePrefix("@").equals(cleanHandle, ignoreCase = true)
        }
        if (isCurated) {
            return ValidationResult.Allowed
        }

        // 2. Work / Farming / Manufacturing / Business Override
        // If the channel name or handle or recent videos clearly describe work, farming, manufacturing,
        // machines, business, or practical skills, allow immediately (unless it's an explicit comedy/roast/movie channel).
        val hasWorkSignals = isWorkOrProductiveContent(combinedText)

        // 3. Strict Check: Is the channel explicitly named after banned distractions?
        // E.g. "T-Series", "CarryMinati", "Total Gaming", "Sony Music", "Best Bollywood Movies", "Pranks Zone"
        for (blocked in ALL_BLOCKED_KEYWORDS) {
            // Check channel name and handle with word matching to prevent false positives (e.g. "mulching film" in kheti)
            if (cleanName.contains(blocked) || cleanHandle.contains(blocked)) {
                // If it's a known work channel using a word incidentally, verify
                if (hasWorkSignals && (cleanName.contains("farming") || cleanName.contains("kheti") ||
                                cleanName.contains("manufacturing") || cleanName.contains("machine") ||
                                cleanName.contains("business") || cleanName.contains("fabric"))) {
                    // Legitimate work channel (e.g. agricultural mulching film, textile machine demo)
                    continue
                }
                val categoryDesc = categorizeDistraction(blocked)
                return ValidationResult.Blocked(
                    "Channel matches blocked distraction category ($categoryDesc: '$blocked'). FocusTube blocks movies, music, comedy, roasting, casual gaming, and sensational news. All work, farming, manufacturing, business & skill channels are allowed."
                )
            }
        }

        // 4. Sample Video Titles Inspection
        // If sample video titles contain a high proportion of movies, songs, roasts, or gaming, block the channel.
        if (sampleVideoTitles.isNotEmpty()) {
            var distractionCount = 0
            for (title in sampleVideoTitles) {
                if (isDistractionVideo(title)) {
                    distractionCount++
                }
            }
            val distractionRatio = distractionCount.toDouble() / sampleVideoTitles.size.toDouble()
            // If more than 40% of recent videos are explicit entertainment/songs/roasts/games, block
            if (distractionRatio > 0.40 && !hasWorkSignals) {
                return ValidationResult.Blocked(
                    "Distraction content detected in channel ($distractionCount of ${sampleVideoTitles.size} sampled videos match movies/music/comedy/roasts/gaming/masala news). Only work, farming, manufacturing, business, skill, and educational channels are allowed."
                )
            }
        }

        // 5. Work channels, tutorials, explanations, and non-distraction channels are allowed
        return ValidationResult.Allowed
    }

    /**
     * Validates whether a playlist qualifies for addition to FocusTube.
     * All farming, manufacturing, machinery, business, skills, and educational playlists are ALLOWED.
     * Distraction playlists (Music, Songs, Movies, Comedy, Roasts, Gaming, Gossip) are BLOCKED AT THE GATE.
     */
    fun validatePlaylist(
        playlistTitle: String,
        sampleVideoTitles: List<String> = emptyList(),
        sampleChannelNames: List<String> = emptyList()
    ): ValidationResult {
        val cleanTitle = playlistTitle.trim().lowercase()
        val hasWorkSignals = isWorkOrProductiveContent(cleanTitle)

        // 1. Direct check on playlist title
        // Only block if the playlist title itself is EXPLICITLY a blocked distraction category:
        for (blocked in ALL_BLOCKED_KEYWORDS) {
            if (cleanTitle.contains(blocked)) {
                if (hasWorkSignals) {
                    continue
                }
                val categoryDesc = categorizeDistraction(blocked)
                return ValidationResult.Blocked(
                    "Playlist matches blocked distraction category ($categoryDesc: '$blocked'). FocusTube strictly blocks music, movies, comedy, and entertainment. All education, study, work, farming, manufacturing, business & skill playlists can be added."
                )
            }
        }

        // 2. Reject playlist only if title is explicitly 'undefined' and no sample videos exist
        if (cleanTitle == "undefined" && sampleVideoTitles.isEmpty()) {
            return ValidationResult.Blocked(
                "Invalid or blocked playlist. Please provide a valid educational or work playlist."
            )
        }

        // 3. If the playlist title has clear work / education / study signals, ALLOW it immediately
        if (hasWorkSignals) {
            return ValidationResult.Allowed
        }

        // 4. Sample videos check for generic playlist titles (e.g. "YouTube Playlist", "Saved", "Batch 1"):
        if (sampleVideoTitles.isNotEmpty()) {
            val productiveCount = sampleVideoTitles.indices.count { index ->
                isWorkOrProductiveContent(sampleVideoTitles[index]) ||
                isWorkOrProductiveContent(sampleChannelNames.getOrNull(index).orEmpty())
            }
            val distractionCount = sampleVideoTitles.indices.count { index ->
                isDistractionVideo(sampleVideoTitles[index], sampleChannelNames.getOrNull(index).orEmpty())
            }

            // If there are productive/educational videos or less than 50% are distractions, ALLOW:
            if (productiveCount > 0 || distractionCount < (sampleVideoTitles.size / 2)) {
                return ValidationResult.Allowed
            }

            // Only block if >= 50% are explicit music/distractions and 0 are productive:
            if (distractionCount >= (sampleVideoTitles.size / 2) && productiveCount == 0) {
                return ValidationResult.Blocked(
                    "Distraction content detected in playlist ($distractionCount of ${sampleVideoTitles.size} sampled videos match music, songs, or entertainment). FocusTube strictly blocks music and entertainment playlists."
                )
            }
        }

        return ValidationResult.Allowed
    }

    /**
     * Determines whether an individual video is a distraction.
     * Takes both title and channelName.
     * Work, education, study, farming, manufacturing, machine, business, and skill videos are ALWAYS kept safe.
     * Entertainment movies, songs, comedy vines, roasts, games, and sensational news are filtered out.
     */
    fun isDistractionVideo(title: String, channelName: String = ""): Boolean {
        val lowerTitle = title.lowercase().trim()
        val lowerChannel = channelName.lowercase().trim()
        val combined = "$lowerTitle $lowerChannel".trim()

        // 1. Check if the video or channel has clear education / work / study / practical signals:
        val hasWorkOrStudySignals = isWorkOrProductiveContent(lowerTitle) || isWorkOrProductiveContent(lowerChannel)

        if (hasWorkOrStudySignals) {
            // If it has genuine education, study, or work signals, it is PROTECTED.
            // ONLY block it if it is an UNAMBIGUOUS explicit song, full movie, or roast release:
            val isExplicitMusicOrMovie = lowerTitle.contains("official music video") ||
                    lowerTitle.contains("official video song") ||
                    lowerTitle.contains("full video song") ||
                    lowerTitle.contains("video song") ||
                    lowerTitle.contains("audio song") ||
                    lowerTitle.contains("full song") ||
                    lowerTitle.contains("remix song") ||
                    lowerTitle.contains("punjabi song") ||
                    lowerTitle.contains("bhojpuri song") ||
                    lowerTitle.contains("bollywood song") ||
                    lowerTitle.contains("jukebox") ||
                    lowerTitle.contains("full movie hd") ||
                    lowerTitle.contains("south movie hindi dubbed") ||
                    lowerTitle.contains("carryminati roast") ||
                    lowerTitle.contains("pubg gameplay")
            if (isExplicitMusicOrMovie) {
                return true
            }
            // All other educational lectures, tutorials, classes, exams, study materials are 100% ALLOWED!
            return false
        }

        // 2. ABSOLUTE MUSIC & ENTERTAINMENT GATE (for non-work/non-education videos):
        if (lowerTitle.contains("official music video") || lowerTitle.contains("official video song") ||
            lowerTitle.contains("music video") || lowerTitle.contains("video song") ||
            lowerTitle.contains("audio song") || lowerTitle.contains("full video song") ||
            lowerTitle.contains("lyrical video") || lowerTitle.contains("lyric video") ||
            lowerTitle.contains("full song") || lowerTitle.contains("remix song") ||
            lowerTitle.contains("jukebox") ||
            (lowerTitle.contains("slowed") && lowerTitle.contains("reverb")) ||
            lowerTitle.contains("lofi beats") || lowerTitle.contains("8d audio") ||
            lowerTitle.contains("3d audio") || lowerTitle.contains("soundtrack") ||
            lowerTitle.contains("ost") || lowerTitle.contains("official audio") ||
            lowerTitle.contains("8k video song") || lowerTitle.contains("4k video song") ||
            (lowerTitle.contains("8k video") && (lowerTitle.contains("bom diggy") || lowerTitle.contains("zack knight") || lowerTitle.contains("song"))) ||
            (lowerTitle.contains("official video") && (lowerTitle.contains("song") || lowerTitle.contains("gaana") ||
                    lowerTitle.contains("geet") || lowerTitle.contains("punjabi") || lowerTitle.contains("bhojpuri") ||
                    lowerTitle.contains("music") || lowerTitle.contains("lyrics") || lowerTitle.contains("veet baljit") ||
                    lowerTitle.contains("t-series") || lowerTitle.contains("singer") || lowerTitle.contains("feat") ||
                    lowerTitle.contains("ft."))) ||
            lowerTitle.contains("full movie hd") || lowerTitle.contains("south movie hindi dubbed") ||
            lowerTitle.contains("carryminati roast") || lowerTitle.contains("roast video") ||
            lowerTitle.contains("pubg") || lowerTitle.contains("bgmi") ||
            lowerTitle.contains("free fire") || lowerTitle.contains("gta 5") ||
            lowerTitle.contains("gameplay")) {
            return true
        }

        // 3. Channel-Level Music & Record Label Detection
        if (lowerChannel.isNotBlank()) {
            val isKnownMusicOrDistractionChannel = lowerChannel.contains("t-series") || lowerChannel.contains("tseries") ||
                    lowerChannel.contains("speed records") || lowerChannel.contains("state studio") ||
                    lowerChannel.contains("onima") || lowerChannel.contains("sony music") ||
                    lowerChannel.contains("zee music") || lowerChannel.contains("saregama") ||
                    lowerChannel.contains("yrf") || lowerChannel.contains("white hill") ||
                    lowerChannel.contains("geet mp3") || lowerChannel.contains("desi melodies") ||
                    lowerChannel.contains("desi music factory") || lowerChannel.contains("panj-aab") ||
                    lowerChannel.contains("savanna") || lowerChannel.contains("vevo") ||
                    lowerChannel.endsWith("music") || lowerChannel.endsWith("records") ||
                    lowerChannel.endsWith("songs")
            if (isKnownMusicOrDistractionChannel) {
                return true
            }
        }

        // 4. Keyword matching across all blocked entertainment/distraction lists
        return ALL_BLOCKED_KEYWORDS.any { blocked ->
            if (blocked.length <= 4) {
                Regex("\\b" + Regex.escape(blocked) + "\\b").containsMatchIn(combined)
            } else {
                combined.contains(blocked)
            }
        }
    }

    private fun categorizeDistraction(keyword: String): String {
        return when {
            MUSIC_KEYWORDS.contains(keyword) -> "Music & Songs (म्यूजिक/गाने)"
            MOVIE_KEYWORDS.contains(keyword) -> "Movies & Cinema (फिल्म/मूवी)"
            COMEDY_KEYWORDS.contains(keyword) -> "Comedy & Pranks (कॉमेडी/प्रैंक)"
            ROASTING_KEYWORDS.contains(keyword) -> "Roasting (रोस्टिंग)"
            GAMING_KEYWORDS.contains(keyword) -> "Casual Gaming (गेमिंग)"
            SENSATIONAL_NEWS_GOSSIP_KEYWORDS.contains(keyword) -> "Sensational News & Gossip (फालतू की न्यूज)"
            else -> "Distraction"
        }
    }
}
