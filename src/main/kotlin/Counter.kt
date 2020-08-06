class Counter {
    var count = 0
    var start: Long = System.currentTimeMillis()
    val millisPassed: Long
        get() = System.currentTimeMillis() - start
    val perSecond: Double
        get() {
            val secondsPassed = millisPassed.toDouble() / 1000
            return count / secondsPassed
        }

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

    /**
     * Increase the counter, print and reset if the given time has passed.
     */
    fun tick(resetAfterMillis: Long, print: Boolean = true) {
        inc()
        if (millisPassed > resetAfterMillis) {
            if (print)
                println("""per sec: $perSecond counter: $count millis passed: $millisPassed""")
            reset()
        }
    }
}