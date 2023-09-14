package com.sayzen.campfiresdk.controllers.notifications

import android.graphics.Color
import com.sayzen.campfiresdk.R
import com.sup.dev.android.tools.ToolsResources

object ControllerApp {
    fun isDarkThem() = ToolsResources.getColorAttr(R.attr.colorOnPrimary) == Color.WHITE
}
