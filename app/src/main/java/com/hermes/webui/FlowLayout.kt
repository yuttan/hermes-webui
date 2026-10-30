package com.hermes.webui

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

/** Minimal wrapping row layout for shortcut chips. */
class FlowLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val gap = (8 * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        var widest = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(child, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                y += rowHeight + gap
                x = 0
                rowHeight = 0
            }
            x += child.measuredWidth + gap
            widest = maxOf(widest, x - gap)
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        val height = y + rowHeight + paddingTop + paddingBottom
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec)
            else widest + paddingLeft + paddingRight
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxWidth = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            if (x > 0 && x + child.measuredWidth > maxWidth) {
                y += rowHeight + gap
                x = 0
                rowHeight = 0
            }
            child.layout(paddingLeft + x, paddingTop + y,
                paddingLeft + x + child.measuredWidth, paddingTop + y + child.measuredHeight)
            x += child.measuredWidth + gap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
    }
}
