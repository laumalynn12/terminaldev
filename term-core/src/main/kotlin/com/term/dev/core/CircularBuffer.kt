package com.term.dev.core

class CircularBuffer<T>(val capacity: Int) {
    init {
        require(capacity > 0) { "Capacity must be greater than 0" }
    }

    private val elements = ArrayList<T?>(capacity).apply {
        repeat(capacity) { add(null) }
    }
    private var head = 0
    private var tail = 0
    var size = 0
        private set

    val isFull: Boolean
        get() = size == capacity

    val isEmpty: Boolean
        get() = size == 0

    fun push(item: T) {
        if (isFull) {
            elements[head] = item
            head = (head + 1) % capacity
            tail = (tail + 1) % capacity
        } else {
            elements[tail] = item
            tail = (tail + 1) % capacity
            size++
        }
    }

    fun get(index: Int): T {
        require(index in 0 until size) { "Index $index out of bounds for size $size" }
        val actualIndex = (head + index) % capacity
        @Suppress("UNCHECKED_CAST")
        return elements[actualIndex] as T
    }

    fun clear() {
        head = 0
        tail = 0
        size = 0
        elements.fill(null)
    }
}
