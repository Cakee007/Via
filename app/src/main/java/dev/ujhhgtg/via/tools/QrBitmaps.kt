package dev.ujhhgtg.via.tools

import android.graphics.Bitmap

object QrBitmaps {
    fun generate(content: String): Bitmap {
        val matrix = QrCodec.encode(content)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) pixels[y * matrix.width + x] = if (matrix[x, y]) 0xff000000.toInt() else -1
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
    fun decode(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return QrCodec.decodeImage(bitmap.width, bitmap.height, pixels)
    }
}
