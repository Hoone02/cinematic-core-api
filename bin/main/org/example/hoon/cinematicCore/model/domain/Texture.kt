package org.example.hoon.cinematicCore.model.domain

data class Texture(
    val name: String,
    val id: String,
    val uuid: String,
    val width: Int,
    val height: Int,
    val source: String,  // base64 또는 파일 경로
    val path: String = "",
    val folder: String = "",
    val namespace: String = "",
    val group: String = "",
    val uvWidth: Int = 0,
    val uvHeight: Int = 0,
    val particle: Boolean = false,
    val useAsDefault: Boolean = false,
    val layersEnabled: Boolean = false,
    val syncToProject: String = "",
    val renderMode: String = "default",
    val renderSides: String = "auto",
    val pbrChannel: String = "color",
    val frameTime: Int = 1,
    val frameOrderType: String = "loop",
    val frameOrder: String = "",
    val frameInterpolate: Boolean = false,
    val visible: Boolean = true,
    val internal: Boolean = false,
    val saved: Boolean = false
)

