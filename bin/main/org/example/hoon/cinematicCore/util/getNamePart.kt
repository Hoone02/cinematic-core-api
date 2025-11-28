package org.example.hoon.cinematicCore.util

fun String.getNamePart(index: Int): String? {
    return this.split("(").getOrNull(index)?.removeSuffix(".json")
}