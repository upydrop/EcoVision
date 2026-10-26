package com.example.ecovision

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class Prediction(
    val label: String,
    val confidence: Float
)

class WasteClassifier(context: Context) : Closeable {

    private val labels: List<String> =
        context.assets.open("labels.json")
            .bufferedReader()
            .use { reader ->
                org.json.JSONArray(reader.readText()).let { array ->
                    List(array.length()) { index ->
                        array.getString(index)
                    }
                }
            }

    private val interpreter: Interpreter

    init {
        val modelBuffer = context.assets
            .openFd("waste_classifier.tflite")
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength
                    )
                }
            }

        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }

        interpreter = Interpreter(modelBuffer, options)

        require(interpreter.getInputTensor(0).shape()
            .contentEquals(intArrayOf(1, 224, 224, 3))) {
            "Unexpected model input shape"
        }

        require(labels.size == interpreter.getOutputTensor(0).shape().last()) {
            "Number of labels does not match model output"
        }
    }

    fun classify(bitmap: Bitmap): Prediction {
        val inputSize = 224

        // Простое масштабирование для первой версии.
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            inputSize,
            inputSize,
            true
        )

        val input = ByteBuffer.allocateDirect(
            1 * inputSize * inputSize * 3 * 4
        ).order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(
            pixels, 0, inputSize, 0, 0, inputSize, inputSize
        )

        for (pixel in pixels) {
            input.putFloat(((pixel shr 16) and 0xFF).toFloat())
            input.putFloat(((pixel shr 8) and 0xFF).toFloat())
            input.putFloat((pixel and 0xFF).toFloat())
        }

        input.rewind()

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, output)

        val scores = output[0]
        val bestIndex = scores.indices.maxBy { scores[it] }

        if (resized !== bitmap) {
            resized.recycle()
        }

        return Prediction(
            label = labels[bestIndex],
            confidence = scores[bestIndex].coerceIn(0f, 1f)
        )
    }

    override fun close() {
        interpreter.close()
    }
}
