package com.proto13.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTitleMatcherTest {
    @Test
    fun `extractBestTitle chooses the likely title line`() {
        val text = "Proto13\nApex Relay\nVersion 2\n"

        assertEquals("Apex Relay", extractBestTitle(text))
    }

    @Test
    fun `extractBestTitle prefers the far-end title when the footer appears early in OCR`() {
        val text = "Ariana Chase\nArt Code 12\nWizards of the Coast\nProto13 Core\nApex Relay\n"

        assertEquals("Apex Relay", extractBestTitle(text))
    }

    @Test
    fun `extractBestTitle prefers the near-start title when the footer appears late in OCR`() {
        val text = "Apex Relay\nProto13 Core\nArtist Name\nCard 123\nEdition 2\nCopyright\nWizards of the Coast\n"

        assertEquals("Apex Relay", extractBestTitle(text))
    }

    @Test
    fun `extractBestTitle chooses the stronger footer side when both ends are evaluated`() {
        val text = "Apex Relay\nArtist Name\nWizards of the Coast\n123 456\nCopyright\nProto13 Core\n"

        assertEquals("Proto13 Core", extractBestTitle(text))
    }

    @Test
    fun `extractBestTitle prioritizes the nearest valid title candidate from the opposite edge of the footer`() {
        val trailingFooterText = "Card 123\nApex Relay\nArtist Name\nWizards of the Coast\nCopyright\n"
        val leadingFooterText = "Wizards of the Coast\nArtist Name\nApex Relay\nCard 123\n"

        assertEquals("Apex Relay", extractBestTitle(trailingFooterText))
        assertEquals("Apex Relay", extractBestTitle(leadingFooterText))
    }

    @Test
    fun `first 5 lines and last 5 lines are scored separately and footer is identified`() {
        val text = "Impulsive Pilferer\nCreature Goblin Pirate\nWhen Impulsive Pilferer dies create a\nTreasure token Its an artifact with e\nSacrifice this artifact Add one mana of any\ncolor\nEncore 3 3a Exile this card from you\ngraveyard For each opponent create a 10ket C\nthat attacks that opponent this turn if able They\ngain haste Sacrifice them at the beginning of the\nnext end step Activate only as a sorcery\n185 361 C\nSast\nM &C 20208\nCMR EN JAKUB KASPER\n"
        val lines = text.lineSequence()
            .map { normalizeTitleText(it) }
            .filter { it.isNotBlank() }
            .toList()

        val firstFive = lines.take(5).joinToString(" ")
        val lastFive = lines.takeLast(5).joinToString(" ")

        val footerScoreMethod = Class.forName("com.proto13.android.MainActivityKt")
            .getDeclaredMethod("footerMatchScore", String::class.java)
        footerScoreMethod.isAccessible = true

        val firstFiveScore = footerScoreMethod.invoke(null, firstFive) as Int
        val lastFiveScore = footerScoreMethod.invoke(null, lastFive) as Int

        assertTrue(firstFiveScore < lastFiveScore)
        assertTrue(lastFiveScore >= 50)

        val footerWindowMethod = Class.forName("com.proto13.android.MainActivityKt")
            .getDeclaredMethod("findBestFooterWindow", List::class.java)
        footerWindowMethod.isAccessible = true

        val footerWindow = footerWindowMethod.invoke(null, lines)
        val side = footerWindow!!.javaClass.getMethod("getSide").invoke(footerWindow) as String
        val score = footerWindow.javaClass.getMethod("getScore").invoke(footerWindow) as Int
        val signalCount = footerWindow.javaClass.getMethod("getSignalCount").invoke(footerWindow) as Int

        assertEquals("trailing", side)
        assertTrue(score >= 50)
        assertTrue(signalCount >= 2)
    }

    @Test
    fun `extractBestTitle ignores body text and keeps the actual card name`() {
        val text = "Impulsive Pilferer\nCreature Goblin Pirate\nWhen Impulsive Pilferer dies create a\nTreasure token Its an artifact with e\nSacrifice this artifact Add one mana of any\ncolor\nEncore 3 3a Exile this card from you\ngraveyard For each opponent create a 10ket C\nthat attacks that opponent this turn if able They\ngain haste Sacrifice them at the beginning of the\nnext end step Activate only as a sorcery\n185 361 C\nSast\nM &C 20208\nCMR EN JAKUB KASPER\n"

        assertEquals("Impulsive Pilferer", extractBestTitle(text))
    }

    @Test
    fun `footerMatchScore counts a valid 4 digit year as a footer signal`() {
        val method = Class.forName("com.proto13.android.MainActivityKt")
            .getDeclaredMethod("footerMatchScore", String::class.java)
        method.isAccessible = true

        val score = method.invoke(null, "1998") as Int

        assertTrue(score > 0)
    }

    @Test
    fun `findExactCardTitleMatch matches an exact card name from the reference list`() {
        val knownTitles = listOf(
            "Apex Relay",
            "Proto13 Core",
            "Impulsive Pilferer"
        )

        assertEquals("Apex Relay", findExactCardTitleMatch("Apex Relay\nArtist Name\n", knownTitles))
        assertEquals("Impulsive Pilferer", findExactCardTitleMatch("\nImpulsive Pilferer\nBody text\n", knownTitles))
        assertTrue(findExactCardTitleMatch("No match here\n", knownTitles) == null)
    }

    @Test
    fun `shouldRetryCapture only retries when an exact match has not been found`() {
        val knownTitles = listOf("Apex Relay", "Proto13 Core")

        assertTrue(shouldRetryCapture("No match here\n", knownTitles, 3, 0))
        assertTrue(!shouldRetryCapture("Apex Relay\nArtist Name\n", knownTitles, 3, 0))
        assertTrue(!shouldRetryCapture("Proto13 Core\n", knownTitles, 3, 2))
    }

    @Test
    fun `matchTitle returns a likely reference match`() {
        val knownTitles = listOf("Apex Relay", "Proto13 Core", "Impulsive Pilferer")
        val match = matchTitleToReference("Apex Rely", knownTitles)

        assertTrue(match.contains("Apex Relay") || match.contains("Possible match"))
    }
}
