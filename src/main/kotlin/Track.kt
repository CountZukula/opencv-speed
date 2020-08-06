import Utils.randomColor
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.Rect2d
import org.bytedeco.opencv.opencv_tracking.TrackerCSRT

class Track(val br: Rect, val mat: Mat) {

    // this will be our history and the given br is our first entry
    val brs = mutableListOf<Rect>(br)
    val tracker: TrackerCSRT = TrackerCSRT.create()
    var active = true
    var lastUpdated = System.currentTimeMillis()
    val color = randomColor()

    init {
        tracker.init(mat, br.toRect2d())
    }

    fun update(): Boolean {
        // if the track is not active, just return false
        if (!active)
            return false
        // update with the tracker
        var newBox: Rect2d = Rect2d()
        val updated = tracker.update(mat, newBox)
        if (updated) {
            brs.add(newBox.toRect())
            lastUpdated = System.currentTimeMillis()
        } else {
            active = false
        }
        return updated
    }
}
