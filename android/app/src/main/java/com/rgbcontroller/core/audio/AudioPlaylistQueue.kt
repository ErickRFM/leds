package com.rgbcontroller.core.audio

/** Pure session playlist model; does not access hardware or storage permissions. */
class AudioPlaylistQueue(private val limit: Int = 50) {
    init { require(limit in 1..500) }

    private val items = mutableListOf<String>()
    var currentIndex: Int = -1
        private set

    fun tracks(): List<String> = items.toList()
    fun current(): String? = items.getOrNull(currentIndex)

    fun enqueue(contentUri: String): Boolean {
        if (!contentUri.startsWith("content://") || contentUri.length > 2048 ||
            items.size >= limit || contentUri in items) return false
        items += contentUri
        if (currentIndex < 0) currentIndex = 0
        return true
    }

    fun select(index: Int): String? {
        if (index !in items.indices) return null
        currentIndex = index
        return current()
    }

    fun next(): String? {
        if (items.isEmpty()) return null
        currentIndex = (currentIndex + 1) % items.size
        return current()
    }

    fun previous(): String? {
        if (items.isEmpty()) return null
        currentIndex = (currentIndex - 1 + items.size) % items.size
        return current()
    }

    fun remove(index: Int): Boolean {
        if (index !in items.indices) return false
        items.removeAt(index)
        currentIndex = when {
            items.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            currentIndex >= items.size -> items.lastIndex
            else -> currentIndex
        }
        return true
    }

    fun clear() { items.clear(); currentIndex = -1 }
}
