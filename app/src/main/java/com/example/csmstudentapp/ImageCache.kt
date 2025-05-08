package com.example.csmstudentapp

object ImageCache {
    private val cache = mutableMapOf<String, String>()

    fun storeImage(rollNumber: String, imageBase64: String) {
        cache[rollNumber] = imageBase64
    }

    fun getImage(rollNumber: String): String? {
        return cache[rollNumber]
    }

    fun clearImage(rollNumber: String) {
        cache.remove(rollNumber)
    }

    fun clearAll() {
        cache.clear()
    }
}