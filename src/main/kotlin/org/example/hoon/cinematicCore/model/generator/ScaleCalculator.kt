package org.example.hoon.cinematicCore.model.generator

import org.example.hoon.cinematicCore.model.domain.Bone
import org.example.hoon.cinematicCore.model.domain.ModelElement
import org.example.hoon.cinematicCore.model.domain.Vec3

data class ScaleContext(
    val originOffset: Vec3,
    val pivot: Vec3 = Vec3(8.0, 8.0, 8.0),
    val scale: Double = 1.0,
    val isOversized: Boolean = false
)

class ScaleCalculator(
    private val minBound: Double = -16.0,
    private val maxBound: Double = 32.0
) {

    fun evaluate(
        bone: Bone,
        elements: List<ModelElement>,
        @Suppress("UNUSED_PARAMETER") oversizedScale: Double? = null
    ): ScaleContext {
        if (elements.isEmpty()) {
            return ScaleContext(
                originOffset = Vec3(8.0 - bone.origin.x, 8.0 - bone.origin.y, 8.0 - bone.origin.z)
            )
        }

        val originOffset = Vec3(
            8.0 - bone.origin.x,
            8.0 - bone.origin.y,
            8.0 - bone.origin.z
        )

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        var maxZ = -Double.MAX_VALUE

        elements.forEach { element ->
            val offsetFromX = element.from.x + originOffset.x
            val offsetFromY = element.from.y + originOffset.y
            val offsetFromZ = element.from.z + originOffset.z
            val offsetToX = element.to.x + originOffset.x
            val offsetToY = element.to.y + originOffset.y
            val offsetToZ = element.to.z + originOffset.z

            minX = minOf(minX, offsetFromX)
            minY = minOf(minY, offsetFromY)
            minZ = minOf(minZ, offsetFromZ)
            maxX = maxOf(maxX, offsetToX)
            maxY = maxOf(maxY, offsetToY)
            maxZ = maxOf(maxZ, offsetToZ)
        }

        val pivot = Vec3(8.0, 8.0, 8.0)

        val scaleCandidates = mutableListOf(1.0)
        calculateAxisScale(minX, maxX, pivot.x)?.let { scaleCandidates += it }
        calculateAxisScale(minY, maxY, pivot.y)?.let { scaleCandidates += it }
        calculateAxisScale(minZ, maxZ, pivot.z)?.let { scaleCandidates += it }

        val computedScale = scaleCandidates.minOrNull() ?: 1.0
        val appliedScale = computedScale.coerceIn(0.01, 1.0)
        val oversized = appliedScale < 0.9999

        return ScaleContext(
            originOffset = originOffset,
            pivot = pivot,
            scale = appliedScale,
            isOversized = oversized
        )
    }

    private fun calculateAxisScale(min: Double, max: Double, pivot: Double): Double? {
        var scale = 1.0
        var adjusted = false

        val positiveExtent = max - pivot
        if (positiveExtent > 0 && max > maxBound) {
            val limit = maxBound - pivot
            if (limit > 0) {
                val ratio = limit / positiveExtent
                if (ratio.isFinite() && ratio > 0) {
                    scale = minOf(scale, ratio)
                    adjusted = true
                }
            }
        }

        val negativeExtent = pivot - min
        if (negativeExtent > 0 && min < minBound) {
            val limit = pivot - minBound
            if (limit > 0) {
                val ratio = limit / negativeExtent
                if (ratio.isFinite() && ratio > 0) {
                    scale = minOf(scale, ratio)
                    adjusted = true
                }
            }
        }

        return if (adjusted) scale else null
    }
}

