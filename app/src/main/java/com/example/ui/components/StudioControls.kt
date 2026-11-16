package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalStudioPalette
import com.example.ui.theme.TimecodeTextStyle

@Composable
fun StudioOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalStudioPalette.current.playheadCyan,
    accentContainer: Color = LocalStudioPalette.current.cyanMuted,
    leadingIcon: ImageVector? = null,
    leadingDotColor: Color? = null
) {
    val palette = LocalStudioPalette.current
    val bgColor = if (selected) accentContainer else palette.elevatedTrack
    val borderColor = if (selected) accentColor else palette.hairlineBorder
    val contentColor = if (selected) accentColor else palette.textPrimary

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(if (selected) 1.dp else 0.5.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingDotColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(leadingDotColor)
                    .border(0.75.dp, palette.hairlineBorder, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) accentColor else palette.textSecondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StudioTogglePill(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = LocalStudioPalette.current.playheadCyan
) {
    val palette = LocalStudioPalette.current
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) activeColor.copy(alpha = 0.16f) else palette.elevatedTrack)
            .border(
                width = if (checked) 1.dp else 0.5.dp,
                color = if (checked) activeColor else palette.hairlineBorder,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (checked) activeColor else palette.textMuted)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                color = if (checked) palette.textPrimary else palette.textSecondary
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun StudioScrubSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    accentColor: Color = LocalStudioPalette.current.playheadCyan,
    centerOrigin: Boolean = false,
    defaultValue: Float? = null,
    height: Dp = 28.dp,
    markerNormA: Float? = null,
    markerNormB: Float? = null
) {
    val palette = LocalStudioPalette.current
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val norm = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val originNorm = if (centerOrigin && valueRange.start < 0f && valueRange.endInclusive > 0f) {
        ((0f - valueRange.start) / span).coerceIn(0f, 1f)
    } else 0f

    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestRange by rememberUpdatedState(valueRange)
    val latestDefault by rememberUpdatedState(defaultValue)

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .pointerInput(span) {
                detectTapGestures(
                    onTap = { pos ->
                        val frac = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        val mapped = latestRange.start + frac * (latestRange.endInclusive - latestRange.start)
                        latestOnChange(mapped.coerceIn(latestRange.start, latestRange.endInclusive))
                    },
                    onDoubleTap = {
                        latestDefault?.let { def ->
                            latestOnChange(def.coerceIn(latestRange.start, latestRange.endInclusive))
                        }
                    }
                )
            }
            .pointerInput(span) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val mapped = latestRange.start + frac * (latestRange.endInclusive - latestRange.start)
                    latestOnChange(mapped.coerceIn(latestRange.start, latestRange.endInclusive))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hPad = 6.dp.toPx()
            val trackW = (size.width - hPad * 2f).coerceAtLeast(4f)
            val trackH = 4.dp.toPx()
            val cy = size.height / 2f

            // Track bed
            drawRoundRect(
                color = palette.voidBg,
                topLeft = Offset(hPad, cy - trackH / 2f),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = palette.hairlineBorder,
                topLeft = Offset(hPad, cy - trackH / 2f),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(0.75.dp.toPx())
            )

            // Active fill segment
            val fillStartFrac = minOf(originNorm, norm)
            val fillEndFrac = maxOf(originNorm, norm)
            val fillX = hPad + fillStartFrac * trackW
            val fillW = ((fillEndFrac - fillStartFrac) * trackW).coerceAtLeast(0f)
            if (fillW > 0.5f) {
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(fillX, cy - trackH / 2f),
                    size = Size(fillW, trackH),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }

            // Center zero detent tick for bipolar grading sliders
            if (centerOrigin && originNorm in 0.05f..0.95f) {
                val zeroX = hPad + originNorm * trackW
                drawLine(
                    color = palette.textSecondary.copy(alpha = 0.65f),
                    start = Offset(zeroX, cy - 6.dp.toPx()),
                    end = Offset(zeroX, cy + 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Optional A/B Loop markers for ProPlayer
            markerNormA?.let { aFrac ->
                val ax = hPad + aFrac.coerceIn(0f, 1f) * trackW
                drawLine(
                    color = palette.audioAmber,
                    start = Offset(ax, cy - 8.dp.toPx()),
                    end = Offset(ax, cy + 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
            markerNormB?.let { bFrac ->
                val bx = hPad + bFrac.coerceIn(0f, 1f) * trackW
                drawLine(
                    color = palette.readyEmerald,
                    start = Offset(bx, cy - 8.dp.toPx()),
                    end = Offset(bx, cy + 8.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Precision vertical fader cap
            val thumbX = hPad + norm * trackW
            val thumbW = 10.dp.toPx()
            val thumbH = 16.dp.toPx()
            drawRoundRect(
                color = palette.textPrimary,
                topLeft = Offset(thumbX - thumbW / 2f, cy - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
            )
            drawRoundRect(
                color = accentColor,
                topLeft = Offset(thumbX - thumbW / 2f, cy - thumbH / 2f),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.25.dp.toPx())
            )
        }
    }
}

@Composable
fun StudioRangeBar(
    startValue: Float,
    endValue: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onRangeChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    minGap: Float = 250f,
    accentColor: Color = LocalStudioPalette.current.playheadCyan
) {
    val palette = LocalStudioPalette.current
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1f)
    val safeStart = startValue.coerceIn(valueRange.start, (valueRange.endInclusive - minGap).coerceAtLeast(valueRange.start))
    val safeEnd = endValue.coerceIn((safeStart + minGap).coerceAtMost(valueRange.endInclusive), valueRange.endInclusive)

    val startFrac = ((safeStart - valueRange.start) / span).coerceIn(0f, 1f)
    val endFrac = ((safeEnd - valueRange.start) / span).coerceIn(0f, 1f)

    val latestStart by rememberUpdatedState(safeStart)
    val latestEnd by rememberUpdatedState(safeEnd)
    val latestOnChange by rememberUpdatedState(onRangeChange)
    val latestRange by rememberUpdatedState(valueRange)

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(span, minGap) {
                var draggingStart = true
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val hPad = 8.dp.toPx()
                        val trackW = (size.width - hPad * 2f).coerceAtLeast(1f)
                        val touchFrac = ((offset.x - hPad) / trackW).coerceIn(0f, 1f)
                        val sF = ((latestStart - latestRange.start) / span)
                        val eF = ((latestEnd - latestRange.start) / span)
                        draggingStart = kotlin.math.abs(touchFrac - sF) <= kotlin.math.abs(touchFrac - eF)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val hPad = 8.dp.toPx()
                        val trackW = (size.width - hPad * 2f).coerceAtLeast(1f)
                        val frac = ((change.position.x - hPad) / trackW).coerceIn(0f, 1f)
                        val rawVal = latestRange.start + frac * span
                        if (draggingStart) {
                            val clamped = rawVal.coerceIn(latestRange.start, (latestEnd - minGap).coerceAtLeast(latestRange.start))
                            latestOnChange(clamped, latestEnd)
                        } else {
                            val clamped = rawVal.coerceIn((latestStart + minGap).coerceAtMost(latestRange.endInclusive), latestRange.endInclusive)
                            latestOnChange(latestStart, clamped)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val hPad = 8.dp.toPx()
            val trackW = (size.width - hPad * 2f).coerceAtLeast(4f)
            val barH = 12.dp.toPx()
            val topY = (size.height - barH) / 2f

            // Dimmed outer source timeline
            drawRoundRect(
                color = palette.voidBg,
                topLeft = Offset(hPad, topY),
                size = Size(trackW, barH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = palette.hairlineBorder,
                topLeft = Offset(hPad, topY),
                size = Size(trackW, barH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(0.75.dp.toPx())
            )

            // Active trim bracket region
            val x1 = hPad + startFrac * trackW
            val x2 = hPad + endFrac * trackW
            drawRect(
                color = accentColor.copy(alpha = 0.25f),
                topLeft = Offset(x1, topY),
                size = Size((x2 - x1).coerceAtLeast(2f), barH)
            )
            drawLine(accentColor, Offset(x1, topY), Offset(x2, topY), 1.5.dp.toPx())
            drawLine(accentColor, Offset(x1, topY + barH), Offset(x2, topY + barH), 1.5.dp.toPx())

            // Left & right bracket handles
            val capW = 8.dp.toPx()
            val capH = 18.dp.toPx()
            val capY = (size.height - capH) / 2f
            drawRoundRect(accentColor, Offset(x1 - capW / 2f, capY), Size(capW, capH), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
            drawRoundRect(accentColor, Offset(x2 - capW / 2f, capY), Size(capW, capH), CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        }
    }
}

@Composable
fun AspectRatioWireframeIcon(
    floatRatio: Float,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val maxBox = 20.dp
    val clamped = floatRatio.coerceIn(0.45f, 2.4f)
    val (wFrac, hFrac) = if (clamped >= 1f) {
        1f to (1f / clamped).coerceAtLeast(0.42f)
    } else {
        clamped.coerceAtLeast(0.42f) to 1f
    }
    Box(
        modifier = modifier.size(maxBox),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = maxBox * wFrac, height = maxBox * hFrac)
                .border(1.4.dp, tint, RoundedCornerShape(2.dp))
                .background(tint.copy(alpha = 0.14f), RoundedCornerShape(2.dp))
        )
    }
}
