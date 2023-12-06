package com.sayzen.campfiresdk.support

import com.sayzen.campfiresdk.controllers.ControllerMention
import com.sup.dev.android.views.splash.SplashField
import sh.sit.bonfire.formatting.BonfireMarkdown

fun SplashField.setMarkdownEditor(inline: Boolean = true): SplashField {
    val listener = if (inline) {
        BonfireMarkdown.getInlineEditorTextChangedListener(vFieldWidget.vField)
    } else {
        BonfireMarkdown.getEditorTextChangedListener(vFieldWidget.vField)
    }

    vFieldWidget.vField.addTextChangedListener(listener)

    return this
}

fun SplashField.setMentions(): SplashField {
    ControllerMention.startFor(vFieldWidget.vField)

    return this
}
