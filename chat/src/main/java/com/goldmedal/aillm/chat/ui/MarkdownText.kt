package com.goldmedal.aillm.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp

/**
 * A deliberately small Markdown renderer: headings, bullet/numbered lists,
 * inline bold + code, and fenced code blocks with copy. No third-party
 * dependency, so the APK stays lean and the streaming renderer stays fast.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeAccent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    text = inlineStyled(block.text, codeBackground, codeAccent),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )

                is MdBlock.Bullet -> Row {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = inlineStyled(block.text, codeBackground, codeAccent),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                is MdBlock.Numbered -> Row {
                    Text(
                        text = "${block.number}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = inlineStyled(block.text, codeBackground, codeAccent),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                is MdBlock.Code -> CodeBlock(block, codeBackground)

                is MdBlock.Paragraph -> Text(
                    text = inlineStyled(block.text, codeBackground, codeAccent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code, background: Color) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Surface(
        color = background,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(block.code))
                    copied = true
                }) {
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            )
        }
    }
}

/**
 * Bold, inline code and bare URLs. A URL is a link so a search the app handed
 * back as text (see the web-search fallback) stays tappable in the transcript.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun inlineStyled(text: String, codeBackground: Color, codeAccent: Color): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) -> {
                    val end = text.indexOf("**", index + 2)
                    if (end > index) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                        append(text.substring(index + 2, end))
                        pop()
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }

                text[index] == '`' -> {
                    val end = text.indexOf('`', index + 1)
                    if (end > index) {
                        pushStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                                color = codeAccent
                            )
                        )
                        append(text.substring(index + 1, end))
                        pop()
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }

                text.startsWith("http://", index) || text.startsWith("https://", index) -> {
                    // A URL runs to the next space (or the end of the block).
                    val offset = text.substring(index).indexOfFirst { it.isWhitespace() }
                    val end = if (offset < 0) text.length else index + offset
                    // Trailing punctuation belongs to the sentence, not the URL.
                    val url = text.substring(index, end).trimEnd('.', ',', ')', ';', ':', '!', '?')
                    withLink(LinkAnnotation.Url(url)) {
                        pushStyle(
                            SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        )
                        append(url)
                        pop()
                    }
                    index += url.length
                }

                else -> {
                    append(text[index])
                    index++
                }
            }
        }
    }
}

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val number: Int, val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
}

internal fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var codeLanguage = ""

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(paragraph.toString().trim()))
        }
        paragraph.clear()
    }

    source.split("\n").forEach { line ->
        val trimmed = line.trimEnd()

        if (trimmed.startsWith("```")) {
            if (!inCode) {
                flushParagraph()
                inCode = true
                codeLanguage = trimmed.removePrefix("```").trim()
                code.clear()
            } else {
                inCode = false
                blocks.add(MdBlock.Code(codeLanguage, code.toString().trimEnd()))
                code.clear()
            }
            return@forEach
        }

        if (inCode) {
            code.append(line).append("\n")
            return@forEach
        }

        when {
            trimmed.startsWith("### ") -> {
                flushParagraph(); blocks.add(MdBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph(); blocks.add(MdBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph(); blocks.add(MdBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph(); blocks.add(MdBlock.Bullet(trimmed.drop(2).trim()))
            }
            NUMBERED.matches(trimmed) -> {
                flushParagraph()
                val number = trimmed.substringBefore('.').toIntOrNull() ?: 1
                blocks.add(MdBlock.Numbered(number, trimmed.substringAfter('.').trim()))
            }
            trimmed.isBlank() -> flushParagraph()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(" ")
                paragraph.append(trimmed)
            }
        }
    }

    if (inCode && code.isNotEmpty()) {
        blocks.add(MdBlock.Code(codeLanguage, code.toString().trimEnd()))
    }
    flushParagraph()
    return blocks
}

private val NUMBERED = Regex("^\\d+\\.\\s+.*")
