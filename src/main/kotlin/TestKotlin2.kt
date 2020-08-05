import io.reactivex.Observable
import io.vertx.reactivex.core.Vertx
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger


fun main() {
    Observable.interval(33, TimeUnit.MILLISECONDS)
            .map { System.currentTimeMillis() }
            .buffer(100, TimeUnit.MILLISECONDS)
            .subscribe {
                println("time: ${System.currentTimeMillis()} Got this: $it")
            }
    Thread.sleep(100000)
}


