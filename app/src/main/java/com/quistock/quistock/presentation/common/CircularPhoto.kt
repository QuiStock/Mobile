package com.quistock.quistock.presentation.common

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory

/** Shows a photo center-cropped into a circle, whatever its original aspect ratio. */
internal object CircularPhoto {
    fun apply(view: ImageView, bitmap: Bitmap) {
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        view.setImageDrawable(drawable(view.resources, bitmap))
    }

    private fun drawable(resources: Resources, bitmap: Bitmap): Drawable {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        return RoundedBitmapDrawableFactory.create(resources, square).apply { isCircular = true }
    }
}
