package model

import ColorSpace
import org.bytedeco.javacv.Frame
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.opencv_core.Mat

data class ProcessedFrame(
        val original: DrawableMat,
        val gray: DrawableMat,
        var toDraw: DrawableMat
)
