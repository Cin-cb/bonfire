package com.sayzen.campfiresdk.compose.publication.post.pages

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dzen.campfire.api.models.publications.post.PageText
import com.sayzen.campfiresdk.app.CampfireConstants
import com.sup.dev.java.libs.text_format.TextFormatter
import sh.sit.bonfire.formatting.compose.BonfireMarkdownContent
import sh.sit.bonfire.formatting.compose.LinksClickableText

@Composable
internal fun PageTextRenderer(page: PageText) {
    if (page.icon > 0 && page.icon < CampfireConstants.TEXT_ICONS.size) {
        val icon = painterResource(CampfireConstants.TEXT_ICONS[page.icon])

        Row(
            modifier = Modifier.padding(start = 12.dp),
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.padding(top = 4.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PageTextContent(page = page)
            }
        }
    } else {
        PageTextContent(page)
    }
}

@Composable
private fun PageTextContent(page: PageText) {
    if (page.newFormatting) {
        BonfireMarkdownContent(
            text = page.formattedText,
            contentPadding = PaddingValues(horizontal = 12.dp),
        )
    } else {
        val colors = MaterialTheme.colorScheme.primary
        val annotatedString = remember(page.text) {
            val filtered = page.text.replace("<", "&#60;")
            val html = TextFormatter(filtered).parseHtml()
                .replace("\n", "<br />")
            AnnotatedString.fromHtml(
                htmlString = html,
                linkStyle = SpanStyle(color = colors, textDecoration = TextDecoration.Underline),
            )
        }

        LinksClickableText(
            text = annotatedString,
            modifier = Modifier
                .padding(horizontal = 12.dp),
            style = if (page.size == PageText.SIZE_1) {
                MaterialTheme.typography.headlineMedium
            } else {
                LocalTextStyle.current
            }.merge(
                textAlign = if (page.align == PageText.ALIGN_LEFT) {
                    TextAlign.Start
                } else if (page.align == PageText.ALIGN_RIGHT) {
                    TextAlign.End
                } else if (page.align == PageText.ALIGN_CENTER) {
                    TextAlign.Center
                } else {
                    TextAlign.Unspecified
                }
            )
        )
    }
}
