import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler
import io.reactivex.schedulers.Schedulers
import javafx.collections.FXCollections
import javafx.embed.swing.SwingFXUtils
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Slider
import javafx.scene.control.Spinner
import javafx.scene.image.*
import model.DrawableMat
import model.Output
import model.ProcessedFrame
import org.bytedeco.javacv.*
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_imgproc.cvtColor
import org.bytedeco.opencv.opencv_core.Mat
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.util.concurrent.ScheduledExecutorService


class RxController {

    private var subscription: Disposable? = null

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

    // what are we drawing?
    enum class DISPLAY_MODE {
        ORIGINAL, GRAY, CONTOUR, DIFF, BLUR
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

        if (subscription != null) {
            stopAcquisition()
        } else {
            startAcquisition()
        }
    }

    private fun startAcquisition() {
        println("startAcquisition starting")
        // this helps us count frames per second
        var fpsCounter = Counter()

        // Two converters, two worlds.
        val converterFX = JavaFXFrameConverter()
        val converterCV = OpenCVFrameConverter.ToMat()
        val converter2D = Java2DFrameConverter()

        // create a buffer for the final image outputter
        val matBuffer = Mat()
        val formatByte: WritablePixelFormat<ByteBuffer> = PixelFormat.getByteBgraPreInstance()

        // execution is wrapped in a single so we can schedule easily
        subscription = Single.just(this)
                .observeOn(Schedulers.computation())
                .map {
                    // set up the frame grabber
                    grabber = FFmpegFrameGrabber(cameraUri)
                    println("Starting the frame grabber.")
                    grabber.start()
                    println("Started it.")

                    // set up the canvas sizes and print some info
                    grabber.apply {
                        printGrabberInfo(this)
                        // change the image frame
                        canvasFrame.width = imageWidth.toDouble()
                        canvasFrame.height = imageHeight.toDouble()
                        originalView.width = imageWidth.toDouble()
                        originalView.height = imageHeight.toDouble()
                    }

                    JavaFxScheduler.platform().scheduleDirect {
                        toggleCamera.text = "Started feed"
                        toggleCamera.disableProperty().value = false
                    }
                }
                .flatMapObservable {
                    // observable for the frames
                    grabber.observable()
                }
                // preprocess the frames
                .map { frame ->
                    print("frame.")
                    val time = System.currentTimeMillis()
                    // prepare the containers
                    val gray = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_8UC1), ColorSpace.GRAY)
                    val original = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_32SC1), ColorSpace.BGR)
                    // fill them up
                    converterCV.convert(frame).copyTo(original.mat)
                    cvtColor(original.mat, gray.mat, opencv_imgproc.CV_BGR2GRAY)
                    // save it to output
                    ProcessedFrame(
                            original = original,
                            gray = gray,
                            toDraw = original // placeholder, can be changed later
                    )
                }

                // pick the mat we want to draw
                .map {
                    print("processedframe.")
                    val from = it.original
                    val to = DrawableMat(Mat(), ColorSpace.BGR, from.timeMillis)
                    from.mat.copyTo(to.mat)
                    from.space = to.space
                    it.toDraw = to
                    // convert to color
                    toColor(it.toDraw)
                    it
                }

                // convert the desired frame into an image javafx can draw
                .map {
                    print("convertimage.")
                    // we can draw this image
//                    println("drawing: space ${it.toDraw!!.space} height ${it.toDraw!!.mat.arrayHeight()}")
//                    val image = converterFX.convert(converterCV.convert(it.toDraw.mat))

                    val bufferedImage: BufferedImage = converter2D.getBufferedImage(converterCV.convert(it.toDraw.mat))
                    val r = SwingFXUtils.toFXImage(bufferedImage, null)

                    Output(it, r)
                }
                // output is handled on the javafx thread, needs to be able to draw
                .observeOn(JavaFxScheduler.platform())
                .subscribe { output ->
                    print("draw.")
                    // count fps
//                    fpsCounter.tick(1000)
                    // draw the desired image
                    canvasFrame.graphicsContext2D.clearRect(0.0, 0.0, output.image.width, output.image.height)
                    canvasFrame.graphicsContext2D.drawImage(output.image, 0.0, 0.0)
                    canvasFrame.graphicsContext2D.save()

                    fpsCounter.inc()

                    println("${fpsCounter.count}.")
                }
        toggleCamera.text = "Starting feed"
        toggleCamera.disableProperty().value = true
        println("startAcquisition done")
    }


    /**
     * Make sure that the given mat is in the BGR space.
     */
    private fun toColor(d: DrawableMat) {
        // if it's grayscale, convert
        if (d.space == ColorSpace.GRAY) {
            cvtColor(d.mat, d.mat, opencv_imgproc.CV_GRAY2BGR)
            d.space = ColorSpace.BGR
        }
    }

    /**
     * Pair each frame with the previous frame for easy access.
     */
    private fun pairPreviousAndCurrent(frames: Observable<Frame>): Observable<Array<Frame>>? {
        // first put each frame together with its previous frame, for easy processing
        return frames.scan(emptyArray<Frame>()) { previousAndCurrent: Array<Frame>, next: Frame ->
            when {
                previousAndCurrent.isEmpty() -> {
                    arrayOf(next)
                }
                previousAndCurrent.size == 1 -> {
                    arrayOf(previousAndCurrent[0], next)
                }
                else -> {
                    arrayOf(previousAndCurrent[1], next)
                }
            }
        }
    }

    private fun printGrabberInfo(grabber: FFmpegFrameGrabber) {
        grabber.apply {
            println("uri: $cameraUri")
            println("framerate: $videoFrameRate")
            println("has video: ${hasVideo()}")
            println("has audio: ${hasAudio()}")
            println("image height: $imageHeight")
            println("image width: $imageWidth")

        }
    }

    private fun stopAcquisition() {
        // release grabber
        if (grabber != null) {
            grabber.release()
        }

        // stop the active subscription
        if (subscription != null) {
            subscription!!.dispose()
        }

        // toggle UI, be ready for the next run
        toggleCamera.text = "Start"
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