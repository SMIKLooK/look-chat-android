package com.look.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParserTest {

    private fun parse(text: String) = MarkdownParser.parse(text)
    private fun inline(text: String) = MarkdownParser.inline(text)

    @Test
    fun `plain text becomes a single paragraph`() {
        assertEquals(
            listOf(MarkdownParser.Block.Paragraph(listOf(MarkdownParser.Span("просто текст")))),
            parse("просто текст"),
        )
    }

    @Test
    fun `consecutive lines join into one paragraph`() {
        val blocks = parse("строка один\nстрока два")
        assertEquals(1, blocks.size)
        assertEquals(
            listOf(MarkdownParser.Span("строка один\nстрока два")),
            (blocks[0] as MarkdownParser.Block.Paragraph).spans,
        )
    }

    @Test
    fun `empty lines split paragraphs`() {
        val blocks = parse("первый\n\nвторой")
        assertEquals(2, blocks.size)
        assertEquals("первый", (blocks[0] as MarkdownParser.Block.Paragraph).spans.single().text)
        assertEquals("второй", (blocks[1] as MarkdownParser.Block.Paragraph).spans.single().text)
    }

    @Test
    fun `headers parse with their level`() {
        val blocks = parse("## Заголовок")
        assertEquals(
            listOf(MarkdownParser.Block.Header(2, listOf(MarkdownParser.Span("Заголовок")))),
            blocks,
        )
    }

    @Test
    fun `hash without space is not a header`() {
        val blocks = parse("#тег в начале")
        assertEquals(
            listOf(MarkdownParser.Block.Paragraph(listOf(MarkdownParser.Span("#тег в начале")))),
            blocks,
        )
    }

    @Test
    fun `bullet markers become dot, numbered stay as is`() {
        val blocks = parse("- один\n* два\n+ три\n1. четыре")
        assertEquals(
            listOf(
                MarkdownParser.Block.ListItem("•", listOf(MarkdownParser.Span("один"))),
                MarkdownParser.Block.ListItem("•", listOf(MarkdownParser.Span("два"))),
                MarkdownParser.Block.ListItem("•", listOf(MarkdownParser.Span("три"))),
                MarkdownParser.Block.ListItem("1.", listOf(MarkdownParser.Span("четыре"))),
            ),
            blocks,
        )
    }

    @Test
    fun `code fence collects lines and ignores language`() {
        val blocks = parse("```kotlin\nprint(1)\nprint(2)\n```")
        assertEquals(
            listOf(MarkdownParser.Block.Code("print(1)\nprint(2)")),
            blocks,
        )
    }

    @Test
    fun `unclosed code fence still renders as code`() {
        val blocks = parse("текст до\n```\nстрока кода")
        assertEquals(
            listOf(
                MarkdownParser.Block.Paragraph(listOf(MarkdownParser.Span("текст до"))),
                MarkdownParser.Block.Code("строка кода"),
            ),
            blocks,
        )
    }

    @Test
    fun `bold, italic and both at once`() {
        assertEquals(
            listOf(MarkdownParser.Span("жирный", bold = true)),
            inline("**жирный**"),
        )
        assertEquals(
            listOf(MarkdownParser.Span("курсив", italic = true)),
            inline("*курсив*"),
        )
        assertEquals(
            listOf(MarkdownParser.Span("оба", bold = true, italic = true)),
            inline("***оба***"),
        )
    }

    @Test
    fun `code span stays verbatim`() {
        assertEquals(
            listOf(MarkdownParser.Span("a * b", code = true)),
            inline("`a * b`"),
        )
    }

    @Test
    fun `mixed inline keeps plain text around markup`() {
        assertEquals(
            listOf(
                MarkdownParser.Span("до "),
                MarkdownParser.Span("жирный", bold = true),
                MarkdownParser.Span(" после"),
            ),
            inline("до **жирный** после"),
        )
    }

    @Test
    fun `arithmetic stars stay literal`() {
        assertEquals(
            listOf(MarkdownParser.Span("2 * 3 * 4")),
            inline("2 * 3 * 4"),
        )
        assertEquals(
            listOf(MarkdownParser.Span("2 ** 3 ** 4")),
            inline("2 ** 3 ** 4"),
        )
    }

    @Test
    fun `unmatched bold marker stays literal`() {
        assertEquals(
            listOf(MarkdownParser.Span("цена ** не ясна")),
            inline("цена ** не ясна"),
        )
    }

    @Test
    fun `underscores stay literal`() {
        assertEquals(
            listOf(MarkdownParser.Span("имя_поля_2")),
            inline("имя_поля_2"),
        )
    }

    @Test
    fun `link keeps only its text`() {
        assertEquals(
            listOf(MarkdownParser.Span("текст ссылки")),
            inline("[текст ссылки](http://example.com/a)"),
        )
    }

    @Test
    fun `block parsing applies inline markup inside paragraphs`() {
        val blocks = parse("Ответ: **готово**, см. `код`")
        assertEquals(
            listOf(
                MarkdownParser.Block.Paragraph(
                    listOf(
                        MarkdownParser.Span("Ответ: "),
                        MarkdownParser.Span("готово", bold = true),
                        MarkdownParser.Span(", см. "),
                        MarkdownParser.Span("код", code = true),
                    ),
                ),
            ),
            blocks,
        )
    }
}
