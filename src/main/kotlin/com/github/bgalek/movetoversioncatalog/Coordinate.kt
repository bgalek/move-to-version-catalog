package com.github.bgalek.movetoversioncatalog

data class Coordinate(
    val group: String,
    val name: String,
    val version: String? = null,
) {
    companion object {
        fun parse(text: String): Coordinate? {
            val parts = text.split(':')
            return when (parts.size) {
                2 -> Coordinate(parts[0], parts[1])
                3 -> Coordinate(parts[0], parts[1], parts[2])
                else -> null
            }?.takeIf { it.group.isNotBlank() && it.name.isNotBlank() }
        }
    }
}
