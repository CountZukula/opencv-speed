import io.reactivex.Observable
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameConverter
import java.util.concurrent.TimeUnit
import org.bytedeco.javacv.*
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.helper.opencv_core.RGB
import org.bytedeco.opencv.opencv_core.*

/**
 * Create an Observable of Mat frames out of the grabber.
 */
fun FFmpegFrameGrabber.observable(): Observable<Frame> {
    return Observable.create<Frame> { observableEmitter ->
        while (true) {
            val grabbedImage = grabImage() ?: break
            observableEmitter.onNext(grabbedImage.clone())
        }
        observableEmitter.onComplete()
    }
}

/**
 * This makes sure that the source material is output at the expected times. Eliminates buffering issues.
 */
fun FFmpegFrameGrabber.bufferedObservable(): Observable<Mat> {
    var firstFrame: Long? = null
    var firstTime: Long? = null
    val converterCV = OpenCVFrameConverter.ToMat()
    return Observable
            .create<Pair<Mat, Long>> { observableEmitter ->
                while (true) {
                    val grabbedImage = grabImage() ?: break
                    // convert to a Matrix so we can continue the flow
                    val mat = converterCV.convert(grabbedImage)
                    observableEmitter.onNext(mat to grabbedImage.timestamp)
                }
                observableEmitter.onComplete()
            }
            .flatMap {
                // make sure we have the time to start with
                // we work in milliseconds here, no use to get more accurate
                val time = System.currentTimeMillis()
                // the pi outputs frame timestamps in microseconds
                if (firstFrame == null)
                    firstFrame = TimeUnit.MILLISECONDS.convert(it.second, TimeUnit.MICROSECONDS)
                if (firstTime == null)
                    firstTime = time
                // what's the difference between now and the time the first frame came in on the host?
                val hostDiff = time - firstTime!!
                // what's the difference between this frame and the first frame, as experienced by the source material?
                val sourceDiff = TimeUnit.MILLISECONDS.convert(it.second, TimeUnit.MICROSECONDS) - firstFrame!!
                // what's the drift between host and source?
                // - 0 is exactly right, output the frame right now
                // - >0 means the host should wait, delay the emission (drift)
                // - <0 means the frame should already be out there, just output it
                val drift = sourceDiff - hostDiff
                println("hostDiff $hostDiff sourceDiff $sourceDiff drift $drift")
                if (drift <= 0) {
                    Observable.just(it.first)
                } else {
                    Observable.just(it.first).delay(drift, TimeUnit.MILLISECONDS)
                }
            }
}

fun MatVector.asList(): List<Mat> {
    val result = mutableListOf<Mat>()
    for (i in 0 until this.size()) {
        result.add(this[i])
    }
    return result
}

fun Rect.intersects(br: Rect): Boolean =
        br.contains(tl()) || br.contains(tr()) || br.contains(bl()) || br.contains(br())
                || contains(br.tl()) || contains(br.tr()) || contains(br.bl()) || contains(br.br())

fun Rect.tr(): Point = Point(x() + width(), y())
fun Rect.bl(): Point = Point(0, y() + height())

fun Rect2d.toRect(): Rect = Rect(x().toInt(), y().toInt(), width().toInt(), height().toInt())
