package io.legado.app.ui.rss.read

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.webkit.WebView
import io.legado.app.R

class VisibleWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(VISIBLE)
    }

    override fun requestRectangleOnScreen(rectangle: Rect): Boolean {
        if (getTag(R.id.inline_content_lock_parent_scroll) == true) {
            return false
        }
        return super.requestRectangleOnScreen(rectangle)
    }

    override fun requestRectangleOnScreen(rectangle: Rect, immediate: Boolean): Boolean {
        if (getTag(R.id.inline_content_lock_parent_scroll) == true) {
            return false
        }
        return super.requestRectangleOnScreen(rectangle, immediate)
    }

}
