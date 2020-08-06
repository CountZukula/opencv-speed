import Utils.randomColor
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Spinner
import javafx.scene.image.*
import model.DrawableMat
import org.bytedeco.javacv.*
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.global.opencv_imgproc.*
import org.bytedeco.opencv.opencv_core.*
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.text.DecimalFormat
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.round


class ControllerSequential {

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

//    @FXML
//    private lateinit var canvasFrame: Canvas

    @FXML
    private lateinit var originalView: ImageView

    @FXML
    private lateinit var displayMode: ComboBox<DISPLAY_MODE>

    @FXML
    private lateinit var thresholdSpinner: Spinner<Double>

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

    private val minimumContourBoundingArea = 2000

    private fun startAcquisition() {
        val run = Runnable {
            // this helps us count frames per second
            var fpsCounter = Counter()
            // Two converters, two worlds.
            val converterFX = JavaFXFrameConverter()
            val converterCV = OpenCVFrameConverter.ToMat()
            val converterOrgOpenCV = OpenCVFrameConverter.ToOrgOpenCvCoreMat()
            val converter2D = Java2DFrameConverter()

            // configuration variables
            // TODO move these to a config file
            val maximumActiveTracks = 5

            // set up the frame grabber
            grabber = FFmpegFrameGrabber(cameraUri)
            println("Starting the frame grabber.")
            grabber.start()
            println("Started it.")

            // set up the canvas sizes and print some info
            grabber.apply {
                printGrabberInfo(this)
                // change the image frame
//                canvasFrame.width = imageWidth.toDouble()
//                canvasFrame.height = imageHeight.toDouble()
                originalView.fitWidth = imageWidth.toDouble()
                originalView.fitHeight = imageHeight.toDouble()
            }


            // create a buffer for the final image outputter
            var matBuffer = Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_8UC4)
            var buffer: ByteBuffer = matBuffer.createBuffer()
            val formatByte: WritablePixelFormat<ByteBuffer> = PixelFormat.getByteBgraPreInstance()

            // update UI
            Platform.runLater {
                toggleCamera.text = "Started feed"
                toggleCamera.disableProperty().value = false
            }

            // grab a frame to start with
            // keep track of the current time and the time of the source material
            val firstFrame = TimeUnit.MILLISECONDS.convert(grabber.grabImage().timestamp, TimeUnit.MICROSECONDS)
            val firstTime = System.currentTimeMillis()


            // all the matrices we need
            val mats = object {
                val gray = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_8UC1), ColorSpace.GRAY)
                val previousGray = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_8UC1), ColorSpace.GRAY)
                val diff = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_8UC1), ColorSpace.GRAY)
                val blurDiff = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_8UC1), ColorSpace.GRAY)
                val threshold = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_8UC1), ColorSpace.GRAY)
                val original = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_32SC1), ColorSpace.BGR)
                val draw = DrawableMat(Mat(grabber.imageHeight, grabber.imageWidth, opencv_core.CV_32SC1), ColorSpace.BGR)
            }

            // initialize stuff with a first frame
            grabber.grabImage().also {
                val mat = converterCV.convert(it)
                cvtColor(mat, mats.previousGray.mat, CV_BGR2GRAY)
            }

            // entry and exit rectangles (left and right portion of the image in which no tracking occurs and where tracks are being removed)
            val exitWidthPercentage = 0.05
            val leftExit = Rect(grabber.imageRect().tl(), Point((grabber.imageWidth * exitWidthPercentage).toInt(), grabber.imageHeight))
            val rightExit = Rect(Point(grabber.imageWidth - (grabber.imageWidth * exitWidthPercentage).toInt(), 0), grabber.imageRect().br())
            // divide the whole image in quarts
            val q1 = Rect(Point((grabber.imageWidth * 0.0).toInt(), 0), Point((grabber.imageWidth * 0.25).toInt(), grabber.imageHeight))
            val q2 = Rect(Point((grabber.imageWidth * 0.25).toInt(), 0), Point((grabber.imageWidth * 0.50).toInt(), grabber.imageHeight))
            val q3 = Rect(Point((grabber.imageWidth * 0.50).toInt(), 0), Point((grabber.imageWidth * 0.75).toInt(), grabber.imageHeight))
            val q4 = Rect(Point((grabber.imageWidth * 0.75).toInt(), 0), Point((grabber.imageWidth * 1.0).toInt(), grabber.imageHeight))

            // tracks that we want to keep
            val tracks = mutableListOf<Track>()
            val speedTracks = mutableListOf<Track>()

            // start grabbing frames
            while (true) {
                val grabbedImage = grabber.grabImage()

                // start processing
                val mat = converterCV.convert(grabbedImage)
                // prepare the containers
                // fill them up
                mat.copyTo(mats.original.mat)
                cvtColor(mats.original.mat, mats.gray.mat, CV_BGR2GRAY)
                mat.copyTo(mats.draw.mat)

                // do the processing
                drawGrid(mats.draw, 100)
                // 0,0 is top left
                drawRectangle(mats.draw, Point(0, 400), Point(mats.draw.mat.arrayWidth(), 200))

                // do contour detection
                opencv_core.absdiff(mats.previousGray.mat, mats.gray.mat, mats.diff.mat)
                blur(mats.diff.mat, mats.blurDiff.mat, Size(10, 10))
                threshold(mats.blurDiff.mat, mats.threshold.mat, thresholdSpinner.value, 255.0, THRESH_BINARY)
                val contours = MatVector()
                findContours(mats.threshold.mat, contours, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)

                // remove old tracks
                tracks.removeIf { track ->
                    // 10 seconds old tracks are
                    val shouldRemove = !track.active || track.brs.last().intersects(leftExit) || track.brs.last().intersects(rightExit)
                    if (shouldRemove) {
                        println("going to remove a track! ${System.currentTimeMillis() - track.lastUpdated}ms since last update")
                        track.end()
                    }

                    // if the track should be removed, check if it's a valid speed measurement track
                    if (shouldRemove && isValidSpeedTrack(track, q1, q4)) {
                        speedTracks.add(track)
                    }

                    shouldRemove
                }

                // before we look for new candidates, check our existing tracks
                // find, for each track, the next likely matching rectangle
                tracks.forEach { track ->
//                    val cvMat = org.opencv.core.Mat(mats.draw.mat.address())
                    track.update()
                }

                // draw the tracks
                tracks.forEach { track ->
                    // draw the entire track in the same color
//                    track.brs.forEach { rect -> rectangle(mats.draw.mat, rect, track.color) }
                    track.brs.first().apply { rectangle(mats.draw.mat, this, track.color) }
                    track.brs.last().apply { rectangle(mats.draw.mat, this, track.color) }
                }
                speedTracks.forEach { track ->
                    track.brs.first().apply { rectangle(mats.draw.mat, this, track.color, 2, LINE_4, 0) }
                    track.brs.last().apply { rectangle(mats.draw.mat, this, track.color, 2, LINE_4, 0) }
                    round(3.0)
                    val df = DecimalFormat("#.##")
                    df.roundingMode = RoundingMode.CEILING
                    // draw the speed!
                    putText(mats.draw.mat, "${df.format(track.speedPixels())}px/ms", track.brs.last().tl().moveY(10).moveX(10), FONT_HERSHEY_SIMPLEX, 0.4, track.color)
                    putText(mats.draw.mat, "${df.format(track.age)}ms", track.brs.last().tl().moveX(10).moveY(20), FONT_HERSHEY_SIMPLEX, 0.4, track.color)
                }

                // draw the largest contour's bounding rectangle
                val biggestContour = contours.asList().maxBy { boundingRect(it).area() }
                if (biggestContour != null) {
                    drawRectangle(mats.draw.mat, boundingRect(biggestContour), Scalar.YELLOW)
                }

                // determine the largest contour
                // this might add a new track, so only do it if we didn't reach our maximum track amount
                if (tracks.size < 4) {
                    val biggestContour = contours.asList().maxBy { boundingRect(it).area() }
                    if (isGoodTrackStart(biggestContour, leftExit, rightExit, minimumContourBoundingArea) && tracks.size < maximumActiveTracks) {
                        val br = boundingRect(biggestContour)
                        println("start of track, area: " + br.area())
                        rectangle(mats.draw.mat, br, Scalar.RED)
                        putText(mats.draw.mat, "area ${br.area()}", br.tl(), FONT_HERSHEY_SIMPLEX, 0.5, Scalar.GREEN)
                        // make sure we're not overlapping with an existing track
                        // also, just do one track for now
                        if (br.area() > minimumContourBoundingArea) {
                            // might be a nice track start
                            // check if there's an overlap with the last frame of a track
                            val overlapExists = tracks.any { it.brs.last().intersects(br) }
                            if (!overlapExists) {
                                tracks.add(Track(br, mats.original.mat))
                            }
                        }
                    }
                }

                // draw info
                addInfo(mat = mats.draw.mat, fps = fpsCounter.perSecond, tracks = tracks)

                // draw exit rectangles
                drawRectangle(mats.draw.mat, leftExit)
                drawRectangle(mats.draw.mat, rightExit)


                // try the direct buffer approach
                cvtColor(mats.draw.mat, matBuffer, COLOR_BGR2BGRA)
                val pb = PixelBuffer(matBuffer.arrayWidth(), matBuffer.arrayHeight(), buffer, formatByte)
                val wi = WritableImage(pb)

                // before drawing, determine whether we should wait
                // what's the difference between now and the time the first frame came in on the host?
                val hostDiff = System.currentTimeMillis() - firstTime
                // what's the difference between this frame and the first frame, as experienced by the source material?
                val sourceDiff = TimeUnit.MILLISECONDS.convert(grabbedImage.timestamp, TimeUnit.MICROSECONDS) - firstFrame
                // what's the drift between host and source?
                // - 0 is exactly right, output the frame right now
                // - >0 means the host should wait, delay the emission (drift)
                // - <0 means the frame should already be out there, just output it
                val drift = sourceDiff - hostDiff
//                println("hostDiff $hostDiff sourceDiff $sourceDiff drift $drift")
                if (drift > 0) {
                    Thread.sleep(drift)
                }
                originalView.image = wi

                // keep a copy of the current gray scale image in the previous grayscale image so we can perform contour detection
                mats.apply { gray.mat.copyTo(previousGray.mat) }

                // keep track of fps
                fpsCounter.tick(resetAfterMillis = 1000, print = false)
            }
        }
        Thread(run).start()

        toggleCamera.text = "Starting feed"
        toggleCamera.disableProperty().value = true
        println("startAcquisition done")
    }

    private fun isValidSpeedTrack(track: Track, q1: Rect, q4: Rect): Boolean {
        // we need sufficient measurements
        if (track.brs.size < 5)
            return false
        // check if the first and last are on opposite sides of the image
        return (track.brs.first().intersects(q1) && track.brs.last().intersects(q4)) ||
                (track.brs.first().intersects(q4) && track.brs.last().intersects(q1))
    }

    private fun isGoodTrackStart(biggestContour: Mat?, leftExit: Rect, rightExit: Rect, minimumContourBoundingArea: Int): Boolean =
            if (biggestContour == null) {
                false
            } else {
                val br = boundingRect(biggestContour)
                !br.intersects(leftExit) && !br.intersects(rightExit) && (br.area() >= minimumContourBoundingArea)
            }

    private fun addInfo(mat: Mat, fps: Double, tracks: MutableList<Track>) {
        var y = 10
        val x = 5
        val lineHeight = 7
        putText(mat, "fps $fps",
                Point(x, y.also { y += lineHeight }), FONT_HERSHEY_SIMPLEX, 0.4, Scalar.BLUE)
        putText(mat, "#tracks ${tracks.size}",
                Point(x, y.also { y += lineHeight }), FONT_HERSHEY_SIMPLEX, 0.4, Scalar.BLUE)
    }

    private fun drawRectangle(draw: Mat, p1: Rect, color: Scalar = Scalar.RED) {
        rectangle(draw, p1, color)
    }

    private fun drawGrid(draw: DrawableMat, step: Int) {
        for (i in 0..draw.mat.arrayHeight() step step) {
            line(draw.mat, Point(0, i), Point(draw.mat.arrayWidth(), i), Scalar.YELLOW)
        }
    }

    private fun drawRectangle(draw: DrawableMat, p1: Point, p2: Point) {
        rectangle(draw.mat, Rect(p1, p2), AbstractScalar.WHITE)
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

    fun drawContours(mat: Mat, contours: MatVector, drawContour: Boolean = true, drawBoundingRect: Boolean = true) {
        for (i in 0 until contours.size()) {
            //random color
            if (drawContour)
                drawContours(mat, contours, i.toInt(), randomColor())
            boundingRect(contours[i]).also {
                if (drawBoundingRect)
                    drawRectangle(mat, it)
            }
        }
    }
}


fun Rect.toRect2d(): Rect2d {
    return Rect2d(x().toDouble(), y().toDouble(), width().toDouble(), height().toDouble())
}

