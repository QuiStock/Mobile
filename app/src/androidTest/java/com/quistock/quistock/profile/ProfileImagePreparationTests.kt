package com.quistock.quistock.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.data.remote.core.AndroidProfileImagePreparation
import com.quistock.quistock.domain.port.PreparedProfileImage
import com.quistock.quistock.domain.port.ProfileImageError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileImagePreparationTests {
    private val preparation = AndroidProfileImagePreparation()

    @Test fun staticPngIsConvertedToBoundedJpegWithoutUpscaling() = runBlocking {
        val image = Bitmap.createBitmap(1025, 512, Bitmap.Config.ARGB_8888)
        val input = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val result = preparation.prepare(input.toByteArray()) as PreparedProfileImage.Ready
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(result.jpeg, 0, result.jpeg.size))
        assertTrue(decoded.width <= 1024)
        assertTrue(decoded.height <= 1024)
        assertTrue(result.jpeg.size <= 2 * 1024 * 1024)
        assertEquals(0xff.toByte(), result.jpeg[0])
        decoded.recycle()
    }

    @Test fun orientationIsAppliedAndMetadataIsRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("profile-exif-", ".jpg", context.cacheDir)
        try {
            val image = Bitmap.createBitmap(300, 150, Bitmap.Config.ARGB_8888)
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            image.recycle()
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_USER_COMMENT, "fixture")
                saveAttributes()
            }
            val prepared = preparation.prepare(file.readBytes()) as PreparedProfileImage.Ready
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(prepared.jpeg, 0, prepared.jpeg.size))
            assertEquals(150, decoded.width)
            assertEquals(300, decoded.height)
            assertNull(ExifInterface(prepared.jpeg.inputStream()).getAttribute(ExifInterface.TAG_USER_COMMENT))
            decoded.recycle()
        } finally {
            file.delete()
        }
    }

    @Test fun oversizedInputIsRejectedBeforeDecoding() = runBlocking {
        val result = preparation.prepare(ByteArray(10 * 1024 * 1024 + 1)) as PreparedProfileImage.Failed
        assertEquals(ProfileImageError.TOO_LARGE, result.reason)
    }
}
