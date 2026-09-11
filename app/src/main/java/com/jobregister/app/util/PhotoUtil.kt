package com.jobregister.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Camera/gallery images: downscale, JPEG-compress and stamp with job info. */
object PhotoUtil {

    /** Read an image uri, downsample to a reasonable size and compress. */
    fun compress(context: Context, uri: Uri, maxDim: Int = 1600): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    /** Draw a label (job no + date/time) onto the bottom-left of the photo. */
    fun stamp(bytes: ByteArray, label: String): ByteArray {
        return try {
            val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
            val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(bmp)
            val textSize = bmp.width / 28f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                this.textSize = textSize
                isFakeBoldText = true
                setShadowLayer(textSize / 5f, 0f, 0f, android.graphics.Color.BLACK)
            }
            canvas.drawText(label, textSize / 2f, bmp.height - textSize / 2f, paint)
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } catch (_: Exception) {
            bytes
        }
    }
}
