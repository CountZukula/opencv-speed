import io.reactivex.Observable
import io.vertx.reactivex.core.Vertx
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@ExperimentalStdlibApi
fun main() {
//    straight()
    observable()
}

fun observable() {
    // do a manual test of the ffmpeggrabber
    val grabber = FFmpegFrameGrabber("http://192.168.178.111:2222")
    grabber.start()
    val counter = Counter()
    grabber
            .bufferedObservable()
            .subscribe {
                counter.tick(30)
            }
    Thread.sleep(200000)
}

fun straight() {
    // do a manual test of the ffmpeggrabber
    val grabber = FFmpegFrameGrabber("http://192.168.178.111:2222")
    grabber.start()
    val vertx = Vertx.vertx()
    val count = AtomicInteger()
    var start = AtomicLong(System.currentTimeMillis())
    val timestamps = mutableListOf<Long>()
    vertx.setPeriodic(33) {
        val timestampDiffs = mutableListOf<Long>()
        for (i in 1 until timestamps.size) {
            timestampDiffs.add(timestamps[i] - timestamps[i - 1])
        }
        println("count: ${count.getAndSet(0)} millis: ${System.currentTimeMillis() - start.getAndSet(System.currentTimeMillis())} timestampDiffs ${timestampDiffs.joinToString(",")} timestamps ${timestamps.joinToString(",")}")
        timestamps.clear()
    }
    while (true) {
        val image = grabber.grab()
        timestamps.add(image.timestamp)
        count.incrementAndGet()
    }
}

