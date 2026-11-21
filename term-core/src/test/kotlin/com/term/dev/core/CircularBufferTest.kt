package com.term.dev.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircularBufferTest {

    @Test
    fun testPushAndRetrieve() {
        val buffer = CircularBuffer<String>(3)
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)

        buffer.push("A")
        buffer.push("B")
        assertEquals(2, buffer.size)
        assertEquals("A", buffer.get(0))
        assertEquals("B", buffer.get(1))
    }

    @Test
    fun testEvictionWhenFull() {
        val buffer = CircularBuffer<Int>(3)
        buffer.push(1)
        buffer.push(2)
        buffer.push(3)
        assertTrue(buffer.isFull)
        assertEquals(3, buffer.size)

        buffer.push(4)
        assertEquals(3, buffer.size)
        assertEquals(2, buffer.get(0))
        assertEquals(3, buffer.get(1))
        assertEquals(4, buffer.get(2))
    }

    @Test
    fun testClear() {
        val buffer = CircularBuffer<String>(2)
        buffer.push("X")
        buffer.clear()
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)
    }
}
