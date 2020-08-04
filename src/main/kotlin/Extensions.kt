import io.reactivex.Observable
import model.printTime
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame

/**
 * Create an Observable of Mat frames out of the grabber.
 */
fun FFmpegFrameGrabber.observable(): Observable<Frame> {
    return Observable.create<Frame> { observableEmitter ->
        while (true) {
            val grabbedImage = grabImage() ?: break
            observableEmitter.onNext(grabbedImage)
        }
        observableEmitter.onComplete()
    }
}
