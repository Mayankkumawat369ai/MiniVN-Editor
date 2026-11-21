package com.mayank.minivn

import android.graphics.Color as AndroidColor
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import com.google.common.collect.ImmutableList

enum class VideoFilter(val title: String) {
    ORIGINAL("Original"),
    BW("B&W"),
    VINTAGE("Vintage"),
    COOL("Cool"),
    VIVID("Vivid"),
    CINEMATIC("Cinematic")
}

@UnstableApi
fun buildFilterEffects(filter: VideoFilter): List<Effect> {
    return when (filter) {
        VideoFilter.ORIGINAL -> emptyList()
        VideoFilter.BW -> listOf(RgbFilter.createGrayscaleFilter())
        VideoFilter.VINTAGE -> listOf(
            RgbAdjustment.Builder()
                .setRedScale(1.18f)
                .setGreenScale(1.04f)
                .setBlueScale(0.78f)
                .build()
        )
        VideoFilter.COOL -> listOf(
            RgbAdjustment.Builder()
                .setRedScale(0.85f)
                .setGreenScale(0.98f)
                .setBlueScale(1.22f)
                .build()
        )
        VideoFilter.VIVID -> listOf(
            Contrast(0.30f),
            RgbAdjustment.Builder()
                .setRedScale(1.12f)
                .setGreenScale(1.10f)
                .setBlueScale(1.12f)
                .build()
        )
        VideoFilter.CINEMATIC -> listOf(
            Contrast(0.40f),
            RgbAdjustment.Builder()
                .setRedScale(1.05f)
                .setGreenScale(0.95f)
                .setBlueScale(0.88f)
                .build()
        )
    }
}

@UnstableApi
fun buildExportEffects(
    filter: VideoFilter,
    overlayText: String,
    textAnchorX: Float,
    textAnchorY: Float,
    textScale: Float
): List<Effect> {
    val effects = buildFilterEffects(filter).toMutableList()

    if (overlayText.isNotBlank()) {
        val spannable = SpannableString("  $overlayText  ").apply {
            setSpan(
                ForegroundColorSpan(AndroidColor.WHITE),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                BackgroundColorSpan(0x99000000.toInt()),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                RelativeSizeSpan(2.0f),
                0,
                length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(textAnchorX, textAnchorY)
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(textScale, textScale)
            .build()

        val textOverlay = TextOverlay.createStaticTextOverlay(spannable, settings)
        effects.add(OverlayEffect(ImmutableList.of(textOverlay)))
    }

    return effects
}
