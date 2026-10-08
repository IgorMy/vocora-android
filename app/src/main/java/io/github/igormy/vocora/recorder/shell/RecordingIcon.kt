package io.github.igormy.vocora.recorder.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon

private const val SIZE = 96f
private const val STROKE = 8f

/**
 * Draws the notification's microphone rather than naming one.
 *
 * The notification is posted as the shell package, so resources of this APK cannot be resolved for
 * it, and framework drawables are no good either: HyperOS replaces them, so asking for a microphone
 * by id got a download arrow. A bitmap built here is whatever we drew and nothing else.
 */
object RecordingIcon {

    fun microphone(): Icon {
        val bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        // The capsule.
        canvas.drawRoundRect(RectF(36f, 16f, 60f, 56f), 12f, 12f, paint)

        // The cradle under it, and the stem down to the base.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = STROKE
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(26f, 30f, 70f, 70f), 0f, 180f, false, paint)
        canvas.drawLine(48f, 70f, 48f, 82f, paint)

        return Icon.createWithBitmap(bitmap)
    }
}
