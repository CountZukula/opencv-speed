import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.JavaFXFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import java.util.concurrent.ScheduledExecutorService
import javax.swing.Spring.height


class Controller {

    // this grabs our frames from the stream
    private lateinit var grabber: FFmpegFrameGrabber

    // the FXML button
    @FXML
    private lateinit var button: Button

    @FXML
    private lateinit var toggleGrey: Button

    // the FXML image view
    @FXML
    private lateinit var currentFrame: ImageView

    // a timer for acquiring the video stream
    private var timer: ScheduledExecutorService? = null

    private val cameraUri = "http://192.168.178.111:2222"

    // toggle to show color or gray image
    private var showGrayImage = false

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
        val converter = JavaFXFrameConverter()
        val converterCV = OpenCVFrameConverter.ToMat()

        // print some info
        grabber.apply {
            println("uri: $cameraUri")
            println("framerate: ${this.videoFrameRate}")
            println("has video: ${this.hasVideo()}")
            println("has audio: ${this.hasAudio()}")
            println("image height: $imageHeight")
            println("image width: $imageWidth")

            // change the image frame
            currentFrame.fitHeight = imageHeight.toDouble()
            currentFrame.fitWidth = imageWidth.toDouble()
        }

        // gray image that will be update with the latest image
        val grayImage = Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1)

        // set up a timer that fetches a frame at the correct moment
        frameTimer = vertx.setPeriodic((1000.0 / grabber.frameRate).toLong()) {
            // timer fired, we should fetch another frame
            val grabbedImage = grabber.grabImage()
            val image = converter.convert(grabbedImage)

            // create the gray image
            cvtColor(grabbedImage.let { converterCV.convert(it) }, grayImage, CV_BGR2GRAY)
                .also { cvtColor(grayImage, grayImage, CV_GRAY2BGR) }
//            cvtColor(converterCV.convert(grabbedImage), grayImage, CV_BGR2GRAY)

            if (image.isError) {
                println("image is error")
            } else {
                // convert it to a frame
//                println("image h ${image.height} w ${image.width}")

                // render the image on FX thread
                Platform.runLater {
                    if (!showGrayImage) {
                        currentFrame.imageProperty().set(image)
                        toggleGrey.text = "Grey"
                    } else {
                        currentFrame.imageProperty().set(converter.convert(converterCV.convert(grayImage)))
                        toggleGrey.text = "Colour"
                    }
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

    fun toggleGrey(actionEvent: ActionEvent) {
        showGrayImage = !showGrayImage
    }
}