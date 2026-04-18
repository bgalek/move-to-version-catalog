package com.github.bgalek.movetoversioncatalog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CoordinateTest {

    @Test
    fun `parses group and name without version`() {
        val coordinate = Coordinate.parse("com.example:library")
        assertEquals(Coordinate("com.example", "library"), coordinate)
    }

    @Test
    fun `parses group name and version`() {
        val coordinate = Coordinate.parse("com.example:library:1.2.3")
        assertEquals(Coordinate("com.example", "library", "1.2.3"), coordinate)
    }

    @Test
    fun `returns null for single segment`() {
        assertNull(Coordinate.parse("library"))
    }

    @Test
    fun `returns null for too many segments`() {
        assertNull(Coordinate.parse("a:b:c:d"))
    }

    @Test
    fun `returns null for blank group`() {
        assertNull(Coordinate.parse(":library:1.0"))
    }

    @Test
    fun `returns null for blank name`() {
        assertNull(Coordinate.parse("com.example::1.0"))
    }
}
