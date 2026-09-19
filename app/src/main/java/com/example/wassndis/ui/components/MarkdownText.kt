package com.example.wassndis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Parst Inline-Markdown wie **fett**, *kursiv*, ***fett+kursiv***, `code` und ~~durchgestrichen~~
 * in einen Jetpack Compose AnnotatedString.
 */
fun parseMarkdownInline(
    text: String,
    primaryColor: Color = Color.Unspecified,
    codeBackgroundColor: Color = Color.Unspecified
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = text.length

        while (cursor < len) {
            // Check for code: `code`
            if (text[cursor] == '`') {
                val nextTick = text.indexOf('`', cursor + 1)
                if (nextTick != -1) {
                    val codeContent = text.substring(cursor + 1, nextTick)
                    val span = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        background = if (codeBackgroundColor != Color.Unspecified) codeBackgroundColor else Color(0x18888888)
                    )
                    val start = length
                    append(codeContent)
                    addStyle(span, start, length)
                    cursor = nextTick + 1
                    continue
                }
            }

            // Check for bold italic: ***text*** or ___text___
            if (cursor + 2 < len && 
                ((text[cursor] == '*' && text[cursor + 1] == '*' && text[cursor + 2] == '*') ||
                 (text[cursor] == '_' && text[cursor + 1] == '_' && text[cursor + 2] == '_'))
            ) {
                val marker = text.substring(cursor, cursor + 3)
                val closing = text.indexOf(marker, cursor + 3)
                if (closing != -1) {
                    val inner = text.substring(cursor + 3, closing)
                    val start = length
                    append(inner)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                        start,
                        length
                    )
                    cursor = closing + 3
                    continue
                }
            }

            // Check for bold: **text** or __text__
            if (cursor + 1 < len &&
                ((text[cursor] == '*' && text[cursor + 1] == '*') ||
                 (text[cursor] == '_' && text[cursor + 1] == '_'))
            ) {
                val marker = text.substring(cursor, cursor + 2)
                val closing = text.indexOf(marker, cursor + 2)
                if (closing != -1) {
                    val inner = text.substring(cursor + 2, closing)
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    cursor = closing + 2
                    continue
                }
            }

            // Check for strikethrough: ~~text~~
            if (cursor + 1 < len && text[cursor] == '~' && text[cursor + 1] == '~') {
                val closing = text.indexOf("~~", cursor + 2)
                if (closing != -1) {
                    val inner = text.substring(cursor + 2, closing)
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
                    cursor = closing + 2
                    continue
                }
            }

            // Check for italic: *text* or _text_
            if ((text[cursor] == '*' || text[cursor] == '_') &&
                (cursor == 0 || !text[cursor - 1].isLetterOrDigit())
            ) {
                val marker = text[cursor]
                val closing = text.indexOf(marker, cursor + 1)
                // Avoid matching across words for underscore like foo_bar_baz
                if (closing != -1 && closing > cursor + 1 && !text[cursor + 1].isWhitespace()) {
                    val inner = text.substring(cursor + 1, closing)
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    cursor = closing + 1
                    continue
                }
            }

            append(text[cursor])
            cursor++
        }
    }
}

/**
 * Entfernt Markdown-Syntaxzeichen für Vorschautexte (z. B. auf Cards im OverviewScreen).
 */
fun stripMarkdown(text: String): String {
    if (text.isBlank()) return ""
    return text
        .replace(Regex("```[a-zA-Z]*\\n?"), "")
        .replace("```", "")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\*\\*\\*(.*?)\\*\\*\\*"), "$1")
        .replace(Regex("___(.*?)___"), "$1")
        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .replace(Regex("~~(.*?)~~"), "$1")
        .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^[\\*\\-\\+•]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^>\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\*|_"), "")
        .replace(Regex("\\n{2,}"), " ")
        .replace('\n', ' ')
        .trim()
}

/**
 * Interne Repräsentation von Markdown-Blöcken.
 */
sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
}

/**
 * Zerlegt Markdown-Text in strukturierte Blöcke.
 */
fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    if (text.isBlank()) return emptyList()

    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val rawLine = lines[i]
        val trimmed = rawLine.trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // 1. Code-Block: ```
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        // 2. Horizontal Divider: ---, ***, ___
        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // 3. Headings: # ## ### ####
        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.Heading(level, headingText))
            i++
            continue
        }

        // 4. Blockquotes: >
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            quoteLines.add(trimmed.removePrefix(">").trim())
            i++
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
            continue
        }

        // 5. Bullet items: * item, - item, + item, • item
        val bulletMatch = Regex("^[\\*\\-\\+•]\\s+(.+)$").find(trimmed)
        if (bulletMatch != null) {
            blocks.add(MarkdownBlock.BulletItem(bulletMatch.groupValues[1].trim()))
            i++
            continue
        }

        // 6. Numbered items: 1. item, 2) item
        val numberedMatch = Regex("^(\\d+)[\\.\\)]\\s+(.+)$").find(trimmed)
        if (numberedMatch != null) {
            val number = numberedMatch.groupValues[1]
            val itemText = numberedMatch.groupValues[2].trim()
            blocks.add(MarkdownBlock.NumberedItem(number, itemText))
            i++
            continue
        }

        // 7. Normal paragraph (aggregates consecutive non-empty lines)
        val paragraphLines = mutableListOf<String>()
        paragraphLines.add(rawLine.trimEnd())
        i++
        while (i < lines.size) {
            val nextTrim = lines[i].trim()
            if (nextTrim.isEmpty() ||
                nextTrim.startsWith("```") ||
                nextTrim.startsWith("#") ||
                nextTrim.startsWith(">") ||
                nextTrim.startsWith("* ") ||
                nextTrim.startsWith("- ") ||
                nextTrim.startsWith("+ ") ||
                nextTrim.startsWith("• ") ||
                Regex("^\\d+[\\.\\)]\\s+").containsMatchIn(nextTrim) ||
                nextTrim == "---" || nextTrim == "***" || nextTrim == "___"
            ) {
                break
            }
            paragraphLines.add(lines[i].trimEnd())
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString("\n")))
    }

    return blocks
}

/**
 * Rendert Markdown-formatierten Text von Gemini mit Überschriften, Aufzählungen,
 * Fett-/Kursivschrift, Zitaten und Absätzen.
 */
@Composable
fun GeminiMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseTextStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    if (blocks.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        2 -> MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                        3 -> MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        else -> MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val annotated = remember(block.text, primaryColor) {
                        parseMarkdownInline(block.text, primaryColor, codeBg)
                    }
                    Text(
                        text = annotated,
                        style = headingStyle
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text, primaryColor) {
                        parseMarkdownInline(block.text, primaryColor, codeBg)
                    }
                    Text(
                        text = annotated,
                        style = baseTextStyle,
                        color = textColor,
                        lineHeight = baseTextStyle.lineHeight * 1.25f
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = baseTextStyle.copy(
                                fontWeight = FontWeight.Black,
                                color = primaryColor
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        val annotated = remember(block.text, primaryColor) {
                            parseMarkdownInline(block.text, primaryColor, codeBg)
                        }
                        Text(
                            text = annotated,
                            style = baseTextStyle,
                            color = textColor,
                            lineHeight = baseTextStyle.lineHeight * 1.2f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = baseTextStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            ),
                            modifier = Modifier.width(24.dp)
                        )
                        val annotated = remember(block.text, primaryColor) {
                            parseMarkdownInline(block.text, primaryColor, codeBg)
                        }
                        Text(
                            text = annotated,
                            style = baseTextStyle,
                            color = textColor,
                            lineHeight = baseTextStyle.lineHeight * 1.2f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(primaryColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        val annotated = remember(block.text, primaryColor) {
                            parseMarkdownInline(block.text, primaryColor, codeBg)
                        }
                        Text(
                            text = annotated,
                            style = baseTextStyle.copy(fontStyle = FontStyle.Italic),
                            color = textColor.copy(alpha = 0.9f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .horizontalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Rendert Inline-Markdown für kompakte Elemente wie Kurzbeschreibungen oder Detailwerte.
 */
@Composable
fun InlineMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val annotated = remember(text, primaryColor) {
        parseMarkdownInline(text, primaryColor, codeBg)
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        lineHeight = style.lineHeight * 1.2f
    )
}
