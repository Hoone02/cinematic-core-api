package org.example.hoon.cinematicCore.util.display

import org.bukkit.entity.Display
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

fun Display.updateTransformation(block: KTransformation.() -> Unit) {
    transformation = KTransformation(transformation).apply(block).toTransformation()
}

class KTransformation(transformation: Transformation) {

    private var translation: Vector3f = transformation.translation
    private var leftRotation: Quaternionf = transformation.leftRotation
    private var scale: Vector3f = transformation.scale
    private var rightRotation: Quaternionf = transformation.rightRotation

    fun translation(block: KVector3f.() -> Unit) {
        translation = KVector3f().apply(block).toVector3f()
    }

    fun leftRotation(block: KQuaternionf.() -> Unit) {
        leftRotation = KQuaternionf().apply(block).toQuaternionf()
    }

    fun scale(block: KVector3f.() -> Unit) {
        scale = KVector3f().apply(block).toVector3f()
    }

    fun rightRotation(block: KQuaternionf.() -> Unit) {
        rightRotation = KQuaternionf().apply(block).toQuaternionf()
    }

    fun toTransformation(): Transformation {
        return Transformation(translation, leftRotation, scale, rightRotation)
    }
}

class KVector3f {
    var x: Float = 0.0f
    var y: Float = 0.0f
    var z: Float = 0.0f

    fun toVector3f(): Vector3f {
        return Vector3f(x, y, z)
    }
}

class KQuaternionf {
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0
    var w: Double = 0.0

    fun toQuaternionf(): Quaternionf {
        return Quaternionf(x, y, z, w)
    }
}

