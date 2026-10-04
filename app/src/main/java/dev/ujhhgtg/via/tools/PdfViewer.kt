package dev.ujhhgtg.via.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** PdfRenderer view recovered from the original PdfViewer; PDF operations share one worker. */
class PdfViewer(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val cache = object : LruCache<String, Bitmap>(max(8192, (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())) {
        override fun sizeOf(key: String, value: Bitmap) = max(1, value.byteCount / 1024)
    }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val disk = File(context.externalCacheDir ?: context.cacheDir, "pdf_viewer").apply { mkdirs() }
    private val pending = mutableSetOf<String>()
    private val scroller = OverScroller(context)
    private var renderer: PdfRenderer? = null
    private var sizes = emptyList<PdfPageSize>()
    private var pages = emptyList<PdfPage>()
    private var source = ""
    private var fileStamp = 0L
    private var closed = false
    private var offsetX = 0f
    private var offsetY = 0f
    private var zoom = 1f
    private var currentPage = -1
    private var offscreenPages = 2
    var onPageChanged: ((Int, Int) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onInteraction: ((Boolean) -> Unit)? = null
    private var minimumZoom = .5f
    private var maximumZoom = 10f
    private var fastScrolling = false

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean { scroller.abortAnimation(); return true }
        override fun onScroll(first: MotionEvent?, current: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!scaling.isInProgress) move(offsetX - dx, offsetY - dy)
            return true
        }
        override fun onFling(first: MotionEvent?, last: MotionEvent, vx: Float, vy: Float): Boolean {
            if (first == null || (abs(first.x - last.x) < 100 && abs(first.y - last.y) < 100)) return false
            val velocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
            if (abs(vx) < velocity && abs(vy) < velocity) return false
            scroller.fling(offsetX.roundToInt(), offsetY.roundToInt(), vx.roundToInt(), vy.roundToInt(), minX().roundToInt(), 0, minY().roundToInt(), 0)
            postInvalidateOnAnimation(); return true
        }
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean = performClick()
    })
    private val scaling = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val previous = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(minimumZoom, maximumZoom)
            move(detector.focusX - (detector.focusX - offsetX) * zoom / previous, detector.focusY - (detector.focusY - offsetY) * zoom / previous)
            return true
        }
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (abs(zoom - 1f) <= .06f && abs(offsetX) <= dp(24f) && abs(offsetY) <= dp(24f)) { zoom = 1f; move(0f, 0f) }
            requestPages()
        }
    })

    init { isFocusable = true; isVerticalScrollBarEnabled = true; setBackgroundColor(0xff666666.toInt()) }
    fun setOnPageChangedListener(listener: ((Int, Int) -> Unit)?) { onPageChanged = listener }
    fun open(uri: Uri) {
        source = uri.toString(); fileStamp = if (uri.scheme == "file") File(uri.path.orEmpty()).lastModified() else 0
        closed = false; pages = emptyList(); sizes = emptyList(); pending.clear(); cache.evictAll(); zoom = 1f; offsetX = 0f; offsetY = 0f; currentPage = -1
        worker.execute {
            val result = runCatching {
                renderer?.close(); renderer = null
                val descriptor = when (uri.scheme) {
                    "file" -> ParcelFileDescriptor.open(File(requireNotNull(uri.path)), ParcelFileDescriptor.MODE_READ_ONLY)
                    "content" -> context.contentResolver.openFileDescriptor(uri, "r")
                    else -> throw IllegalArgumentException("PDF requires a local file or content URI")
                }
                val pdf = try { PdfRenderer(requireNotNull(descriptor)) } catch (failure: Throwable) { descriptor?.close(); throw failure }
                renderer = pdf
                (0 until pdf.pageCount).map { index -> pdf.openPage(index).use { PdfPageSize(it.width, it.height) } }
            }
            main.post { if (!closed) result.fold({ sizes = it; layoutPages() }, { onError?.invoke(it) }) }
        }
    }
    fun close() {
        if (closed) return
        closed = true; main.removeCallbacksAndMessages(null); cache.evictAll()
        worker.execute { renderer?.close(); renderer = null }
        worker.shutdown()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); layoutPages() }
    private fun layoutPages() {
        if (width <= 0 || sizes.isEmpty()) return
        val oldHeight = pages.lastOrNull()?.let { it.top + it.height } ?: 0f
        val fraction = if (oldHeight == 0f) 0f else -offsetY / zoom / oldHeight
        pages = PdfLayout.pages(sizes, width.toFloat(), dp(8f))
        offsetY = -fraction * pages.last().let { it.top + it.height } * zoom
        currentPage = -1; move(offsetX, offsetY)
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pages.isEmpty()) return
        canvas.withTranslation(offsetX, offsetY) {
            scale(zoom, zoom)
            for (index in PdfLayout.visible(pages, offsetY, zoom, height)) {
                val page = pages[index]
                val rect = RectF(page.left, page.top, page.left + page.width, page.top + page.height)
                paint.color = Color.WHITE; drawRect(rect, paint)
                val bitmap = cache.get(detailKey(page)) ?: cache.get(baseKey(page))
                if (bitmap != null) drawBitmap(bitmap, null, rect, paint)
            }
        }
    }
    private fun requestPages() {
        if (closed || pages.isEmpty()) return
        val visible = PdfLayout.visible(pages, offsetY, zoom, height)
        if (visible.isEmpty()) return
        for (index in visible) requestBitmap(pages[index], false)
        if (!scaling.isInProgress) for (index in visible) requestBitmap(pages[index], true)
        for (index in max(0, currentPage - offscreenPages)..min(pages.lastIndex, currentPage + offscreenPages)) requestBitmap(pages[index], false)
    }
    private fun requestBitmap(page: PdfPage, detail: Boolean) {
        val key = if (detail) detailKey(page) else baseKey(page)
        if (cache.get(key) != null || !pending.add(key)) return
        val size = if (detail) PdfLayout.bitmapSize(page.width * zoom, page.height * zoom, 8_000_000)
            else PdfLayout.bitmapSize(sizes[page.index].width.toFloat(), sizes[page.index].height.toFloat(), 6_000_000)
        worker.execute {
            val bitmap = runCatching {
                val file = File(disk, "$key.png")
                val cached = if (!detail && file.isFile) BitmapFactory.decodeFile(file.path)?.also { file.setLastModified(System.currentTimeMillis()) } else null
                cached ?: renderer?.openPage(page.index)?.use { pdfPage ->
                    createBitmap(size.first, size.second).also { bitmap ->
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        if (!detail) { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; trimDisk() }
                    }
                }
            }.getOrNull()
            main.post { pending.remove(key); if (!closed && bitmap != null) { cache.put(key, bitmap); invalidate() } }
        }
    }
    private fun baseKey(page: PdfPage): String = hash("${source}_${fileStamp}_${page.index}_${page.width.roundToInt()}_${page.height.roundToInt()}")
    private fun detailKey(page: PdfPage): String = baseKey(page) + "-" + (zoom * 100).roundToInt()
    private fun hash(value: String) = BigInteger(1, MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))).toString(16)
    private fun trimDisk() {
        val files = disk.listFiles()?.filter { it.isFile && it.extension == "png" }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) { if (total <= 20 * 1024 * 1024) break; val bytes = file.length(); if (file.delete()) total -= bytes }
    }
    private fun updatePage() {
        val page = PdfLayout.current(pages, offsetY, zoom, height)
        if (page != currentPage) { currentPage = page; onPageChanged?.invoke(page, pages.size) }
    }
    private fun minX() = min(0f, width - width * zoom)
    private fun minY() = min(0f, height - ((pages.lastOrNull()?.let { it.top + it.height } ?: 0f) + dp(8f)) * zoom)
    private fun move(x: Float, y: Float) {
        offsetX = if (zoom < 1f) width * (1 - zoom) / 2 else x.coerceIn(minX(), 0f)
        offsetY = y.coerceIn(minY(), 0f)
        updatePage(); requestPages(); awakenScrollBars(); invalidate()
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        onInteraction?.invoke(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) fastScrolling = pages.isNotEmpty() && event.x > width - dp(24f)
        if (fastScrolling) {
            move(offsetX, minY() * (event.y / height).coerceIn(0f, 1f))
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) fastScrolling = false
            return true
        }
        scaling.onTouchEvent(event)
        if (!scaling.isInProgress) gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) requestPages()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun computeScroll() { if (scroller.computeScrollOffset()) { move(scroller.currX.toFloat(), scroller.currY.toFloat()); postInvalidateOnAnimation() } }
    override fun scrollTo(x: Int, y: Int) = move(-x.toFloat(), -y.toFloat())
    override fun scrollBy(x: Int, y: Int) = move(offsetX - x, offsetY - y)
    override fun computeVerticalScrollOffset(): Int = (-offsetY).roundToInt()
    override fun computeVerticalScrollRange(): Int = ((pages.lastOrNull()?.let { it.top + it.height } ?: 0f) * zoom).roundToInt()
    override fun computeVerticalScrollExtent(): Int = height
}
