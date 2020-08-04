import io.vertx.reactivex.core.Vertx

fun main() {
    val vertx = Vertx.vertx()
    vertx.setPeriodic(1000) {
        System.currentTimeMillis().div(1000).let {
            Thread.sleep(5000)
            println(it)
        }
    }
}

