package com.makaohub.android.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class VerificationImageRejected : Exception("Choose a readable JPEG, PNG or WebP image of at most 10 MB and 25 megapixels.")

internal fun validVerificationImageDimensions(width: Int, height: Int) =
    width > 0 && height > 0 && width.toLong() * height <= 25_000_000

/** In-memory JPEG preparation: bounded decoding, orientation correction and no copied EXIF/GPS metadata. */
internal object VerificationImages {
    private const val MAX_BYTES = 10_485_760
    fun thumbnail(bytes: ByteArray): ByteArray? {
        val image = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return null
        try {
            val output = ByteArrayOutputStream()
            return if (image.compress(Bitmap.CompressFormat.JPEG, 75, output) && output.size() <= 262144)
                output.toByteArray() else null
        } finally { image.recycle() }
    }
    fun fromUri(resolver: ContentResolver, uri: Uri): ByteArray {
        val mime = resolver.getType(uri)?.lowercase()
        if (mime !in listOf("image/jpeg", "image/png", "image/webp")) throw VerificationImageRejected()
        val encoded = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) throw VerificationImageRejected()
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw VerificationImageRejected()
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options)
        if (!validVerificationImageDimensions(options.outWidth, options.outHeight)) throw VerificationImageRejected()
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2400) options.inSampleSize *= 2
        val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options) ?: throw VerificationImageRejected()
        try {
            val orientation = if (mime == "image/jpeg") {
                ByteArrayInputStream(encoded).use { ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            } else ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            try { return fromBitmap(oriented) }
            finally { if (oriented !== decoded) oriented.recycle() }
        } finally { decoded.recycle() }
    }
    fun fromBitmap(bitmap: Bitmap): ByteArray {
        if (!validVerificationImageDimensions(bitmap.width, bitmap.height)) {
            throw VerificationImageRejected()
        }
        val output = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output) || output.size() !in 1..MAX_BYTES) {
            throw VerificationImageRejected()
        }
        return output.toByteArray()
    }
}
