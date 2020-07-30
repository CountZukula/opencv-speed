import io.reactivex.Observable
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.JavaFXFrameConverter
import org.bytedeco.javacv.OpenCVFrameConverter
import org.bytedeco.opencv.global.opencv_core.*
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.MatVector
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Size
import java.util.concurrent.ScheduledExecutorService


class Controller {

    // this grabs our frames from the stream
    private lateinit var grabber: FFmpegFrameGrabber

    // the FXML button
    @FXML
    private lateinit var toggleCamera: Button

    @FXML
    private lateinit var blurSpinner: Spinner<Int>

    @FXML
    private lateinit var toggleGrey: Button

    @FXML
    private lateinit var canvasFrame: Canvas

    @FXML
    private lateinit var originalView: Canvas

    @FXML
    private lateinit var displayMode: ComboBox<DISPLAY_MODE>

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
        ORIGINAL, GRAY, CONTOUR, DIFF, BLUR
    }

    // the known color spaces
    enum class COLOR_SPACE {
        BGR, GRAY
    }

    @FXML
    fun initialize() {
        val itemList = FXCollections.observableArrayList<DISPLAY_MODE>()
        DISPLAY_MODE.values().forEach {
            itemList.add(it)
        }
        displayMode.items = itemList
        displayMode.selectionModel.select(DISPLAY_MODE.ORIGINAL)

        // initial blur value
        blurSpinner.valueFactory.value = 10
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
        // acquisition thread
        val run = Runnable {
            // set up the frame grabber
            grabber = FFmpegFrameGrabber(cameraUri)
//        grabber.format = "h264"
            grabber.start()

            // this helps us count frames per second
            var fpsCounter = Counter()

            // get an Observable going for the frames served by the Pi
//            val frameObservable = Observable.create<Frame> { observableEmitter ->
//                while (true) {
//                    val grabbedImage = grabber.grabImage()
//                    if (grabbedImage == null)
//                        break
//                    observableEmitter.onNext(grabbedImage)
//                }
//                observableEmitter.onComplete()
//            }
//
//            frameObservable.blockingForEach {
//                println("got an image from the observable!")
//            }

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
                canvasFrame.width = imageWidth.toDouble()
                canvasFrame.height = imageHeight.toDouble()
                originalView.width = imageWidth.toDouble()
                originalView.height = imageHeight.toDouble()
            }


            data class DrawableMat(val mat: Mat, val space: COLOR_SPACE, val timeMillis: Long = System.currentTimeMillis())

            // gray image that will be update with the latest image
            val grayMat = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1), COLOR_SPACE.GRAY)
            // this will be initialised in the first frame with grayMat
            val previousGrayMat: DrawableMat by lazy {
                DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1), COLOR_SPACE.GRAY)
                        .apply {
                            grayMat.mat.copyTo(this.mat)
                        }
            }
            val diffMat = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1), COLOR_SPACE.GRAY)
            val blurDiffMat = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1), COLOR_SPACE.GRAY)
            val contourMat = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, CV_8UC1), COLOR_SPACE.GRAY)
            val originalMat = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, CV_32SC1), COLOR_SPACE.BGR)

            // this is the image that will be drawn on the GUI every frame
            var toDraw = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, CV_32SC1), COLOR_SPACE.BGR)


            // set up a timer that fetches a frame at the correct moment
            var refreshTimer = (1000.0 / grabber.frameRate).toLong()
            println("Refresh every ms: $refreshTimer")

            println("starting while")
            while (true) {
                val grabbedImage = grabber.grabImage()
                if (grabbedImage == null) {
                    println("got a null image :(")
                    break
                }
                fpsCounter.inc()
                if (fpsCounter.millisPassed() > 1000) {
                    println("fps: ${fpsCounter.perSecond()}")
                    fpsCounter.reset()
                }

                // timer fired, we should fetch another frame
                grabbedImage
                        .let {
                            converterCV.convert(it)
                        }
                        .apply {
                            // we save the frame to the 'original' version, full color
                            this.copyTo(originalMat.mat)
                        }


                // do calculations, preprocessing and setup moves
                // create the gray scale image
                cvtColor(originalMat.mat, grayMat.mat, CV_BGR2GRAY)

                // absolute difference between this frame and the previous
                absdiff(previousGrayMat.mat, grayMat.mat, diffMat.mat)
                blur(diffMat.mat, blurDiffMat.mat, Size(blurSpinner.value, blurSpinner.value))

                // calculate contours
                val contours = MatVector()
                threshold(blurDiffMat.mat, contourMat.mat, thresholdSlider.value, thresholdSlider.max, CV_THRESH_BINARY);
                findContours(contourMat.mat, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_SIMPLE)

                // get ready for the next frame
                grayMat.mat.copyTo(previousGrayMat.mat)

                // pick which of the images we want to draw, any GUI post-processing will happen on top of this
                // render the image on FX thread
                toDraw = when (displayMode.selectionModel.selectedItem) {
                    DISPLAY_MODE.ORIGINAL -> {
                        originalMat
                    }
                    DISPLAY_MODE.GRAY -> {
                        grayMat
                    }
                    DISPLAY_MODE.CONTOUR -> {
                        contourMat
                    }
                    DISPLAY_MODE.DIFF -> {
                        diffMat
                    }
                    DISPLAY_MODE.BLUR -> {
                        blurDiffMat
                    }
                    else -> {
                        originalMat
                    }

                    // ready to draw!
                    // create the gray image
                    // go to GRAY and back to BGR so we can draw it on the same ImageView
                }

                // ready to draw!
                // create the gray image
                // go to GRAY and back to BGR so we can draw it on the same ImageView
                if (toDraw.space == COLOR_SPACE.GRAY) {
                    cvtColor(toDraw.mat, toDraw.mat, CV_GRAY2BGR)
                }

                // draw the contours on the final image
                val n = contours.size()
                for (i in 0 until n) {
                    val contour = contours[i]
                    val points = Mat()
                    approxPolyDP(contour, points, arcLength(contour, true) * 0.02, true)
                    drawContours(toDraw.mat, MatVector(points), -1, Scalar.YELLOW)
                }

                val image = converter.convert(converterCV.convert(toDraw.mat))
                if (image.isError) {
                    println("image is error")
                } else {
                    // render the image on FX thread
                    Platform.runLater {
//                    currentFrame.imageProperty().set(image)
                        canvasFrame.graphicsContext2D.drawImage(image, 0.0, 0.0)
                        originalView.graphicsContext2D.drawImage(converter.convert(converterCV.convert(originalMat.mat)), 0.0, 0.0)
                    }
                }

            }
        }
        // start it
        Thread(run).start()
        // update UI
        toggleCamera.text = "Stop"
    }

    private fun stopAcquisition() {
        // toggle UI
        toggleCamera.text = "Start"

        // stop listening
        if (frameTimer != null) {
            vertx.cancelTimer(frameTimer!!)
        }

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