package com.freetime.lumastore.shared

interface SourceStateStore {
    fun load(): String?
    fun save(value: String)
}

class InMemorySourceStateStore : SourceStateStore {
    private var value: String? = null

    override fun load(): String? = value

    override fun save(value: String) {
        this.value = value
    }
}
