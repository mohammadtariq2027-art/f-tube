package com.example

import com.example.data.repository.FocusTubeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testYouTubeVideoIdExtraction() {
        val standardUrl = "https://www.youtube.com/watch?v=fNk_zzaMoSs"
        assertEquals("fNk_zzaMoSs", FocusTubeRepository.extractVideoId(standardUrl))

        val shortUrl = "https://youtu.be/k7RM-ot2NWY"
        assertEquals("k7RM-ot2NWY", FocusTubeRepository.extractVideoId(shortUrl))

        val embedUrl = "https://www.youtube.com/embed/kYB8IZa5AuE"
        assertEquals("kYB8IZa5AuE", FocusTubeRepository.extractVideoId(embedUrl))

        val shortsUrl = "https://www.youtube.com/shorts/Ip3X9LOh2dk"
        assertEquals("Ip3X9LOh2dk", FocusTubeRepository.extractVideoId(shortsUrl))

        val bareId = "bbMsuI2P15M"
        assertEquals("bbMsuI2P15M", FocusTubeRepository.extractVideoId(bareId))

        val invalid = "https://invalidurl.com/nothing"
        assertNull(FocusTubeRepository.extractVideoId(invalid))
    }

    @Test
    fun testYouTubePlaylistIdExtraction() {
        val fullUrl = "https://www.youtube.com/playlist?list=PLr6-GrHGFlylyEwWz2UaI3O_K4d_Wl3mQ"
        assertEquals("PLr6-GrHGFlylyEwWz2UaI3O_K4d_Wl3mQ", FocusTubeRepository.extractPlaylistId(fullUrl))

        val bareListId = "PL1234567890abcdef"
        assertEquals("PL1234567890abcdef", FocusTubeRepository.extractPlaylistId(bareListId))
    }

    @Test
    fun testDurationUtilsNeverReturnsHDOrVideo() {
        // 1. Should never return "HD" or "Video"
        val resultFromHD = com.example.util.DurationUtils.formatCleanDuration("HD", "abc12345678")
        org.junit.Assert.assertNotEquals("HD", resultFromHD)
        org.junit.Assert.assertNotEquals("Video", resultFromHD)

        val resultFromVideo = com.example.util.DurationUtils.formatCleanDuration("Video", "fNk_zzaMoSs")
        org.junit.Assert.assertNotEquals("Video", resultFromVideo)
        org.junit.Assert.assertNotEquals("HD", resultFromVideo)

        // 2. Real timestamp preserves
        assertEquals("09:52", com.example.util.DurationUtils.formatCleanDuration("09:52", "fNk_zzaMoSs"))

        // 3. ISO 8601 parsing
        assertEquals("09:52", com.example.util.DurationUtils.formatCleanDuration("PT9M52S", "fNk_zzaMoSs"))
        assertEquals("1:02:03", com.example.util.DurationUtils.formatCleanDuration("PT1H2M3S", "fNk_zzaMoSs"))

        // 4. Seconds parsing
        assertEquals("10:00", com.example.util.DurationUtils.formatCleanDuration("600", "fNk_zzaMoSs"))
    }

    @Test
    fun testVideoEntityNotesAttachment() {
        val video = com.example.data.local.entity.VideoEntity(
            videoId = "test1234567",
            title = "Test Video",
            channelName = "Channel",
            notes = "This is my personal study takeaway!"
        )
        assertEquals("This is my personal study takeaway!", video.notes)
    }

    @Test
    fun testFocusShieldBlocksMusicPlaylistsAndSongs() {
        // 1. Music playlists must be blocked at the gate
        val musicPlaylistTitle = "Best Bollywood Romantic Songs 2024"
        val validation = com.example.util.FocusShield.validatePlaylist(musicPlaylistTitle, listOf("Kesariya", "Apna Bana Le"))
        org.junit.Assert.assertTrue(validation is com.example.util.ValidationResult.Blocked)

        val punjabiSongsPlaylist = "Top Punjabi Hits Jukebox"
        val punjabiValidation = com.example.util.FocusShield.validatePlaylist(punjabiSongsPlaylist, emptyList())
        org.junit.Assert.assertTrue(punjabiValidation is com.example.util.ValidationResult.Blocked)

        // 2. Individual songs must be recognized as distraction
        org.junit.Assert.assertTrue(com.example.util.FocusShield.isDistractionVideo("Arijit Singh Official Video Song"))
        org.junit.Assert.assertTrue(com.example.util.FocusShield.isDistractionVideo("Dilbar Song Lyrics | Neha Kakkar"))
        org.junit.Assert.assertTrue(com.example.util.FocusShield.isDistractionVideo("Midnight Lofi Beats - Chill Jukebox"))
        org.junit.Assert.assertTrue(com.example.util.FocusShield.isDistractionVideo("CarryMinati Roast Video"))

        // Exact 3 videos from user's screenshot MUST BE BLOCKED:
        org.junit.Assert.assertTrue(
            "Bhain Nanki must be blocked despite containing 'ai' in title",
            com.example.util.FocusShield.isDistractionVideo(
                title = "Bhain Nanki | Official Video | Veet Baljit | Latest Punjabi Song",
                channelName = "State Studio."
            )
        )
        org.junit.Assert.assertTrue(
            "Savanna song must be blocked",
            com.example.util.FocusShield.isDistractionVideo(
                title = "Savanna - Mos ma luj djal synin o",
                channelName = "SAVANNA"
            )
        )
        org.junit.Assert.assertTrue(
            "Bom Diggy Diggy 8K video from T-Series must be blocked",
            com.example.util.FocusShield.isDistractionVideo(
                title = "Bom Diggy Diggy 8K Video | Zack Knight | Jasmin Walia",
                channelName = "T-Series"
            )
        )

        // 3. Farming, manufacturing, business, and skill videos must be strictly ALLOWED
        org.junit.Assert.assertFalse(com.example.util.FocusShield.isDistractionVideo("Organic Kheti kaise karein - Complete Farming Guide", "Krishi Vigyan"))
        org.junit.Assert.assertFalse(com.example.util.FocusShield.isDistractionVideo("Textile Fabric Weaving Machine in Surat Factory", "Industrial Knowledge"))
        org.junit.Assert.assertFalse(com.example.util.FocusShield.isDistractionVideo("Small Business Idea: Paper Plate Manufacturing Setup", "Business Ideas Hindi"))
        org.junit.Assert.assertFalse(com.example.util.FocusShield.isDistractionVideo("How to start Ecommerce and Product Ranking on Amazon", "Tech Skills"))
        org.junit.Assert.assertFalse(com.example.util.FocusShield.isDistractionVideo("AI Machine Learning Course for Beginners", "FreeCodeCamp"))
    }

    @Test
    fun testFocusShieldBlocksUndefinedOrMusicPlaylists() {
        val blockedRes1 = com.example.util.FocusShield.validatePlaylist("undefined", emptyList())
        org.junit.Assert.assertTrue(blockedRes1 is com.example.util.ValidationResult.Blocked)

        val blockedRes2 = com.example.util.FocusShield.validatePlaylist(
            playlistTitle = "My Songs",
            sampleVideoTitles = listOf("Bhain Nanki | Official Video", "Bom Diggy Diggy"),
            sampleChannelNames = listOf("State Studio.", "T-Series")
        )
        org.junit.Assert.assertTrue(blockedRes2 is com.example.util.ValidationResult.Blocked)
    }

    @Test
    fun testEducationalAndImportantPlaylistsAreAllowed() {
        // Educational and important playlists must be 100% allowed
        val educationRes = com.example.util.FocusShield.validatePlaylist(
            playlistTitle = "Education",
            sampleVideoTitles = listOf("Class 10 Maths Chapter 1 Real Numbers (Full HD Video)")
        )
        org.junit.Assert.assertTrue(educationRes is com.example.util.ValidationResult.Allowed)

        val importantRes = com.example.util.FocusShield.validatePlaylist(
            playlistTitle = "Important Videos",
            sampleVideoTitles = listOf("Physics Wallah Official Video: JEE Strategy", "Organic Chemistry Lecture")
        )
        org.junit.Assert.assertTrue(importantRes is com.example.util.ValidationResult.Allowed)

        val studyRes = com.example.util.FocusShield.validatePlaylist(
            playlistTitle = "My Study Playlist",
            sampleVideoTitles = listOf("UPSC Current Affairs 2026", "General Knowledge Class")
        )
        org.junit.Assert.assertTrue(studyRes is com.example.util.ValidationResult.Allowed)

        // HD Video and Official Video in educational context must NOT be blocked
        org.junit.Assert.assertFalse(
            com.example.util.FocusShield.isDistractionVideo(
                title = "Class 10 Trigonometry Full HD Video Lecture",
                channelName = "Physics Wallah"
            )
        )
        // Educational topics that previously collided with music/artist keywords MUST be allowed:
        org.junit.Assert.assertFalse(
            "Animal Kingdom in Biology must be allowed",
            com.example.util.FocusShield.isDistractionVideo("Animal Kingdom Class 11 Biology NCERT Chapter 4", "Magnet Brains")
        )
        org.junit.Assert.assertFalse(
            "King Ashoka in History must be allowed",
            com.example.util.FocusShield.isDistractionVideo("King Ashoka & The Mauryan Empire", "Khan GS Research Centre")
        )
        org.junit.Assert.assertFalse(
            "Acoustic Waves in Physics must be allowed",
            com.example.util.FocusShield.isDistractionVideo("Acoustic Waves and Speed of Sound in Physics", "Unacademy JEE")
        )
        org.junit.Assert.assertFalse(
            "Beats in Wave Physics must be allowed",
            com.example.util.FocusShield.isDistractionVideo("Superposition of Waves and Beats Phenomenon", "Physics Wallah")
        )
        org.junit.Assert.assertFalse(
            "Singer and Nicolson Model in Cell Biology must be allowed",
            com.example.util.FocusShield.isDistractionVideo("Cell Membrane: Singer and Nicolson Fluid Mosaic Model", "LearnoHub")
        )
    }

    @Test
    fun testDuplicateVideoFiltering() {
        val rawIds = listOf("video111111", "video222222", "video111111", "video333333", "video222222")
        val uniqueIds = rawIds.distinct()
        assertEquals(3, uniqueIds.size)
        assertEquals(listOf("video111111", "video222222", "video333333"), uniqueIds)
    }

    @Test
    fun testBatchQueueUrlGeneration() {
        val videoIds = listOf("fNk_zzaMoSs", "k7RM-ot2NWY", "kYB8IZa5AuE")
        val url = "https://www.youtube.com/watch_videos?video_ids=" + videoIds.joinToString(",")
        assertEquals("https://www.youtube.com/watch_videos?video_ids=fNk_zzaMoSs,k7RM-ot2NWY,kYB8IZa5AuE", url)
    }
}
