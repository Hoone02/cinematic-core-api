import org.bukkit.entity.Display
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.ItemDisplay
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.max

object DisplayPivotUtil {
    sealed interface Pivot {
        data class Local(val x: Float, val y: Float, val z: Float) : Pivot
        data class WorldOffset(val x: Float, val y: Float, val z: Float) : Pivot
    }

    data class EulerDeg(val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f)
    @JvmInline value class Quat(val q: Quaternionf)

    private val ZERO = Vector3f(0f, 0f, 0f)
    private val ONE  = Vector3f(1f, 1f, 1f)

    fun setTransform(
        display: Display,
        pivot: Pivot,
        euler: EulerDeg? = EulerDeg(),
        quat: Quat? = null,
        scale: Vector3f? = null,
        offset: Vector3f = ZERO
    ) {
        val curScale: Vector3f = display.transformation?.scale ?: ONE

        val sx = if (scale != null) curScale.x * scale.x else curScale.x
        val sy = if (scale != null) curScale.y * scale.y else curScale.y
        val sz = if (scale != null) curScale.z * scale.z else curScale.z

        val (px, py, pz) = when (pivot) {
            is Pivot.Local -> {
                val invX = 1f / max(sx, 1e-6f)
                val invY = 1f / max(sy, 1e-6f)
                val invZ = 1f / max(sz, 1e-6f)
                Triple(pivot.x * invX, pivot.y * invY, pivot.z * invZ)
            }
            is Pivot.WorldOffset -> Triple(pivot.x, pivot.y, pivot.z)
        }

        // 회전 행렬 생성 (offset을 회전 행렬의 역변환으로 적용하기 위해)
        val rotationMatrix = Matrix4f()
        when {
            quat != null -> rotationMatrix.rotate(quat.q)
            euler != null -> {
                val (ry, rp, rr) = euler.toRad()
                rotationMatrix.rotateZ(rr).rotateY(ry).rotateX(rp)
            }
        }
        
        // offset을 회전 행렬의 역변환으로 변환 (회전된 좌표계에서 원래 좌표계로)
        // 회전 후에 offset을 적용하면 회전된 좌표계에서의 translation이 적용되므로,
        // offset을 회전 행렬의 역변환으로 변환해서 적용해야 함
        val rotatedOffset = Vector3f(offset.x, offset.y, offset.z)
        val invRotationMatrix = Matrix4f(rotationMatrix).invert()
        invRotationMatrix.transformPosition(rotatedOffset)
        
        val m = Matrix4f()
            .translate(px, py, pz)
            .mul(rotationMatrix)
            .scale(sx, sy, sz)
            .translate(-px, -py, -pz)
            .translate(rotatedOffset.x, rotatedOffset.y, rotatedOffset.z)

        display.setTransformationMatrix(m)
    }

    fun setTransform(
        display: Display,
        pivot: Pivot,
        euler: EulerDeg? = EulerDeg(),
        quat: Quat? = null,
        scale: Triple<Float, Float, Float>? = null,
        offset: Triple<Float, Float, Float> = Triple(0f, 0f, 0f)
    ) = setTransform(
        display, pivot, euler, quat,
        scale?.let { Vector3f(it.first, it.second, it.third) },
        Vector3f(offset.first, offset.second, offset.third)
    )

    fun setTransformLocal(
        display: Display,
        px: Float, py: Float, pz: Float,
        euler: EulerDeg? = EulerDeg(),
        quat: Quat? = null,
        scale: Vector3f? = null,
        offset: Vector3f = ZERO
    ) = setTransform(display, Pivot.Local(px, py, pz), euler, quat, scale, offset)

    fun setTransformAbsolute(
        display: Display,
        ax: Float, ay: Float, az: Float,
        euler: EulerDeg? = EulerDeg(),
        quat: Quat? = null,
        scale: Vector3f? = null,
        offset: Vector3f = ZERO
    ) = setTransform(display, Pivot.WorldOffset(ax, ay, az), euler, quat, scale, offset)


    fun setTransformLocal(
        display: Display,
        px: Float, py: Float, pz: Float,
        euler: EulerDeg? = EulerDeg(),
        quat: Quat? = null,
        scale: Triple<Float, Float, Float>? = null,
        offset: Triple<Float, Float, Float> = Triple(0f, 0f, 0f)
    ) = setTransformLocal(
        display, px, py, pz, euler, quat,
        scale?.let { Vector3f(it.first, it.second, it.third) },
        Vector3f(offset.first, offset.second, offset.third)
    )

    fun setTransformAbsolute(
        display: Display,
        ax: Float, ay: Float, az: Float,
        euler: EulerDeg? = EulerDeg(),
        quat: Quat? = null,
        scale: Triple<Float, Float, Float>? = null,
        offset: Triple<Float, Float, Float> = Triple(0f, 0f, 0f)
    ) = setTransformAbsolute(
        display, ax, ay, az, euler, quat,
        scale?.let { Vector3f(it.first, it.second, it.third) },
        Vector3f(offset.first, offset.second, offset.third)
    )

    fun BlockDisplay.setTransformLocal(
        px: Float, py: Float, pz: Float,
        euler: EulerDeg? = EulerDeg(), quat: Quat? = null,
        scale: Vector3f? = null, offset: Vector3f = ZERO
    ) = setTransformLocal(this as Display, px, py, pz, euler, quat, scale, offset)

    fun ItemDisplay.setTransformLocal(
        px: Float, py: Float, pz: Float,
        euler: EulerDeg? = EulerDeg(), quat: Quat? = null,
        scale: Vector3f? = null, offset: Vector3f = ZERO
    ) = setTransformLocal(this as Display, px, py, pz, euler, quat, scale, offset)

    fun BlockDisplay.setTransformAbsolute(
        ax: Float, ay: Float, az: Float,
        euler: EulerDeg? = EulerDeg(), quat: Quat? = null,
        scale: Vector3f? = null, offset: Vector3f = ZERO
    ) = setTransformAbsolute(this as Display, ax, ay, az, euler, quat, scale, offset)

    fun ItemDisplay.setTransformAbsolute(
        ax: Float, ay: Float, az: Float,
        euler: EulerDeg? = EulerDeg(), quat: Quat? = null,
        scale: Vector3f? = null, offset: Vector3f = ZERO
    ) = setTransformAbsolute(this as Display, ax, ay, az, euler, quat, scale, offset)

    private data class RotRad(val yaw: Float, val pitch: Float, val roll: Float)
    private fun EulerDeg.toRad(): RotRad = RotRad(
        yaw = Math.toRadians(yaw.toDouble()).toFloat(),
        pitch = Math.toRadians(pitch.toDouble()).toFloat(),
        roll = Math.toRadians(roll.toDouble()).toFloat()
    )
}