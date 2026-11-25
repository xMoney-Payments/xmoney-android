package com.xmoney.paymentelement.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xmoney.payments.config.Appearance
import com.xmoney.payments.config.AppearanceLog
import com.xmoney.payments.config.SrgbColor
internal object ThemeConstants {
    /** Dark extended theme: neutral tint mixed over the raw background. */
    const val sheetOverBackdropDark = 0.24
    const val cardOverBackdropDark = 0.34
    const val selectedOverBackdropDark = 0.46
    const val cardOverSheetDark = 0.24
    const val cardOverSheetLight = 0.75
    const val selectedOverSheetDark = 0.36
    const val selectedOverCardLight = 0.12
    const val borderAlphaDark = 0.30
    const val borderAlphaLight = 0.25
    const val secondaryOverSheetLight = 0.15
    const val secondaryOverCardDark = 0.16
    const val pressedTowardLabel = 0.12
    const val disabledFillAlpha = 0.40
    const val brandShadow = 0.30
    const val achromaticShadow = 0.14
    /** Brand mixed over white for the light sheet and selected row. */
    const val lightSurfaceBrandTint = 0.06
    /** Brand mixed over white for the light plus and details chips. */
    const val lightIconChipBrand = 0.15
    /** Dark links and icons move this far toward white when no dark brand is set. */
    const val darkLinkTowardWhite = 0.17
}

internal object ThemeDerivation {
    fun resolve(
        appearance: Appearance,
        isDark: Boolean,
        contentSizeScale: Float = 1f,
        increasedContrast: Boolean = false,
    ): CheckoutTheme {
        // Order: surfaces, derived roles, exact overrides, then shape (typography, borders, shape).
        val brand = appearance.brandColor?.srgb(isDark) ?: Defaults.brand
        val accent = appearance.accentColor?.srgb(isDark) ?: brand
        val customBrand = appearance.brandColor != null
        val backdrop = appearance.backgroundColor?.srgb(isDark)
        val tint = appearance.neutralTint?.srgb(isDark)
        val lifted = isDark && backdrop != null && tint != null
        val sheet = if (lifted) {
            tint!!.mixed(backdrop!!, ThemeConstants.sheetOverBackdropDark)
        } else backdrop ?: if (isDark) {
            Defaults.darkSheet
        } else if (customBrand) {
            brand.mixed(SrgbColor.WHITE, ThemeConstants.lightSurfaceBrandTint)
        } else {
            Defaults.lightSheet
        }
        val customSurface = appearance.backgroundColor != null || tint != null
        val derivedCard = if (customSurface) {
            if (isDark) {
                if (lifted) tint!!.mixed(backdrop!!, ThemeConstants.cardOverBackdropDark)
                else (tint ?: Defaults.darkGray).mixed(sheet, ThemeConstants.cardOverSheetDark)
            } else SrgbColor.WHITE.mixed(sheet, ThemeConstants.cardOverSheetLight)
        } else if (isDark) Defaults.darkCard else Defaults.lightCard
        val card = exact(appearance.overrides.componentColor?.srgb(isDark), derivedCard)
        val cardIsDark = card.relativeLuminance <= 0.5
        val sheetIsDark = sheet.relativeLuminance < 0.5
        val cardNeedsOwnInk = appearance.overrides.textColor == null &&
            (appearance.overrides.componentColor != null || cardIsDark != sheetIsDark)
        val toneDark = if (cardNeedsOwnInk) cardIsDark else isDark
        fun gray(forDark: Boolean) = tint ?: if (forDark) Defaults.darkGray else Defaults.lightGray
        val derivedSelected = if (customSurface) {
            if (toneDark) {
                if (lifted) tint!!.mixed(backdrop!!, ThemeConstants.selectedOverBackdropDark)
                else gray(true).mixed(sheet, ThemeConstants.selectedOverSheetDark)
            } else gray(false).mixed(card, ThemeConstants.selectedOverCardLight)
        } else if (toneDark) Defaults.darkSelected else if (customBrand) sheet else Defaults.lightSelected
        val selected = exact(appearance.overrides.selectedColor?.srgb(isDark), derivedSelected)
        val sentText = appearance.overrides.textColor?.srgb(isDark)
        val derivedSheet = if (customSurface) bestInk(sheet) else if (isDark) Defaults.darkText else Defaults.lightText
        val readableSheet = if (derivedSheet.contrast(sheet) >= 4.5) derivedSheet else bestInk(sheet)
        val sheetText = exact(sentText, readableSheet)
        val primaryText = if (cardNeedsOwnInk) bestInk(card) else sheetText
        warn("textColor", sentText, listOf(sheet, card), 4.5)
        val cardGray = gray(toneDark)
        val sheetGray = gray(sheetIsDark)
        fun target(value: Double) = if (increasedContrast) 7.0 else value
        fun increasedDefault(color: SrgbColor, surface: SrgbColor): SrgbColor =
            if (increasedContrast) color.withContrast(4.6, surface) else color
        val secondary = if (customSurface) cardGray.withContrast(target(if (toneDark) 6.41 else 7.0), selected)
            else increasedDefault(if (toneDark) Defaults.darkSecondary else Defaults.lightSecondary, selected)
        val tertiary = if (customSurface) cardGray.withContrast(target(5.5), card)
            else if (toneDark) Defaults.darkTertiary else Defaults.lightTertiary
        val muted = if (customSurface) cardGray.withContrast(target(4.5), card)
            else if (toneDark) Defaults.darkMuted else Defaults.lightMuted
        val placeholder = if (customSurface) cardGray.withContrast(target(3.0), card)
            else increasedDefault(if (toneDark) Defaults.darkPlaceholder else Defaults.lightPlaceholder, card)
        val powered = if (customSurface) sheetGray.withContrast(target(3.0), sheet)
            else increasedDefault(if (sheetIsDark) Defaults.darkPowered else Defaults.lightPowered, sheet)
        val orLabel = if (customSurface) sheetGray.withContrast(target(4.5), sheet)
            else increasedDefault(if (sheetIsDark) Defaults.darkTertiary else Defaults.lightTertiary, sheet)
        val onBrand = exact(appearance.overrides.onBrandColor?.srgb(isDark), bestLabel(brand))
        warn("onBrandColor", appearance.overrides.onBrandColor?.srgb(isDark), listOf(brand), 4.5)
        val linkSource = appearance.accentColor ?: appearance.brandColor
        val hasDistinctDark = linkSource != null && linkSource.light != linkSource.dark
        val paintedAccent = if (isDark && (customBrand || appearance.accentColor != null) && !hasDistinctDark) {
            SrgbColor.WHITE.mixed(accent, ThemeConstants.darkLinkTowardWhite)
        } else {
            accent
        }
        val derivedLink = if (customBrand || appearance.accentColor != null) paintedAccent
            else if (if (cardNeedsOwnInk) cardIsDark else isDark) Defaults.darkLink else Defaults.lightLink
        val linkBase = exact(appearance.overrides.linkColor?.srgb(isDark), derivedLink)
        val link = if (appearance.overrides.linkColor == null &&
            appearance.accentColor == null && appearance.brandColor == null && cardNeedsOwnInk
        ) {
            linkBase.withContrast(4.5, card)
        } else linkBase
        warn("linkColor", appearance.overrides.linkColor?.srgb(isDark), listOf(sheet, card), 4.5)
        val derivedBorder = if (customSurface) {
            (tint ?: cardGray).withAlpha(if (toneDark) ThemeConstants.borderAlphaDark else ThemeConstants.borderAlphaLight)
        } else if (toneDark) Defaults.darkBorder else Defaults.lightBorder
        val border = exact(appearance.overrides.borderColor?.srgb(isDark), derivedBorder)
        appearance.overrides.borderColor?.srgb(isDark)?.let { sent ->
            if (sent.alpha > 0.01 &&
                sent.contrast(sheet, sheet) < 3 &&
                sent.contrast(card, card) < 3
            ) {
                AppearanceLog.warn("borderColor is below 3:1 where it is used as a state indicator")
            }
        }
        val errorBase = if (toneDark) Defaults.darkError else Defaults.lightError
        val error = if (customSurface || appearance.overrides.componentColor != null) {
            errorBase.withContrast(4.5, card)
        } else errorBase
        val focus = meeting(accent, 3.0, listOf(sheet, card))
        var idleRing = uncheckedRing(accent, sheetIsDark)
        if (idleRing.contrast(card, card) < 3) idleRing = uncheckedRing(accent, cardIsDark)
        if (idleRing.contrast(card, card) < 3) AppearanceLog.warn("idle ring is below 3:1 on the card")
        val ink = if (sheetIsDark) SrgbColor.PAPER else SrgbColor.INK
        val pressed = brand.mixed(onBrand, 1 - ThemeConstants.pressedTowardLabel)
        val disabledFill = brand.withAlpha(ThemeConstants.disabledFillAlpha).composited(sheet)
        val disabledText = onBrand.withContrast(4.5, disabledFill)
        val derivedSecondary = if (toneDark) brand.withAlpha(ThemeConstants.secondaryOverCardDark).composited(card)
            else brand.mixed(sheet, ThemeConstants.secondaryOverSheetLight)
        val secondaryFill = exact(appearance.overrides.secondaryButtonColor?.srgb(isDark), derivedSecondary)
        val secondaryLabel = link.withContrast(4.5, secondaryFill)
        val subtle = if (customBrand) {
            if (toneDark) brand.mixed(SrgbColor.BLACK, 0.28) else brand.mixed(SrgbColor.WHITE, ThemeConstants.lightIconChipBrand)
        } else if (toneDark) Defaults.darkSubtle else Defaults.lightSubtle
        val shadowAlpha = if (increasedContrast) 0.0 else if (brand.isAchromatic) ThemeConstants.achromaticShadow else ThemeConstants.brandShadow
        val radii = radii(appearance)
        val scale = contentSizeScale.coerceAtLeast(1f)
        fun ui(color: SrgbColor) = Color(color.argb)
        val builtInBrand = appearance.brandColor == null && appearance.accentColor == null
        val builtInCard = appearance.overrides.componentColor == null && !customSurface
        val iconBase = if (builtInBrand) {
            if (toneDark) Defaults.darkLink else Defaults.brand
        } else paintedAccent
        val icon = if (builtInBrand && !cardNeedsOwnInk) iconBase else if (builtInBrand) iconBase.withContrast(4.5, card) else paintedAccent
        val inverse = if (appearance.overrides.textColor != null || customSurface || cardNeedsOwnInk) {
            bestInk(primaryText)
        } else if (isDark) Defaults.darkInverse else Defaults.lightSheet
        val containerBorder = if (appearance.overrides.borderColor == null) Color.Transparent else ui(border)
        return CheckoutTheme(
            primary = ui(brand),
            background = ui(sheet),
            componentBackground = ui(card),
            componentBorder = ui(border),
            componentDivider = ui(border),
            primaryText = ui(primaryText),
            secondaryText = ui(secondary),
            componentText = ui(secondary),
            placeholderText = ui(placeholder),
            icon = ui(placeholder),
            error = ui(error),
            borderRadius = radii.field,
            borderWidth = appearance.borders.width.coerceIn(0.0, 4.0).dp,
            fontScale = scale,
            fontFamily = CheckoutTheme.resolveFontFamily(appearance.typography.fontFamily),
            primaryButtonFontFamily = CheckoutTheme.resolveFontFamily(appearance.typography.fontFamily),
            primaryButtonBackground = ui(brand),
            primaryButtonText = ui(onBrand),
            primaryButtonBorder = ui(brand),
            primaryButtonBorderRadius = radii.button,
            primaryButtonBorderWidth = 0.dp,
            selectedBackground = ui(selected),
            accentIconBackground = ui(subtle),
            containerBorder = containerBorder,
            neutralChip = ui((tint ?: cardGray).withAlpha(if (toneDark) 0.40 else 0.20).composited(sheet)),
            fieldBorder = ui(border),
            fieldDivider = ui(border),
            mutedIcon = ui(placeholder),
            unselectedRing = ui(idleRing),
            errorBorder = ui(error),
            errorText = ui(error),
            footerBorder = ui((if (sheetIsDark) SrgbColor.WHITE else SrgbColor.INK).withAlpha(if (sheetIsDark) 0.08 else 0.06)),
            sheetHandle = ui(border),
            orDivider = ui(border),
            visaMarkTint = if (toneDark && !builtInCard) Color.White else null,
            brandTileBackground = if (builtInCard) Color.White else ui(card),
            paymentContainerRadius = radii.container,
            formFieldRadius = radii.field,
            formFieldHeight = 48.dp * scale,
            isDark = isDark,
            inputPlaceholder = ui(placeholder),
            metaText = ui(tertiary),
            sectionLabel = ui(muted),
            radioIdle = ui(idleRing),
            textBrand = ui(link),
            iconBrand = ui(icon),
            errorBadge = ui(Defaults.errorBadge),
            dangerTint = Color(0xFFEF4444).copy(alpha = if (isDark) 0.15f else 0.08f),
            dangerTintStrong = Color(0xFFEF4444).copy(alpha = if (isDark) 0.30f else 0.09f),
            inverseText = ui(inverse),
            poweredByText = ui(powered),
            poweredByLogo = ui(powered),
            primaryButtonPressed = ui(pressed),
            primaryButtonDisabledBackground = ui(disabledFill),
            primaryButtonDisabledText = ui(disabledText),
            secondaryButtonBackground = ui(secondaryFill),
            secondaryButtonText = ui(secondaryLabel),
            focusRing = ui(focus),
            stateIndicatorBorder = ui(ink),
            checkboxRadius = radii.checkbox,
            brandShadow = ui(brand).copy(alpha = shadowAlpha.toFloat()),
            sheetCornerRadius = radii.sheet,
            rowRadius = radii.row,
            brandTileRadius = radii.tile,
            sheetText = ui(sheetText),
            isDarkSurface = sheetIsDark,
            orLabel = ui(orLabel),
        )
    }

    /** Smallest change that clears [minimum] on every surface. */
    private fun meeting(color: SrgbColor, minimum: Double, surfaces: List<SrgbColor>): SrgbColor {
        var current = color
        repeat(3) {
            val worst = surfaces.minByOrNull { current.contrast(it) } ?: return current
            if (current.contrast(worst) >= minimum) return current
            val next = current.withContrast(minimum, worst)
            if (next.red == current.red && next.green == current.green && next.blue == current.blue) return current
            current = next
        }
        return current
    }

    /** Idle checkbox and radio stroke. Alpha stays so the ring composites on the card, not the sheet. */
    private fun uncheckedRing(accent: SrgbColor, isDark: Boolean): SrgbColor =
        if (isDark) accent.mixed(SrgbColor.WHITE, 0.72).withAlpha(0.63) else accent.withAlpha(0.80)

    /** A set override is painted as sent. The derived color is used only when the override is nil. */
    private fun exact(sent: SrgbColor?, fallback: SrgbColor): SrgbColor = sent ?: fallback

    private fun warn(name: String, color: SrgbColor?, surfaces: List<SrgbColor>, minimum: Double) {
        color ?: return
        if (color.alpha < 0.99) AppearanceLog.warn("$name is transparent")
        if (surfaces.any { color.contrast(it) < minimum }) {
            AppearanceLog.warn("$name is below $minimum:1 on a surface where it appears")
        }
    }

    private fun bestInk(surface: SrgbColor) =
        if (SrgbColor.INK.contrast(surface) >= SrgbColor.PAPER.contrast(surface)) SrgbColor.INK else SrgbColor.PAPER

    private fun bestLabel(fill: SrgbColor) =
        if (SrgbColor.WHITE.contrast(fill) >= SrgbColor.INK.contrast(fill)) SrgbColor.WHITE else SrgbColor.INK

    private data class Radii(
        val button: Dp,
        val field: Dp,
        val row: Dp,
        val container: Dp,
        val sheet: Dp,
        val checkbox: Dp,
        val tile: Dp,
    )

    private fun radii(appearance: Appearance): Radii {
        val scale = appearance.shape.corners.scale
        val control = interpolate(0.0, 12.0, 24.0, scale)
        val field = interpolate(0.0, 16.0, 24.0, scale)
        val container = interpolate(0.0, 24.0, 32.0, scale)
        val sheet = 32.0
        val checkbox = interpolate(0.0, 8.0, 12.0, scale)
        val row = if (container >= 16) maxOf(8.0, minOf(field, container - 8)) else minOf(control, container)
        val button = if (appearance.shape.pillButtons) CheckoutTheme.DefaultPrimaryButtonRadius.toDouble() else control
        // The chip is 48×34. A radius past half its height (17) draws a stadium, so stop at the 12dp chip.
        val tile = minOf(control, 12.0)
        return Radii(button.dp, field.dp, row.dp, container.dp, sheet.dp, checkbox.dp, tile.dp)
    }

    private fun interpolate(sharp: Double, regular: Double, rounded: Double, scale: Double): Double {
        val value = if (scale <= 16) sharp + (regular - sharp) * (scale / 16) else regular + (rounded - regular) * ((scale - 16) / 8)
        return kotlin.math.round(value)
    }
}

private object Defaults {
    val brand = SrgbColor.fromHex("#7C4DFF")!!
    val lightSheet = SrgbColor.fromHex("#F7F6FF")!!
    val darkSheet = SrgbColor.fromHex("#18181B")!!
    val darkInverse = SrgbColor.fromHex("#09090B")!!
    val lightCard = SrgbColor.fromHex("#FFFFFF")!!
    val darkCard = SrgbColor.fromHex("#1F1F23")!!
    val lightText = SrgbColor.fromHex("#16141A")!!
    val darkText = SrgbColor.fromHex("#FAFAFA")!!
    val lightLink = SrgbColor.fromHex("#7A4BFB")!!
    val darkLink = SrgbColor.fromHex("#926BFF")!!
    val lightSubtle = SrgbColor.fromHex("#ECE5FF")!!
    val darkSubtle = SrgbColor.fromHex("#24124C")!!
    val lightSelected = SrgbColor.fromHex("#F7F6FF")!!
    val darkSelected = SrgbColor.fromHex("#2A2A2F")!!
    val lightSecondary = SrgbColor.fromHex("#4A4653")!!
    val darkSecondary = SrgbColor.fromHex("#D1CDDB")!!
    val lightTertiary = SrgbColor.fromHex("#63606D")!!
    val darkTertiary = SrgbColor.fromHex("#95959D")!!
    val lightMuted = SrgbColor.fromHex("#6B6779")!!
    val darkMuted = SrgbColor.fromHex("#85858D")!!
    val lightPlaceholder = SrgbColor.fromHex("#9691A3")!!
    val darkPlaceholder = SrgbColor.fromHex("#69696F")!!
    val lightPowered = SrgbColor.fromHex("#9691A3")!!
    val darkPowered = SrgbColor.fromHex("#605C70")!!
    val lightBorder = SrgbColor.fromHex("#E3E1E8")!!
    val darkBorder = SrgbColor.WHITE.withAlpha(0x1A / 255.0)
    val lightGray = SrgbColor.fromHex("#9691A3")!!
    val darkGray = SrgbColor.fromHex("#8A868F")!!
    val lightError = SrgbColor.fromHex("#DC2626")!!
    val darkError = SrgbColor.fromHex("#EF4444")!!
    val errorBadge = SrgbColor.fromHex("#DC2626")!!
}
