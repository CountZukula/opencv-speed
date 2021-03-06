import javafx.application.Platform
import javafx.beans.property.ObjectProperty
import org.bytedeco.opencv.helper.opencv_core
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import kotlin.random.Random


object Utils {

    /**
     * Generic method for putting element running on a non-JavaFX thread on the
     * JavaFX thread, to properly update the UI
     *
     * @param property
     * a [ObjectProperty]
     * @param value
     * the value to set for the given [ObjectProperty]
     */
    fun <T> onFXThread(property: ObjectProperty<T>, value: T) {
        Platform.runLater {
            property.set(value)
        }
    }

    /**
     * Support for the [] method
     *
     * @param original
     * the [Mat] object in BGR or grayscale
     * @return the corresponding [BufferedImage]
     */
    private fun matToBufferedImage(original: Mat): BufferedImage {
        // init
        var image: BufferedImage? = null
        val width = original.arrayWidth()
        val height = original.arrayHeight()
        val channels = original.channels()
        val sourcePixels = ByteArray(width * height * channels)
        image = if (original.channels() > 1) {
            BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
        } else {
            BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
        }
        val targetPixels = (image.raster.dataBuffer as DataBufferByte).data
        System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.size)
        return image
    }

    fun randomColor() = opencv_core.RGB(Random.nextDouble(256.0), Random.nextDouble(256.0), Random.nextDouble(256.0))

}

