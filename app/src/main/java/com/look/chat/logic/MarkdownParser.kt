package com.look.chat.logic

internal object MarkdownParser {

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
    )

    sealed interface Block {
        data class Header(val level: Int, val spans: List<Span>) : Block
        data class Paragraph(val spans: List<Span>) : Block
        data class ListItem(val marker: String, val spans: List<Span>) : Block
        data class Code(val text: String) : Block
    }

    private val HEADER = Regex("^(#{1,6})\\s+(.+)$")

    private val LIST_ITEM = Regex("^([-*+]|\\d{1,2}[.)])\\s+(.+)$")

    private val INLINE = Regex(
        "\\*\\*\\*([^\\s*](?:[^*]*[^\\s*])?)\\*\\*\\*" +
            "|\\*\\*([^\\s*](?:[^*]*[^\\s*])?)\\*\\*" +
            "|\\*([^\\s*](?:[^*]*[^\\s*])?)\\*" +
            "|`([^`\\n]+)`" +
            "|\\[([^\\]]+)]\\([^)]*\\)",
    )

    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val codeLines = mutableListOf<String>()
        var inCode = false

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(inline(paragraph.joinToString("\n")))
                paragraph.clear()
            }
        }

        for (rawLine in text.lines()) {
            val trimmed = rawLine.trim()
            if (inCode) {
                if (trimmed.startsWith("```")) {
                    blocks += Block.Code(codeLines.joinToString("\n"))
                    codeLines.clear()
                    inCode = false
                } else {
                    codeLines += rawLine.trimEnd()
                }
                continue
            }
            if (trimmed.startsWith("```")) {
                flushParagraph()
                inCode = true
                continue
            }
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }
            val header = HEADER.matchEntire(trimmed)
            if (header != null) {
                flushParagraph()
                blocks += Block.Header(header.groupValues[1].length, inline(header.groupValues[2]))
                continue
            }
            val item = LIST_ITEM.matchEntire(trimmed)
            if (item != null) {
                flushParagraph()
                val marker = item.groupValues[1]
                val display = if (marker in setOf("-", "*", "+")) "•" else marker
                blocks += Block.ListItem(display, inline(item.groupValues[2]))
                continue
            }
            paragraph += trimmed
        }
        if (inCode && codeLines.isNotEmpty()) blocks += Block.Code(codeLines.joinToString("\n"))
        flushParagraph()
        return blocks
    }

    fun inline(line: String): List<Span> {
        val spans = mutableListOf<Span>()
        var start = 0
        for (m in INLINE.findAll(line)) {
            if (m.range.first > start) spans += Span(line.substring(start, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> spans += Span(g[1], bold = true, italic = true)
                g[2].isNotEmpty() -> spans += Span(g[2], bold = true)
                g[3].isNotEmpty() -> spans += Span(g[3], italic = true)
                g[4].isNotEmpty() -> spans += Span(g[4], code = true)
                g[5].isNotEmpty() -> spans += Span(g[5])
            }
            start = m.range.last + 1
        }
        if (start < line.length) spans += Span(line.substring(start))
        return spans.ifEmpty { listOf(Span(line)) }
    }
}
