package com.arslan.clonecat.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.hypot

class PatternView(context: Context) : View(context) {

    var onPattern: (String) -> Unit = {}

    private val cells = mutableListOf<Int>()
    private var touch: PointF? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(this@PatternView, androidx.appcompat.R.attr.colorPrimary)
        strokeWidth = 6 * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(MeasureSpec.getSize(widthMeasureSpec), (240 * resources.displayMetrics.density).toInt())
        setMeasuredDimension(size, size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) cells.clear()
        touch = PointF(event.x, event.y).takeIf { event.action != MotionEvent.ACTION_UP }
        hit(event.x, event.y)?.takeIf { it !in cells }?.let { add(it) }
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
            onPattern(cells.joinToString("") { "${it + 1}" })
        }
        invalidate()
        return true
    }

    override fun performClick() = super.performClick()

    override fun onDraw(canvas: Canvas) {
        val step = width / 3f
        (0..8).forEach { canvas.drawCircle(cx(it), cy(it), step / (if (it in cells) 6 else 12), paint) }
        val points = cells.map { PointF(cx(it), cy(it)) } + listOfNotNull(touch.takeIf { cells.isNotEmpty() })
        points.zipWithNext { a, b -> canvas.drawLine(a.x, a.y, b.x, b.y, paint) }
    }

    private fun add(cell: Int) {
        val last = cells.lastOrNull() ?: cell
        val row = last / 3 + cell / 3
        val col = last % 3 + cell % 3
        val mid = row / 2 * 3 + col / 2
        if (row % 2 == 0 && col % 2 == 0 && mid != cell && mid !in cells) cells.add(mid)
        cells.add(cell)
    }

    private fun hit(x: Float, y: Float): Int? {
        val step = width / 3f
        val cell = (y / step).toInt().coerceIn(0, 2) * 3 + (x / step).toInt().coerceIn(0, 2)
        return cell.takeIf { hypot(x - cx(it), y - cy(it)) < step / 3 }
    }

    private fun cx(cell: Int) = (cell % 3 + 0.5f) * width / 3f

    private fun cy(cell: Int) = (cell / 3 + 0.5f) * width / 3f
}
