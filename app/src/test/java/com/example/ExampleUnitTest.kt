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
}
