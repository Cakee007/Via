package dev.ujhhgtg.via.tools

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** t5/z + c8/yb: original generation parameters and image/camera format choices. */
object QrCodec {
    fun sideLength(content: String): Int { var side = 512; while (side < content.length) side *= 2; return side }
    fun encode(content: String, side: Int = sideLength(content)): BitMatrix = MultiFormatWriter().encode(
        content, BarcodeFormat.QR_CODE, side, side,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 1),
    )
    fun decodeImage(width: Int, height: Int, pixels: IntArray): String? = runCatching {
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))), mapOf(
            DecodeHintType.CHARACTER_SET to "utf-8", DecodeHintType.TRY_HARDER to true,
        )).text
    }.getOrNull()
}
