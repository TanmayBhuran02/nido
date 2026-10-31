package com.nido.music.feature.music.lyrics

import com.nido.music.feature.music.data.lyrics.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun testParseStandardLrc() {
        val lrc = """
            [00:12.00] Line 1 Intro
            [00:17.50] Line 2 Verse begins
            [01:05.123] Line 3 Chorus loud and clear
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(3, parsed.size)

        assertEquals(12000L, parsed[0].timestampMs)
        assertEquals("Line 1 Intro", parsed[0].text)

        assertEquals(17500L, parsed[1].timestampMs)
        assertEquals("Line 2 Verse begins", parsed[1].text)

        assertEquals(65123L, parsed[2].timestampMs)
        assertEquals("Line 3 Chorus loud and clear", parsed[2].text)
    }

    @Test
    fun testParseMultiTimestampLine() {
        val lrc = "[00:10.00][00:25.50] Repeated chorus hook".trimIndent()
        val parsed = LrcParser.parse(lrc)
        assertEquals(2, parsed.size)
        assertEquals(10000L, parsed[0].timestampMs)
        assertEquals("Repeated chorus hook", parsed[0].text)
        assertEquals(25500L, parsed[1].timestampMs)
        assertEquals("Repeated chorus hook", parsed[1].text)
    }

    @Test
    fun testParseEmptyOrInvalidLines() {
        val lrc = """
            [ti:Song Title]
            [ar:Artist Name]
            [00:05.00] Actual lyric
            Random text without timestamps
        """.trimIndent()

        val parsed = LrcParser.parse(lrc)
        assertEquals(1, parsed.size)
        assertEquals(5000L, parsed[0].timestampMs)
        assertEquals("Actual lyric", parsed[0].text)
    }
}
