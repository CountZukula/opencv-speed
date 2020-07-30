class Counter {
    var count = 0
    var start: Long = System.currentTimeMillis()

    /**
     * Reset the counter. Sets time to current and counter to 0.
     */
    fun reset(): Counter {
        start = System.currentTimeMillis()
        count = 0
        return this
    }

    fun inc(): Counter {
        count++
        return this
    }

    fun millisPassed(): Long = System.currentTimeMillis() - start

    fun perSecond(): Double {
        val secondsPassed = millisPassed().toDouble() / 1000
        return count / secondsPassed
    }
}