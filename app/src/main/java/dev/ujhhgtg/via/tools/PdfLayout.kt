package dev.ujhhgtg.via.tools

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PdfPageSize(val width: Int, val height: Int)
data class PdfPage(val index: Int, val left: Float, val top: Float, val width: Float, val height: Float, val sourceScale: Float)

/** Geometry from PdfViewer.c, N, and the baseline/detail bitmap limits. */
object PdfLayout {
    fun pages(sizes: List<PdfPageSize>, width: Float, margin: Float): List<PdfPage> {
        val contentWidth = max(1f, width - margin * 2)
        var top = margin
        return sizes.mapIndexed { index, size ->
            val scale = contentWidth / size.width
            PdfPage(index, margin, top, contentWidth, size.height * scale, scale).also { top += it.height + margin }
        }
    }
    fun visible(pages: List<PdfPage>, translateY: Float, scale: Float, height: Int): IntRange {
        if (pages.isEmpty()) return IntRange.EMPTY
        val top = -translateY / scale
        val bottom = top + height / scale
        val first = pages.indexOfFirst { it.top + it.height >= top }
        val last = pages.indexOfLast { it.top <= bottom }
        return if (first !in 0..last) IntRange.EMPTY else first..last
    }
    fun current(pages: List<PdfPage>, translateY: Float, scale: Float, height: Int): Int {
        val top = -translateY / scale
        val bottom = top + height / scale
        return visible(pages, translateY, scale, height).maxByOrNull {
            min(pages[it].top + pages[it].height, bottom) - max(pages[it].top, top)
        } ?: 0
    }
    fun bitmapSize(width: Float, height: Float, maximumPixels: Long): Pair<Int, Int> {
        val factor = if (width * height > maximumPixels) sqrt(maximumPixels / (width.toDouble() * height)).toFloat() else 1f
        return max(1, (width * factor).roundToInt()) to max(1, (height * factor).roundToInt())
    }
}
