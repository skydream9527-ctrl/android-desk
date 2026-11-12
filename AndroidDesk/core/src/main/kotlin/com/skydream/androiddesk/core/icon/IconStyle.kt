package com.skydream.androiddesk.core.icon

import kotlinx.serialization.Serializable

/** Outline every icon is clipped to. */
@Serializable
enum class IconShape(val label: String) {
    Circle("圆形"),
    Rounded("圆角方"),
    Squircle("超椭圆"),
    Teardrop("水滴"),
    Hexagon("六边形"),
    Flower("花瓣"),
}

/** How the app's own artwork is treated inside the shape. */
@Serializable
enum class IconTreatment(val label: String, val hint: String) {
    Original("原生", "应用自己的图标，只换外形"),
    Glass("玻璃", "半透明底板，透出壁纸"),
    Tinted("单色", "统一成一种主题色"),
    Gradient("渐变", "按图标主色生成渐变底板"),
    Outline("描边", "只留一圈细边"),
    Letter("字母", "用应用名的首字做图标"),
}

@Serializable
data class IconStyle(
    val shape: IconShape = IconShape.Squircle,
    val treatment: IconTreatment = IconTreatment.Original,
    /** Icon size relative to the default, in percent. */
    val sizePercent: Int = 100,
    val showLabel: Boolean = true,
    /** ARGB accent used by the Tinted treatment. */
    val tintArgb: Long = IconTints.default,
) {
    init {
        require(sizePercent in MIN_SIZE..MAX_SIZE) { "sizePercent out of range" }
    }

    companion object {
        const val MIN_SIZE = 70
        const val MAX_SIZE = 130
    }
}

/** A per-app exception. A null field means "follow the global style". */
@Serializable
data class IconStyleOverride(
    val shape: IconShape? = null,
    val treatment: IconTreatment? = null,
) {
    val isEmpty: Boolean get() = shape == null && treatment == null
}

@Serializable
data class IconTheme(
    val global: IconStyle = IconStyle(),
    /** Keyed by app key (package or package/activity). */
    val overrides: Map<String, IconStyleOverride> = emptyMap(),
) {
    fun styleFor(appKey: String): IconStyle {
        val o = overrides[appKey] ?: return global
        return global.copy(shape = o.shape ?: global.shape, treatment = o.treatment ?: global.treatment)
    }

    fun overrideFor(appKey: String): IconStyleOverride = overrides[appKey] ?: IconStyleOverride()

    fun withGlobal(style: IconStyle): IconTheme = copy(global = style)

    /** Sets or clears one app's exception; an empty override is removed rather than stored. */
    fun withOverride(appKey: String, override: IconStyleOverride?): IconTheme =
        if (override == null || override.isEmpty) copy(overrides = overrides - appKey)
        else copy(overrides = overrides + (appKey to override))

    /** Forgets exceptions for apps that are no longer installed. */
    fun pruned(validKeys: Set<String>): IconTheme {
        val kept = overrides.filterKeys { it in validKeys }
        return if (kept.size == overrides.size) this else copy(overrides = kept)
    }
}

object IconTints {
    const val default: Long = 0xFF5B8DEF

    /** Accent choices offered for the Tinted treatment. */
    val presets: List<Long> = listOf(
        0xFF5B8DEF, // blue
        0xFF34C38F, // green
        0xFFF2A33A, // amber
        0xFFEF6B6B, // coral
        0xFFA78BFA, // violet
        0xFFE5E7EB, // near white
    )
}
