package model

import ColorSpace
import org.bytedeco.opencv.opencv_core.Mat

data class DrawableMat(val mat: Mat, var space: ColorSpace, val timeMillis: Long = System.currentTimeMillis())

