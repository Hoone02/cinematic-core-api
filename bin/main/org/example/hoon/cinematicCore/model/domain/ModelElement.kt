package org.example.hoon.cinematicCore.model.domain

data class ModelElement(
    val name: String,
    val uuid: String,
    val from: Vec3,
    val to: Vec3,
    val rotation: Vec3,
    val origin: Vec3,
    val faces: Face,
    val inflate: Double = 0.0
)

