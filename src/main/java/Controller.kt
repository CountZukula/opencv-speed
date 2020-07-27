import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.JavaFXFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_videoio.VideoCapture
import org.opencv.videoio.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class Controller {

    // this grabs our frames from the stream
    private lateinit var grabber: FFmpegFrameGrabber

    // the FXML button
    @FXML
    private lateinit var button: Button

    // the FXML image view
    @FXML
    private lateinit var currentFrame: ImageView

    // a timer for acquiring the video stream
    private var timer: ScheduledExecutorService? = null

    private val cameraUri = "http://192.168.178.111:2222"

    // fps of the stream
    private val fps = 30

    // if null, no timer is running
    private var frameTimer: Long? = null

    /**
     * The action triggered by pushing the button on the GUI
     *
     * @param event
     * the push button event
     */
    @FXML
    private fun toggleCamera(event: ActionEvent?) {

        if (frameTimer != null) {
            stopAcquisition()
        } else {
            startAcquisition()
        }
    }

    private fun startAcquisition() {
        // set up the frame grabber
        grabber = FFmpegFrameGrabber(cameraUri)
//        grabber.format = "h264"
        grabber.start()

        // CanvasFrame, FrameGrabber, and FrameRecorder use Frame objects to communicate image data.
        // We need a FrameConverter to interface with other APIs (Android, Java 2D, JavaFX, Tesseract, OpenCV, etc).
        val converter: OpenCVFrameConverter.ToMat = OpenCVFrameConverter.ToMat()
        val converter2 = JavaFXFrameConverter()

        grabber.apply {
            println("framerate: ${this.videoFrameRate}")
            println("has video: ${this.hasVideo()}")
            println("has audio: ${this.hasAudio()}")
            println("image height: $imageHeight")
            println("image wideth: $imageWidth")
        }

        // set up a timer that fetches a frame at the correct moment
        frameTimer = vertx.setPeriodic((1000.0 / fps).toLong()) {
            // timer fired, we should fetch another frame
//            val grab = grabber.grabImage()
//            val iplImage = converter.convertToIplImage(grab)
            val image = converter2.convert(grabber.grabImage())
//            val mat = converter.convert(grab)

            if (image.isError) {
                println("image is error")
            } else {
                // convert it to a frame
//                println("image h ${image.height} w ${image.width}")
                // render the image on FX thread
                Platform.runLater {
                    currentFrame.imageProperty().set(image)
                    // currentFrame.image = image
                    currentFrame.fitHeight = image.height
                    currentFrame.fitWidth = image.width
                }
            }
        }

        // update UI
        button.text = "Stop"
    }

    private fun stopAcquisition() {
        // toggle UI
        button.text = "Start"

        // stop listening
        if (frameTimer != null)
            vertx.cancelTimer(frameTimer!!)
        grabber.release()
    }

    /**
     * Update the [ImageView] in the JavaFX main thread
     *
     * @param view
     * the [ImageView] to update
     * @param image
     * the [Image] to show
     */
    private fun updateImageView(view: ImageView?, image: Image) {
        Utils.onFXThread(view!!.imageProperty(), image)
    }

    /**
     * On application close, stop the acquisition from the camera
     */
    fun setClosed() {
        stopAcquisition()
    }
}