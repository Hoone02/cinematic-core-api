package org.example.hoon.cinematicCore.model.domain

data class Face(
    val north: UVPoint? = null,
    val east: UVPoint? = null,
    val south: UVPoint? = null,
    val west: UVPoint? = null,
    val up: UVPoint? = null,
    val down: UVPoint? = null
)

