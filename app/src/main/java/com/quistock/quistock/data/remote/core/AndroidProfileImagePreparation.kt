package com.quistock.quistock.data.remote.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.quistock.quistock.domain.port.PreparedProfileImage
import com.quistock.quistock.domain.port.ProfileImageError
import com.quistock.quistock.domain.port.ProfileImagePreparation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AndroidProfileImagePreparation : ProfileImagePreparation {
    override suspend fun prepare(source: ByteArray): PreparedProfileImage = withContext(Dispatchers.Default) {
        when {
            source.size > MAX_INPUT -> PreparedProfileImage.Failed(ProfileImageError.TOO_LARGE)
            !StaticProfileImage.accepts(source) -> PreparedProfileImage.Failed(ProfileImageError.FORMAT)
            else -> decode(source)
        }
    }

    private fun decode(source: ByteArray): PreparedProfileImage = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds) }
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(source, 0, source.size, options))
        val oriented = orient(bitmap, source)
        val jpeg = encode(oriented)
        if (oriented !== bitmap) oriented.recycle()
        bitmap.recycle()
        PreparedProfileImage.Ready(jpeg)
    } catch (_: IllegalArgumentException) {
        PreparedProfileImage.Failed(ProfileImageError.PREPARATION)
    } catch (_: java.io.IOException) {
        PreparedProfileImage.Failed(ProfileImageError.PREPARATION)
    }

    private fun sample(bounds: BitmapFactory.Options): Int {
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > MAX_DIMENSION.toLong() * sample) sample *= 2
        return sample
    }

    private fun orient(bitmap: Bitmap, source: ByteArray): Bitmap {
        val exif = ExifInterface(source.inputStream())
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(HALF_TURN_DEGREES)

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(QUARTER_TURN_DEGREES)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(QUARTER_TURN_DEGREES)

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(THREE_QUARTER_TURN_DEGREES)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(THREE_QUARTER_TURN_DEGREES)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        Canvas(opaque).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        val output = ByteArrayOutputStream()
        opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        opaque.recycle()
        return output.toByteArray().also { require(it.size <= MAX_OUTPUT) }
    }

    private companion object {
        const val MAX_INPUT = 10 * 1024 * 1024
        const val MAX_OUTPUT = 2 * 1024 * 1024
        const val MAX_DIMENSION = 1024
        const val JPEG_QUALITY = 90
        const val QUARTER_TURN_DEGREES = 90f
        const val HALF_TURN_DEGREES = 180f
        const val THREE_QUARTER_TURN_DEGREES = 270f
    }
}
