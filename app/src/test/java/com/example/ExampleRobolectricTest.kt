package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusShieldTest {

  @Test
  fun `work, farming, manufacturing, and business channels and videos are allowed`() {
    // Farming channel
    val khetiResult = com.example.util.FocusShield.validateChannel(
      channelName = "AgroMoney Mindset",
      handle = "@agromoneymindset",
      description = "Commercial farming and rabbit farming business",
      sampleVideoTitles = listOf("15x15 Feet शेड में Commercial Rabbit Farming से ₹60,000 कमाई")
    )
    assertEquals(com.example.util.ValidationResult.Allowed, khetiResult)

    // Manufacturing / Machinery channel
    val manufacturingResult = com.example.util.FocusShield.validateChannel(
      channelName = "Abhishek Goswami Vlogs",
      handle = "@abhishekgoswamivlogs",
      description = "Paper plate machine manufacturing and small business ideas",
      sampleVideoTitles = listOf("Paper Plate Making Machine | Small Business Idea")
    )
    assertEquals(com.example.util.ValidationResult.Allowed, manufacturingResult)

    // Fabric / Textile channel
    val fabricResult = com.example.util.FocusShield.validateChannel(
      channelName = "Surat Fabric & Textile Mills",
      handle = "@surattextile",
      description = "Fabric manufacturing, power loom and garment production"
    )
    assertEquals(com.example.util.ValidationResult.Allowed, fabricResult)

    // Strategy, Marketing, Branding, E-Commerce & Product Rank channels
    val strategyResult = com.example.util.FocusShield.validateChannel(
      channelName = "Think School",
      handle = "@thethinkschoool",
      description = "Business strategy and company case studies"
    )
    assertEquals(com.example.util.ValidationResult.Allowed, strategyResult)

    val marketingResult = com.example.util.FocusShield.validateChannel(
      channelName = "Neil Patel",
      handle = "@neilpatel",
      description = "Digital marketing, SEO and social ads"
    )
    assertEquals(com.example.util.ValidationResult.Allowed, marketingResult)

    val brandingResult = com.example.util.FocusShield.validateChannel(
      channelName = "The Futur",
      handle = "@thefuturisishere",
      description = "Brand strategy, brand identity design and business"
    )
    assertEquals(com.example.util.ValidationResult.Allowed, brandingResult)

    val ecomResult = com.example.util.FocusShield.validateChannel(
      channelName = "Wholesale Ted",
      handle = "@wholesaleted",
      description = "E-Commerce, Shopify store setup and online business"
    )
    assertEquals(com.example.util.ValidationResult.Allowed, ecomResult)

    val productRankResult = com.example.util.FocusShield.validateChannel(
      channelName = "Helium 10",
      handle = "@helium10",
      description = "Amazon product ranking, keyword research, SEO and listing optimization"
    )
    assertEquals(com.example.util.ValidationResult.Allowed, productRankResult)

    // Videos are not flagged as distraction
    assertEquals(false, com.example.util.FocusShield.isDistractionVideo("15x15 Feet शेड में Commercial Rabbit Farming से ₹60,000 महीने की कमाई का System"))
    assertEquals(false, com.example.util.FocusShield.isDistractionVideo("Paper Plate Making Machine | How to Start a Paper Plate Manufacturing Business"))
    assertEquals(false, com.example.util.FocusShield.isDistractionVideo("Fabric Manufacturing Process in Surat Textile Mill"))
    assertEquals(false, com.example.util.FocusShield.isDistractionVideo("Mini Chaff Cutter & Pulverizer Unit"))
    assertEquals(false, com.example.util.FocusShield.isDistractionVideo("How to Rank Products on Amazon First Page with SEO"))
    assertEquals(false, com.example.util.FocusShield.isDistractionVideo("Complete Digital Marketing and Business Strategy Course"))
  }

  @Test
  fun `music, movies, comedy, roasts, gaming, and sensational news are blocked`() {
    // Music channel
    val musicChannel = com.example.util.FocusShield.validateChannel(
      channelName = "T-Series Music",
      handle = "@tseries"
    )
    assert(musicChannel is com.example.util.ValidationResult.Blocked)

    // Comedy channel
    val comedyChannel = com.example.util.FocusShield.validateChannel(
      channelName = "Round2Hell Comedy Vines",
      handle = "@round2hell"
    )
    assert(comedyChannel is com.example.util.ValidationResult.Blocked)

    // Gaming channel
    val gamingChannel = com.example.util.FocusShield.validateChannel(
      channelName = "Total Gaming",
      handle = "@totalgaming"
    )
    assert(gamingChannel is com.example.util.ValidationResult.Blocked)

    // Distraction videos
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("New Bhojpuri Song 2026 DJ Remix"))
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("Pathaan Full Movie HD 1080p"))
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("Public Prank Gone Wrong Funny Comedy Video"))
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("CarryMinati Roast Video"))
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("PUBG Mobile Gameplay Live Stream"))
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("Breaking News Masala Viral Scandal Today"))

    // Exact 3 screenshot videos:
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("Bhain Nanki | Official Video | Veet Baljit | Latest Punjabi Song", "State Studio."))
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("Savanna - Mos ma luj djal synin o", "SAVANNA"))
    assertEquals(true, com.example.util.FocusShield.isDistractionVideo("Bom Diggy Diggy 8K Video | Zack Knight | Jasmin Walia", "T-Series"))
  }
}
