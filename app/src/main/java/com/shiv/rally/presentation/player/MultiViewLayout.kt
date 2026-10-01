package com.shiv.rally.presentation.player

import com.shiv.rally.domain.model.MultiViewLayoutMode
import kotlin.math.min

internal data class MultiViewBounds(val x: Float, val y: Float, val width: Float, val height: Float)

/** Every video cell is 16:9. Unused space stays outside the composition, never
 * inside a stream; immersive cells share edges without cropping the picture. */
internal fun multiViewBounds(width: Float, height: Float, count: Int, mode: MultiViewLayoutMode, gap: Float): List<MultiViewBounds> {
    if (count == 0 || width <= 0 || height <= 0) return emptyList()
    val n = count.coerceIn(1, 4)
    val aspect = 16f / 9f
    val g = gap.coerceAtLeast(0f)
    val cells = when {
        n == 1 -> listOf(MultiViewBounds(0f, 0f, width, width / aspect))
        n == 2 -> {
            val main = if (mode == MultiViewLayoutMode.DUAL_FOCUS) (width - g) * .7f else (width - g) / 2
            val side = width - g - main
            val mainHeight = main / aspect
            listOf(MultiViewBounds(0f, 0f, main, mainHeight), MultiViewBounds(main + g, (mainHeight - side / aspect) / 2, side, side / aspect))
        }
        n == 3 -> {
            val side = (width - g - g * aspect) / 3
            val main = width - g - side
            listOf(MultiViewBounds(0f, 0f, main, main / aspect),
                MultiViewBounds(main + g, 0f, side, side / aspect),
                MultiViewBounds(main + g, side / aspect + g, side, side / aspect))
        }
        mode == MultiViewLayoutMode.QUAD_FOCUS -> {
            val side = (width - g - 2 * g * aspect) / 4
            val main = width - g - side
            listOf(MultiViewBounds(0f, 0f, main, main / aspect)) + (0..2).map { row ->
                MultiViewBounds(main + g, row * (side / aspect + g), side, side / aspect)
            }
        }
        else -> {
            val cellWidth = (width - g) / 2
            val cellHeight = cellWidth / aspect
            (0..3).map { i -> MultiViewBounds((i % 2) * (cellWidth + g), (i / 2) * (cellHeight + g), cellWidth, cellHeight) }
        }
    }
    val contentHeight = cells.maxOf { it.y + it.height }
    val scale = min(1f, height / contentHeight)
    val left = (width - width * scale) / 2
    val top = (height - contentHeight * scale) / 2
    return cells.map { MultiViewBounds(left + it.x * scale, top + it.y * scale, it.width * scale, it.height * scale) }
}

internal fun multiViewNeighbor(bounds: List<MultiViewBounds>, index: Int, horizontal: Boolean, forward: Boolean): Int? {
    val origin = bounds.getOrNull(index) ?: return null
    val ox = origin.x + origin.width / 2
    val oy = origin.y + origin.height / 2
    return bounds.indices.filter { i ->
        if (i == index) false else {
            val target = bounds[i]
            val delta = if (horizontal) target.x + target.width / 2 - ox else target.y + target.height / 2 - oy
            if (forward) delta > 1 else delta < -1
        }
    }.minByOrNull { i ->
        val target = bounds[i]
        val dx = kotlin.math.abs(target.x + target.width / 2 - ox)
        val dy = kotlin.math.abs(target.y + target.height / 2 - oy)
        if (horizontal) dx + dy * 2 else dy + dx * 2
    }
}
