package com.xmoney.payments.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class ColorSchemePreference(val value: String) {
    AUTOMATIC("automatic"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun from(raw: String?): ColorSchemePreference = when (raw?.trim()?.lowercase()) {
            "light", "alwayslight" -> LIGHT
            "dark", "alwaysdark" -> DARK
            "auto", "automatic", null -> AUTOMATIC
            else -> {
                AppearanceLog.warn("unknown colorScheme '$raw', using automatic")
                AUTOMATIC
            }
        }
    }
}

/** sRGB color, channels 0...1, plus alpha. */
data class SrgbColor(
    val red: Double,
    val green: Double,
    val blue: Double,
    val alpha: Double = 1.0,
) {
    val argb: Int
        get() {
            fun byte(channel: Double) = kotlin.math.round(channel.coerceIn(0.0, 1.0) * 255).toInt()
            return (byte(alpha) shl 24) or (byte(red) shl 16) or (byte(green) shl 8) or byte(blue)
        }

    val hex: String
        get() = String.format("#%06X", argb and 0xFFFFFF)

    fun mixed(over: SrgbColor, weight: Double): SrgbColor {
        val t = weight.coerceIn(0.0, 1.0)
        return SrgbColor(
            red * t + over.red * (1 - t),
            green * t + over.green * (1 - t),
            blue * t + over.blue * (1 - t),
        )
    }

    fun withAlpha(alpha: Double) = copy(alpha = alpha)

    fun composited(over: SrgbColor): SrgbColor {
        val a = alpha.coerceIn(0.0, 1.0)
        return SrgbColor(
            red * a + over.red * (1 - a),
            green * a + over.green * (1 - a),
            blue * a + over.blue * (1 - a),
        )
    }

    val relativeLuminance: Double
        get() {
            fun linear(channel: Double) =
                if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
            return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
        }

    fun contrast(against: SrgbColor): Double {
        val a = relativeLuminance
        val b = against.relativeLuminance
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** Contrast after this color is painted over [base]. Alpha is part of the result. */
    fun contrast(against: SrgbColor, compositedOver: SrgbColor): Double =
        composited(over = compositedOver).contrast(against)

    val isAchromatic: Boolean
        get() = maxOf(red, green, blue) - minOf(red, green, blue) < 0.04

    /**
     * Move lightness in OKLCH until contrast against [against] is reached, keeping hue.
     * Stops at the smallest lightness change that clears [atLeast]. Chroma is reduced per
     * sample so one out-of-gamut lightness does not dull the rest of the search.
     * When the target is unreachable, returns ink or paper, whichever contrasts more.
     */
    fun withContrast(atLeast: Double, against: SrgbColor): SrgbColor {
        if (contrast(against) >= atLeast) return this
        val oklch = Oklch.from(this)
        val towardWhite = SrgbColor.WHITE.contrast(against) >= SrgbColor.BLACK.contrast(against)
        var low = if (towardWhite) oklch.l else 0.0
        var high = if (towardWhite) 1.0 else oklch.l
        var passing: SrgbColor? = null
        var passingDistance = Double.POSITIVE_INFINITY
        fun sample(lightness: Double): SrgbColor? {
            var attempt = oklch.c
            for (step in 0 until 16) {
                val color = Oklch(lightness, attempt, oklch.h).toSrgb()
                if (color != null) return color
                if (attempt <= 0.0001) break
                attempt *= 0.85
            }
            return Oklch(lightness, 0.0, oklch.h).toSrgb()
        }
        repeat(24) {
            val mid = (low + high) / 2
            val color = sample(mid) ?: return@repeat
            if (color.contrast(against) >= atLeast) {
                val distance = kotlin.math.abs(mid - oklch.l)
                if (distance < passingDistance) {
                    passing = color
                    passingDistance = distance
                }
                if (towardWhite) high = mid else low = mid
            } else if (towardWhite) {
                low = mid
            } else {
                high = mid
            }
        }
        passing?.let { return it }
        val fallback = if (SrgbColor.INK.contrast(against) >= SrgbColor.PAPER.contrast(against)) {
            SrgbColor.INK
        } else {
            SrgbColor.PAPER
        }
        AppearanceLog.warn(
            "contrast $atLeast:1 is unreachable on this surface; using ${if (fallback == SrgbColor.INK) "ink" else "paper"}",
        )
        return fallback
    }

    companion object {
        val WHITE = SrgbColor(1.0, 1.0, 1.0)
        val BLACK = SrgbColor(0.0, 0.0, 0.0)
        val INK = fromHex("#16141A")!!
        val PAPER = fromHex("#FAFAFA")!!

        fun fromArgb(argb: Int): SrgbColor = SrgbColor(
            ((argb shr 16) and 0xFF) / 255.0,
            ((argb shr 8) and 0xFF) / 255.0,
            (argb and 0xFF) / 255.0,
            ((argb ushr 24) and 0xFF) / 255.0,
        )

        fun fromHex(hex: String): SrgbColor? {
            var raw = hex.trim()
            if (raw.startsWith("#")) raw = raw.substring(1)
            val value = raw.toLongOrNull(16) ?: return null
            return when (raw.length) {
                6 -> SrgbColor(
                    ((value shr 16) and 0xFF) / 255.0,
                    ((value shr 8) and 0xFF) / 255.0,
                    (value and 0xFF) / 255.0,
                )
                8 -> SrgbColor(
                    ((value shr 16) and 0xFF) / 255.0,
                    ((value shr 8) and 0xFF) / 255.0,
                    (value and 0xFF) / 255.0,
                    ((value shr 24) and 0xFF) / 255.0,
                )
                else -> null
            }
        }
    }
}

@Parcelize
data class ThemeColor(val light: Int, val dark: Int) : Parcelable {
    fun resolved(isDark: Boolean): Int = if (isDark) dark else light

    fun srgb(isDark: Boolean): SrgbColor = SrgbColor.fromArgb(resolved(isDark))

    companion object {
        fun of(hex: String): ThemeColor? {
            val color = SrgbColor.fromHex(hex) ?: return null
            val argb = color.argb
            return ThemeColor(argb, argb)
        }

        fun of(light: String, dark: String): ThemeColor? {
            val lightColor = SrgbColor.fromHex(light) ?: return null
            val darkColor = SrgbColor.fromHex(dark) ?: return null
            return ThemeColor(lightColor.argb, darkColor.argb)
        }

        fun from(value: Any?): ThemeColor? {
            if (value is String) {
                return of(value) ?: run {
                    AppearanceLog.warn("invalid color '$value'")
                    null
                }
            }
            val dict = value as? Map<*, *> ?: return null
            val light = dict["light"] as? String
            val dark = dict["dark"] as? String
            if (light != null && dark != null) {
                return of(light, dark) ?: run {
                    AppearanceLog.warn("invalid light/dark color pair")
                    null
                }
            }
            return null
        }
    }
}

@Parcelize
sealed class Corners : Parcelable {
    @Parcelize
    data object Sharp : Corners()

    @Parcelize
    data object Default : Corners()

    @Parcelize
    data object Rounded : Corners()

    @Parcelize
    data class Custom(val value: Double) : Corners()

    val scale: Double
        get() = when (this) {
            Sharp -> 0.0
            Default -> 16.0
            Rounded -> 24.0
            is Custom -> value.coerceIn(0.0, 24.0)
        }

    companion object {
        fun from(value: Any?): Corners = when (value) {
            is String -> when (value.trim().lowercase()) {
                "sharp" -> Sharp
                "rounded" -> Rounded
                "default" -> Default
                else -> {
                    AppearanceLog.warn("unknown corners '$value', using default")
                    Default
                }
            }
            is Number -> {
                val raw = value.toDouble()
                if (raw < 0 || raw > 24) AppearanceLog.warn("corners $raw is outside 0...24 and was clamped")
                Custom(raw.coerceIn(0.0, 24.0))
            }
            else -> {
                if (value != null) AppearanceLog.warn("corners must be sharp, default, rounded, or a number")
                Default
            }
        }
    }
}

@Parcelize
data class AppearanceOverrides(
    val onBrandColor: ThemeColor? = null,
    val textColor: ThemeColor? = null,
    val linkColor: ThemeColor? = null,
    val borderColor: ThemeColor? = null,
    val componentColor: ThemeColor? = null,
    val selectedColor: ThemeColor? = null,
    val secondaryButtonColor: ThemeColor? = null,
) : Parcelable {
    companion object {
        fun from(value: Any?): AppearanceOverrides {
            val dict = value as? Map<*, *> ?: return AppearanceOverrides()
            return AppearanceOverrides(
                onBrandColor = ThemeColor.from(dict["onBrandColor"]),
                textColor = ThemeColor.from(dict["textColor"]),
                linkColor = ThemeColor.from(dict["linkColor"]),
                borderColor = ThemeColor.from(dict["borderColor"]),
                componentColor = ThemeColor.from(dict["componentColor"]),
                selectedColor = ThemeColor.from(dict["selectedColor"]),
                secondaryButtonColor = ThemeColor.from(dict["secondaryButtonColor"]),
            )
        }
    }
}

@Parcelize
data class Appearance(
    val colorScheme: ColorSchemePreference = ColorSchemePreference.AUTOMATIC,
    val brandColor: ThemeColor? = null,
    val accentColor: ThemeColor? = null,
    val backgroundColor: ThemeColor? = null,
    val neutralTint: ThemeColor? = null,
    val overrides: AppearanceOverrides = AppearanceOverrides(),
    val corners: Corners = Corners.Default,
    val pillButtons: Boolean = true,
    val fontFamily: String? = null,
) : Parcelable {
    companion object {
        fun from(dict: Map<String, Any?>?): Appearance {
            if (dict == null) return Appearance()
            val known = setOf(
                "colorScheme", "brandColor", "accentColor", "backgroundColor", "neutralTint",
                "overrides", "corners", "pillButtons", "fontFamily",
            )
            dict.keys.filter { it !in known }.forEach { AppearanceLog.warn("unknown appearance key '$it'") }
            return Appearance(
                colorScheme = ColorSchemePreference.from(dict["colorScheme"] as? String),
                brandColor = ThemeColor.from(dict["brandColor"]),
                accentColor = ThemeColor.from(dict["accentColor"]),
                backgroundColor = ThemeColor.from(dict["backgroundColor"]),
                neutralTint = ThemeColor.from(dict["neutralTint"]),
                overrides = AppearanceOverrides.from(dict["overrides"]),
                corners = Corners.from(dict["corners"]),
                pillButtons = dict["pillButtons"] as? Boolean ?: true,
                fontFamily = (dict["fontFamily"] as? String)?.takeIf { it.isNotEmpty() },
            )
        }
    }
}

object AppearanceLog {
    private val seen = mutableSetOf<String>()

    fun warn(message: String) {
        if (!seen.add(message)) return
        val debuggable = try {
            val app = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? android.app.Application
            val flags = app?.applicationInfo?.flags ?: 0
            flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        } catch (_: Throwable) {
            false
        }
        if (debuggable) android.util.Log.w("xMoney appearance", message)
    }
}

private data class Oklch(val l: Double, val c: Double, val h: Double) {
    fun toSrgb(): SrgbColor? = Oklab(l, c * kotlin.math.cos(h), c * kotlin.math.sin(h)).toSrgb()

    companion object {
        fun from(color: SrgbColor): Oklch {
            val lab = Oklab.from(color)
            return Oklch(lab.l, kotlin.math.hypot(lab.a, lab.b), kotlin.math.atan2(lab.b, lab.a))
        }
    }
}

private data class Oklab(val l: Double, val a: Double, val b: Double) {
    fun toSrgb(): SrgbColor? {
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b
        val lmsL = l_ * l_ * l_
        val lmsM = m_ * m_ * m_
        val lmsS = s_ * s_ * s_
        val r = 4.0767416621 * lmsL - 3.3077115913 * lmsM + 0.2309699292 * lmsS
        val g = -1.2684380046 * lmsL + 2.6097574011 * lmsM - 0.3413193965 * lmsS
        val bLin = -0.0041960863 * lmsL - 0.7034186147 * lmsM + 1.7076147010 * lmsS
        fun encode(channel: Double): Double? {
            val clipped = channel.coerceIn(0.0, 1.0)
            if (kotlin.math.abs(channel - clipped) > 0.002) return null
            val encoded = if (clipped <= 0.0031308) 12.92 * clipped else 1.055 * Math.pow(clipped, 1 / 2.4) - 0.055
            if (encoded < -0.0001 || encoded > 1.0001) return null
            return encoded.coerceIn(0.0, 1.0)
        }
        return SrgbColor(encode(r) ?: return null, encode(g) ?: return null, encode(bLin) ?: return null)
    }

    companion object {
        fun from(color: SrgbColor): Oklab {
            fun linear(channel: Double) =
                if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
            val r = linear(color.red)
            val g = linear(color.green)
            val b = linear(color.blue)
            val lmsL = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val lmsM = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val lmsS = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            return Oklab(
                0.2104542553 * lmsL + 0.7936177850 * lmsM - 0.0040720468 * lmsS,
                1.9779984951 * lmsL - 2.4285922050 * lmsM + 0.4505937099 * lmsS,
                0.0259040371 * lmsL + 0.7827717662 * lmsM - 0.8086757660 * lmsS,
            )
        }
    }
}
