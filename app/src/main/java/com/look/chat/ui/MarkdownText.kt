package com.look.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.look.chat.logic.MarkdownParser

@Composable
internal fun MarkdownText(blocks: List<MarkdownParser.Block>) {
    val codeBackground = MaterialTheme.colorScheme.background

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownParser.Block.Code -> Surface(
                    color = codeBackground,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }

                is MarkdownParser.Block.Header -> Text(
                    text = annotated(block.spans, codeBackground),
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    }.copy(fontWeight = FontWeight.Bold),
                )

                is MarkdownParser.Block.ListItem -> Text(
                    text = annotated(block.spans, codeBackground, prefix = "${block.marker} "),
                    style = MaterialTheme.typography.bodyMedium,
                )

                is MarkdownParser.Block.Paragraph -> Text(
                    text = annotated(block.spans, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun annotated(
    spans: List<MarkdownParser.Span>,
    codeBackground: Color,
    prefix: String = "",
): AnnotatedString = buildAnnotatedString {
    if (prefix.isNotEmpty()) append(prefix)
    spans.forEach { span ->
        withStyle(
            SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
            ),
        ) {
            append(span.text)
        }
    }
}
