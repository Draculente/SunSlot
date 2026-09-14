package de.sunslot.app.domain.renderer

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import de.sunslot.app.data.model.DayOutdoorWindow
import kotlin.math.roundToInt

/**
 * Renders a slim, rounded dark rail that spans the daylight period. The best
 * outdoor window lights up as a bright neon segment; "partially optimal" hours
 * directly adjacent to it flow into it as a dimmer ramp (one continuous pill,
 * no double rounded ends). Gaps between hours stay unlit.
 *
 * The light is clipped only vertically (to the rail height), so its glow may
 * shine softly past the rail's left/right caps without being cut off there.
 */
class TimelineBitmapRenderer(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    private val isLightMode: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES

    private val palette: Palette
        get() = if (isLightMode) LIGHT_PALETTE else DARK_PALETTE

    fun render(dayWindow: DayOutdoorWindow): Bitmap {
        Log.d(TAG, "render light=$isLightMode start=${dayWindow.startHour} end=${dayWindow.endHour} partial=${dayWindow.partialRanges}")
        val widthPx = (WIDTH_DP * density).roundToInt()
        val heightPx = (HEIGHT_DP * density).roundToInt()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val padding = PADDING_DP * density
        val trackTop = TRACK_TOP_DP * density
        val trackBottom = trackTop + TRACK_HEIGHT_DP * density
        val trackRect = RectF(padding, trackTop, widthPx - padding, trackBottom)
        val cornerRadius = (trackRect.height() / 2f).coerceAtMost(CORNER_RADIUS_DP * density)

        drawTrack(canvas, trackRect, cornerRadius, palette.trackColor)

        val (dayStart, dayEnd) = daylightSpan(dayWindow)
        val accent = accentForScore(dayWindow.score ?: NO_BEST_SCORE, isLightMode)
        val bright = blend(accent, Color.WHITE, palette.brightMixWhite)

        // One light layer for the whole day.
        val lightLayer = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val lightCanvas = Canvas(lightLayer)

        // Merge touching ranges (aura + best) into continuous groups so the
        // transition between "good" and "half-good" has a single rounded shape.
        val groups = collectGroups(dayWindow.partialRanges, dayWindow.startHour, dayWindow.endHour)

        groups.forEach { group ->
            val rect = rangeRect(group.start, group.end, trackRect, dayStart, dayEnd)
            if (rect != null && rect.width() > 1f) {
                drawGroup(lightCanvas, rect, group, cornerRadius, accent, bright, palette)
            }
        }

        applyVerticalMask(canvas, lightLayer, trackRect, palette.maskFeatherDp)
        lightLayer.recycle()

        drawHourLabels(canvas, trackRect, dayStart, dayEnd, palette.labelColor)

        return bitmap
    }

    private data class LitGroup(
        val start: Int,
        val end: Int,
        val bestStart: Int,
        val bestEnd: Int,
        val hasBest: Boolean
    )

    /** Per-mode colors and glow strengths for the timeline. */
    private data class Palette(
        val trackColor: Int,
        val labelColor: Int,
        val spillRadiusDp: Float,
        val haloRadiusDp: Float,
        val maskFeatherDp: Float,
        val spillInflateH: Float,
        val spillInflateV: Float,
        val haloInflateH: Float,
        val haloInflateV: Float,
        val brightMixWhite: Float,
        val dimLevel: Float
    )

    /** Merges ranges that touch each other, then clips the best window to each group. */
    private fun collectGroups(
        partial: List<IntRange>,
        bestStart: Int?,
        bestEnd: Int?
    ): List<LitGroup> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        partial.forEach { ranges.add(it.first to it.last) }
        if (bestStart != null && bestEnd != null) ranges.add(bestStart to bestEnd)
        ranges.sortBy { it.first }
        val groups = mutableListOf<LitGroup>()
        var curStart = -1
        var curEnd = -1

        for ((start, end) in ranges) {
            if (curStart < 0 || start > curEnd) {
                if (curStart >= 0) groups.add(LitGroup(curStart, curEnd, -1, -1, false))
                curStart = start
                curEnd = end
            } else {
                curEnd = maxOf(curEnd, end)
            }
        }
        if (curStart >= 0) groups.add(LitGroup(curStart, curEnd, -1, -1, false))

        if (bestStart == null || bestEnd == null) {
            // No best window exists: every group is only dimmed ("medium").
            return groups.map { LitGroup(it.start, it.end, it.start, it.start + 1, hasBest = false) }
        }

        return groups.map { group ->
            val clippedBestStart = maxOf(group.start, bestStart)
            val clippedBestEnd = minOf(group.end, bestEnd)
            if (clippedBestStart < clippedBestEnd) {
                LitGroup(group.start, group.end, clippedBestStart, clippedBestEnd, true)
            } else {
                LitGroup(group.start, group.end, group.start, group.start + 1, true)
            }
        }
    }

    private fun rangeRect(
        startHour: Int,
        endHourExclusive: Int,
        trackRect: RectF,
        dayStart: Double,
        dayEnd: Double
    ): RectF? {
        val startRatio = hourInSpan(startHour, dayStart, dayEnd)
        val endRatio = hourInSpan(endHourExclusive, dayStart, dayEnd)
        return RectF(
            trackRect.left + trackRect.width() * startRatio,
            trackRect.top,
            trackRect.left + trackRect.width() * endRatio,
            trackRect.bottom
        )
    }

    /** Returns (start hour as Double, end hour as Double) for the daylight span. */
    private fun daylightSpan(dayWindow: DayOutdoorWindow): Pair<Double, Double> {
        val sunrise = dayWindow.sunrise
        val sunset = dayWindow.sunset

        var start = sunrise.hour + sunrise.minute / 60.0
        var end = sunset.hour + sunset.minute / 60.0

        // Fallback: on very short spans keep a minimum of daylight context.
        if (end - start < MIN_SPAN_HOURS) {
            val mid = (start + end) / 2.0
            start = mid - MIN_SPAN_HOURS / 2.0
            end = mid + MIN_SPAN_HOURS / 2.0
        }
        return start to end
    }

    private fun hourInSpan(hour: Int, start: Double, end: Double): Float {
        val span = end - start
        if (span <= 0.0) return 0f
        return (((hour - start) / span).toFloat()).coerceIn(0f, 1f)
    }

    private fun drawTrack(canvas: Canvas, rect: RectF, cornerRadius: Float, trackColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = trackColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    private fun drawGroup(
        canvas: Canvas,
        rect: RectF,
        group: LitGroup,
        cornerRadius: Float,
        accent: Int,
        bright: Int,
        p: Palette
    ) {
        // Soft spill so the segment shines past its own ends.
        canvas.drawRoundRect(
            inflate(rect, p.spillInflateH * density, p.spillInflateV * density),
            cornerRadius, cornerRadius,
            haloPaint(accent, 0x26, p.spillRadiusDp)
        )
        canvas.drawRoundRect(
            inflate(rect, p.haloInflateH * density, p.haloInflateV * density),
            cornerRadius, cornerRadius,
            haloPaint(accent, 0x59, p.haloRadiusDp)
        )

        // One continuous pill whose brightness ramps along its length:
        // half-optimal hours glow dimly, the best window glows at full strength.
        canvas.drawRoundRect(
            rect, cornerRadius, cornerRadius,
            rampPaint(rect, group, bright, p.dimLevel)
        )
    }

    /** Horizontal alpha ramp along the pill so the whole group is one shape. */
    private fun rampPaint(rect: RectF, group: LitGroup, bright: Int, dimLevel: Float): Paint {
        val span = (group.end - group.start).coerceAtLeast(1)
        val steps = span * 8
        val colors = IntArray(steps + 2)
        val positions = FloatArray(steps + 2)
        brightAlpha(group, group.start.toDouble(), 0f, colors, positions, 0, span, bright, dimLevel)
        for (i in 1..steps) {
            val h = group.start + i / 8.0
            brightAlpha(group, h, (i / 8.0 / span).toFloat(), colors, positions, i, span, bright, dimLevel)
        }
        brightAlpha(group, group.end.toDouble(), 1f, colors, positions, steps + 1, span, bright, dimLevel)

        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, 0f, rect.right, 0f,
                colors, positions, Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
    }

    private fun brightAlpha(
        group: LitGroup,
        hour: Double,
        position: Float,
        colors: IntArray,
        positions: FloatArray,
        index: Int,
        span: Int,
        bright: Int,
        dimLevel: Float
    ) {
        positions[index] = position
        colors[index] = withAlpha(bright, hourAlpha(group, hour, dimLevel))
    }

    /** Brightness (0..255) at a fractional [hour] inside the group. */
    private fun hourAlpha(group: LitGroup, hour: Double, dimLevel: Float): Int {
        val edgeIn = ((hour - group.start) / EDGE_FADE_HOURS).toFloat().coerceIn(0f, 1f)
        val edgeOut = ((group.end - hour) / EDGE_FADE_HOURS).toFloat().coerceIn(0f, 1f)
        val edge = minOf(edgeIn, edgeOut)

        val level = if (group.hasBest) {
            // Blend from aura level to full strength over BLEND_HOURS around the
            // best window boundaries, so there is no hard step between bright/dim.
            val bestIn = ((hour - (group.bestStart - BLEND_HOURS)) / BLEND_HOURS).toFloat().coerceIn(0f, 1f)
            val bestOut = ((group.bestEnd - hour) / BLEND_HOURS).toFloat().coerceIn(0f, 1f)
            val best = smoothstep(minOf(bestIn, bestOut))
            dimLevel + (1f - dimLevel) * best
        } else {
            dimLevel
        }

        val alpha = (255f * level * edge).toInt()
        return alpha.coerceIn(0, 255)
    }

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    /**
     * Clips the light layer vertically to the rail height only. The mask spans
     * horizontally far beyond the rail, so glow may extend past the left/right
     * caps without being cut, while staying tightly within the rail vertically.
     */
    private fun applyVerticalMask(
        canvas: Canvas,
        lightLayer: Bitmap,
        trackRect: RectF,
        featherDp: Float
    ) {
        val mask = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ALPHA_8)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            maskFilter = BlurMaskFilter(featherDp * density, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(mask).drawRect(
            RectF(
                trackRect.left - H_MASK_OVERHANG_DP * density,
                trackRect.top,
                trackRect.right + H_MASK_OVERHANG_DP * density,
                trackRect.bottom
            ),
            maskPaint
        )

        canvas.drawBitmap(lightLayer, 0f, 0f, null)
        canvas.drawBitmap(
            mask, 0f, 0f,
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        )
        mask.recycle()
    }

    private fun haloPaint(base: Int, alpha: Int, radiusDp: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(base, alpha)
            maskFilter = BlurMaskFilter(radiusDp * density, BlurMaskFilter.Blur.NORMAL)
        }

    private fun drawHourLabels(
        canvas: Canvas,
        trackRect: RectF,
        dayStart: Double,
        dayEnd: Double,
        labelColor: Int
    ) {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor
            textSize = LABEL_TEXT_SIZE_DP * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        val span = dayEnd - dayStart
        val spanWidth = trackRect.width()
        val labelY = trackRect.bottom + LABEL_TOP_MARGIN_DP * density + labelPaint.textSize

        for (labelHour in HOUR_LABELS) {
            if (labelHour < dayStart + LABEL_PADDING_HOURS || labelHour > dayEnd - LABEL_PADDING_HOURS) continue
            val x = trackRect.left + spanWidth * ((labelHour - dayStart) / span).toFloat()
            canvas.drawText(labelHour.toString(), x, labelY, labelPaint)
        }
    }

    private fun inflate(rect: RectF, dx: Float, dy: Float): RectF =
        RectF(rect).apply { inset(-dx, -dy) }

    private fun accentForScore(score: Double, light: Boolean): Int {
        return if (light) {
            when {
                score >= 80 -> 0xFF00C853.toInt()
                score >= 60 -> 0xFFFFB300.toInt()
                else -> 0xFFF4511E.toInt()
            }
        } else {
            when {
                score >= 80 -> 0xFF43FA7B.toInt()
                score >= 60 -> 0xFFFFD166.toInt()
                else -> 0xFFFF7E5C.toInt()
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    private fun blend(color: Int, other: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val inv = 1f - f
        val r = (Color.red(color) * inv + Color.red(other) * f).toInt()
        val g = (Color.green(color) * inv + Color.green(other) * f).toInt()
        val b = (Color.blue(color) * inv + Color.blue(other) * f).toInt()
        return Color.rgb(r, g, b)
    }

    companion object {
        private const val TAG = "TimelineRenderer"

        private const val WIDTH_DP = 240f
        private const val HEIGHT_DP = 48f
        // Horizontal room so the glow can spill past the rail caps without
        // being cut off at the bitmap edge.
        private const val PADDING_DP = 16f
        private const val TRACK_TOP_DP = 11f
        private const val TRACK_HEIGHT_DP = 14f
        private const val CORNER_RADIUS_DP = 7f

        // Accent used for days without a rain-free best window (medium-only).
        private const val NO_BEST_SCORE = 55.0

        // Dark mode: bright neon rail on a dark background.
        private val DARK_PALETTE = Palette(
            trackColor = 0xFF222227.toInt(),
            labelColor = 0xFF989EA8.toInt(),
            spillRadiusDp = 9f,
            haloRadiusDp = 3.5f,
            maskFeatherDp = 5f,
            spillInflateH = 4f,
            spillInflateV = 2f,
            haloInflateH = 1.5f,
            haloInflateV = 1f,
            brightMixWhite = 0.6f,
            dimLevel = 0.32f
        )

        // Light mode: white rail, more saturated highlights, less blur so the
        // colors stay crisp on a light widget background.
        private val LIGHT_PALETTE = Palette(
            trackColor = 0xFFFFFFFF.toInt(),
            labelColor = 0xFF5F6368.toInt(),
            spillRadiusDp = 6f,
            haloRadiusDp = 2.5f,
            maskFeatherDp = 4f,
            spillInflateH = 3f,
            spillInflateV = 1.5f,
            haloInflateH = 1f,
            haloInflateV = 0.5f,
            brightMixWhite = 0.25f,
            dimLevel = 0.18f
        )

        private const val H_MASK_OVERHANG_DP = 60f

        // Fraction fading in/out over this many hours at group ends.
        private const val EDGE_FADE_HOURS = 1.0
        // Hours over which the best-window boundary blends from aura to full.
        private const val BLEND_HOURS = 1.0

        private const val LABEL_TEXT_SIZE_DP = 11f
        private const val LABEL_TOP_MARGIN_DP = 2f
        private const val LABEL_PADDING_HOURS = 0.8
        private const val MIN_SPAN_HOURS = 8.0

        private val HOUR_LABELS = listOf(6, 8, 10, 12, 14, 16, 18, 20)
    }
}