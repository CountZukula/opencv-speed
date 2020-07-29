import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Slider
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.JavaFXFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.CV_32SC1
import org.bytedeco.opencv.global.opencv_core.CV_8UC1
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Scalar
import java.net.URL
import java.util.*
import java.util.concurrent.ScheduledExecutorService


class Controller {

    // this grabs our frames from the stream
    private lateinit var grabber: FFmpegFrameGrabber

    // the FXML button
    @FXML
    private lateinit var toggleCamera: Button

    @FXML
    private lateinit var toggleGrey: Button

    @FXML
    private lateinit var displayMode: ComboBox<DISPLAY_MODE>

    @FXML
    private lateinit var currentFrame: ImageView

    @FXML
    private lateinit var thresholdSlider: Slider

    // a timer for acquiring the video stream
    private var timer: ScheduledExecutorService? = null

    private val cameraUri = "http://192.168.178.111:2222"

    // toggle to show color or gray image
    private var showGrayImage = false

    // fps of the stream
    private val fps = 30

    // if null, no timer is running
    private var frameTimer: Long? = null

    // what are we drawing?
    enum class DISPLAY_MODE {
        ORIGINAL, GRAY, CONTOUR
    }

    @FXML
    fun initialize() {
        println("Loaded the controller!")
        println(toggleGrey.text)
        println(displayMode == null)

        val itemList = FXCollections.observableArrayList<DISPLAY_MODE>()
        DISPLAY_MODE.values().forEach {
            itemList.add(it)
        }
        displayMode.items = itemList
        displayMode.selectionModel.select(DISPLAY_MODE.ORIGINAL)
    }


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

//        org.bytedeco.opencv.global.opencv_imgproc.drawContours(org.bytedeco.opencv.opencv_core.Mat, org.bytedeco.opencv.opencv_core.GpuMatVector, int, org.bytedeco.opencv.opencv_core.Scalar)
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
        val grayMat = Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1)
        val contourMat = Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1)
        val drawMat = Mat(grabber.imageHeight, grabber.imageWidth, CV_32SC1)

        // set up a timer that fetches a frame at the correct moment
        frameTimer = vertx.setPeriodic((1000.0 / grabber.frameRate).toLong()) {
            // timer fired, we should fetch another frame
            grabber.grabImage()
                .let { converterCV.convert(it) }
                .apply {
                    copyTo(drawMat)
                    cvtColor(this, grayMat, CV_BGR2GRAY)
                    grayMat.copyTo(contourMat)
                }

            val contours = MatVector()
            threshold(contourMat, contourMat, 155.0, 255.0, CV_THRESH_BINARY);
            findContours(grayMat, contours, CV_RETR_LIST, CV_CHAIN_APPROX_SIMPLE)
            val n = contours.size()
            for (i in 0 until n) {
                val contour = contours[i]
                val points = Mat()
                approxPolyDP(contour, points, arcLength(contour, true) * 0.02, true)
                drawContours(drawMat, MatVector(points), -1, Scalar.BLUE)
            }

            // ready to draw!
            // create the gray image
            // go to GRAY and back to BGR so we can draw it on the same ImageView
            if(displayMode.selectionModel.selectedItem == DISPLAY_MODE.ORIGINAL) {

            } else {

            }
            if (true)
                contourMat.copyTo(drawMat)
            if (showGrayImage)
                cvtColor(drawMat, drawMat, CV_BGR2GRAY)
            val image = converter.convert(converterCV.convert(drawMat))
            if (image.isError) {
                println("image is error")
            } else {
                // render the image on FX thread
                Platform.runLater {
                    currentFrame.imageProperty().set(image)
                }
            }
        }

        // update UI
        toggleCamera.text = "Stop"
    }

    private fun stopAcquisition() {
        // toggle UI
        toggleCamera.text = "Start"

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