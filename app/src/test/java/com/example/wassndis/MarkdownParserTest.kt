package com.example.wassndis

import com.example.wassndis.ui.components.MarkdownBlock
import com.example.wassndis.ui.components.parseMarkdownBlocks
import com.example.wassndis.ui.components.parseMarkdownInline
import com.example.wassndis.ui.components.stripMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testStripMarkdown() {
        val input = "Dies ist ein **fetter Text** und ein *kursiver Text* mit `inline code`."
        val stripped = stripMarkdown(input)
        assertEquals("Dies ist ein fetter Text und ein kursiver Text mit inline code.", stripped)

        val headerInput = "### 1. Einsatzzweck\n* Erster Punkt\n* Zweiter Punkt"
        val strippedHeader = stripMarkdown(headerInput)
        assertEquals("1. Einsatzzweck Erster Punkt Zweiter Punkt", strippedHeader)
    }

    @Test
    fun testParseMarkdownBlocks_headingsAndLists() {
        val markdown = """
            ### Hauptmerkmale
            * Sensor: 24 Megapixel
            * Objektiv: 50mm f/1.8
            
            1. Erste Einstellung
            2. Zweite Einstellung
            
            > Wichtiger Hinweis für Sammler
            
            Dies ist ein normaler Erklärungstext über das Gerät.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertTrue(blocks.isNotEmpty())

        val heading = blocks[0] as MarkdownBlock.Heading
        assertEquals(3, heading.level)
        assertEquals("Hauptmerkmale", heading.text)

        val bullet1 = blocks[1] as MarkdownBlock.BulletItem
        assertEquals("Sensor: 24 Megapixel", bullet1.text)

        val bullet2 = blocks[2] as MarkdownBlock.BulletItem
        assertEquals("Objektiv: 50mm f/1.8", bullet2.text)

        val num1 = blocks[3] as MarkdownBlock.NumberedItem
        assertEquals("1", num1.number)
        assertEquals("Erste Einstellung", num1.text)

        val num2 = blocks[4] as MarkdownBlock.NumberedItem
        assertEquals("2", num2.number)
        assertEquals("Zweite Einstellung", num2.text)

        val quote = blocks[5] as MarkdownBlock.Blockquote
        assertEquals("Wichtiger Hinweis für Sammler", quote.text)

        val para = blocks[6] as MarkdownBlock.Paragraph
        assertEquals("Dies ist ein normaler Erklärungstext über das Gerät.", para.text)
    }

    @Test
    fun testParseMarkdownBlocks_codeBlock() {
        val markdown = """
            Hier ist Code:
            ```json
            {"key": "value"}
            ```
            Fertig.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(markdown)
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MarkdownBlock.CodeBlock)
        val codeBlock = blocks[1] as MarkdownBlock.CodeBlock
        assertEquals("json", codeBlock.language)
        assertEquals("{\"key\": \"value\"}", codeBlock.code)
    }

    @Test
    fun testInlineMarkdown_boldAndItalic() {
        val inline = parseMarkdownInline("Das ist **wichtig** und *spannend*.")
        assertEquals("Das ist wichtig und spannend.", inline.text)
        // Check span styles
        val spans = inline.spanStyles
        assertTrue(spans.isNotEmpty())
    }
}
