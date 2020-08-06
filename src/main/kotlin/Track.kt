import Utils.randomColor
import javafx.geometry.Point2D
import org.bytedeco.opencv.opencv_core.*
import org.bytedeco.opencv.opencv_tracking.TrackerCSRT
import kotlin.math.abs

class Track(val br: Rect, val mat: Mat, val maximumAge: Int = 10000, val color: Scalar = randomColor()) {

    // this will be our history and the given br is our first entry
    val brs = mutableListOf<Rect>(br)
    val tracker: TrackerCSRT = TrackerCSRT.create()
    var lastUpdated = System.currentTimeMillis()

    val startMillis = System.currentTimeMillis()
    var endMillis: Long? = null
    var active = true

    // derived properties
    val retired: Boolean
        get() = System.currentTimeMillis() > startMillis + maximumAge
    val age: Long
        get() {
            return if (endMillis != null) {
                endMillis!! - startMillis
            } else {
                System.currentTimeMillis() - startMillis
            }
        }

    // start the track
    init {
        // initialise the tracker
        tracker.init(mat, br.toRect2d())
    }

    fun update(): Boolean {
        // if the track is not active, just return false
        if (!active)
            return false
        // if the track has reached maximum age, return kill it
        if (retired) {
            end()
            return false
        }
        // update with the tracker
        var newBox = Rect2d()
        val updated = tracker.update(mat, newBox)
        if (updated) {
            brs.add(newBox.toRect())
            lastUpdated = System.currentTimeMillis()
        } else {
            println("setting track to inactive, could not update")
            end()
        }
        return updated
    }


    fun end() {
        if (active) {
            active = false
            endMillis = System.currentTimeMillis()
        }
    }

    fun speedPixels(): Double {
        val distance = brs.first().center().toPoint2D().distance(brs.last().center().toPoint2D())
        return distance/age
    }

}

