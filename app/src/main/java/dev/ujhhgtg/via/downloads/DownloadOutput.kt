package dev.ujhhgtg.via.downloads

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

/** k5.d/n/u: buffered output, explicit range seek and original allocation/flush behavior. */
internal abstract class DownloadOutput : OutputStream() {
    abstract fun length(): Long
    abstract fun resize(value: Long)
    abstract fun seek(value: Long)
    abstract fun sync()
    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

    private class Local(file: File) : DownloadOutput() {
        private val access = RandomAccessFile(file, "rw")
        private val stream = BufferedOutputStream(FileOutputStream(access.fd))
        override fun length() = access.length()
        override fun resize(value: Long) = access.setLength(value)
        override fun seek(value: Long) = access.seek(value)
        override fun write(bytes: ByteArray, offset: Int, length: Int) = stream.write(bytes, offset, length)
        override fun sync() { stream.flush(); access.fd.sync() }
        override fun close() { stream.close(); access.close() }
    }

    private class Content(private val asset: AssetFileDescriptor) : DownloadOutput() {
        private val descriptor = asset.fileDescriptor
        private val stream = BufferedOutputStream(FileOutputStream(descriptor))
        override fun length() = asset.length
        override fun resize(value: Long) {
            try {
                val current = Os.fstat(descriptor).st_size
                val volume = Os.fstatvfs(descriptor)
                if (volume.f_bavail * volume.f_bsize < value - current) throw IOException("write failed: ENOSPC(No space left on device)")
                Os.posix_fallocate(descriptor, 0, value)
            } catch (_: Exception) {
                try { Os.ftruncate(descriptor, value) } catch (failure: Exception) { throw IOException(failure) }
            }
        }
        override fun seek(value: Long) {
            try { Os.lseek(descriptor, value, OsConstants.SEEK_SET) } catch (failure: Exception) { throw IOException(failure) }
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) = stream.write(bytes, offset, length)
        override fun sync() { stream.flush(); descriptor.sync() }
        override fun close() { stream.close(); asset.close() }
    }

    companion object {
        fun open(context: Context, record: DownloadRecord): DownloadOutput {
            val uri = record.fileUri
            // e5.c.g selects k5.u for every destination, including file://.
            if (uri?.path == null) throw IllegalArgumentException("File uri can not be null or empty")
            return Content(context.contentResolver.openAssetFileDescriptor(uri, "rw", null)
                ?: throw FileNotFoundException("Unable to create stream"))
        }
    }
}
