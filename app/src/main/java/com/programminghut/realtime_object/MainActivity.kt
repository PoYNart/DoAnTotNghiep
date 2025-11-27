// File: MainActivity.kt (PHIÊN BẢN HOÀN CHỈNH: CAMERA + THƯ VIỆN)
package com.programminghut.realtime_object

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    lateinit var labels: List<String>
    val colors = listOf(Color.BLUE, Color.GREEN, Color.RED)
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var interpreter: Interpreter
    var modelInputWidth = 0
    var modelInputHeight = 0

    lateinit var captureButton: Button
    lateinit var galleryButton: Button // <-- Thêm nút chọn ảnh
    lateinit var debugTextView: TextView
    private var isResultScreen = false

    // Trình xử lý kết quả trả về từ thư viện ảnh
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.let { uri ->
                // Chuyển URI thành Bitmap và xử lý
                bitmap = uriToBitmap(uri)
                processImage(bitmap)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        labels = FileUtil.loadLabels(this, "labels.txt")
        val modelFile = FileUtil.loadMappedFile(this, "model_xoai_metadata.tflite")
        interpreter = Interpreter(modelFile, Interpreter.Options().apply { numThreads = 4 })

        val inputTensor = interpreter.getInputTensor(0)
        modelInputWidth = inputTensor.shape()[2]
        modelInputHeight = inputTensor.shape()[1]
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(modelInputHeight, modelInputWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        textureView = findViewById(R.id.textureView)
        captureButton = findViewById(R.id.capture_button)
        galleryButton = findViewById(R.id.gallery_button) // <-- Tìm nút chọn ảnh
        debugTextView = findViewById(R.id.debug_textview)

        imageView.visibility = View.GONE
        debugTextView.visibility = View.GONE

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean = false
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {}
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Sự kiện cho nút CHỤP
        captureButton.setOnClickListener {
            if (isResultScreen) {
                // Chức năng "Chụp lại" hoặc "Quay lại"
                backToInitialState()
            } else {
                // Chức năng "Chụp & Phân tích"
                if (textureView.bitmap == null) {
                    return@setOnClickListener
                }
                bitmap = textureView.bitmap!!
                processImage(bitmap)
            }
        }

        // Sự kiện cho nút CHỌN ẢNH
        galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }
    }

    private fun backToInitialState() {
        imageView.visibility = View.GONE
        debugTextView.visibility = View.GONE
        textureView.visibility = View.VISIBLE
        captureButton.text = "Chụp & Phân tích"
        isResultScreen = false
    }

    private fun processImage(image: Bitmap) {
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(image)
        tensorImage = imageProcessor.process(tensorImage)

        val outputShape = interpreter.getOutputTensor(0).shape()
        val outputBuffer = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32)
        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        val bestResult = yolov8TFLiteDetector(outputBuffer.floatArray, image.width, image.height)
        val mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        val debugInfo = StringBuilder()

        if (bestResult != null) {
            debugInfo.append("KẾT QUẢ TỐT NHẤT:\n")
            debugInfo.append("Lớp: ${labels[bestResult.classIndex]}, Điểm: ${"%.4f".format(bestResult.score)}\n")

            paint.color = colors[bestResult.classIndex % colors.size]
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            canvas.drawRect(bestResult.rectF, paint)

            paint.style = Paint.Style.FILL
            paint.color = colors[bestResult.classIndex % colors.size]
            canvas.drawRect(bestResult.rectF.left, bestResult.rectF.top - 40f, bestResult.rectF.right, bestResult.rectF.top, paint)

            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 30f
            canvas.drawText("${labels[bestResult.classIndex]} ${"%.2f".format(bestResult.score)}", bestResult.rectF.left + 5, bestResult.rectF.top - 10, paint)
        }

        imageView.setImageBitmap(mutableBitmap)
        if (debugInfo.isNotEmpty()) {
            debugTextView.text = debugInfo.toString()
        } else {
            debugTextView.text = "Không nhận diện được đối tượng nào (ngưỡng > 50%)"
        }
        debugTextView.visibility = View.VISIBLE

        imageView.visibility = View.VISIBLE
        textureView.visibility = View.GONE
        captureButton.text = "Quay lại"
        isResultScreen = true
    }

    private fun uriToBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(this.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
        }.copy(Bitmap.Config.ARGB_8888, true)
    }

    // ... (Các hàm còn lại giữ nguyên không đổi) ...
    private fun yolov8TFLiteDetector(output: FloatArray, imgWidth: Int, imgHeight: Int): Result? {
        val numClasses = labels.size
        val numBoxes = 8400

        var bestResult: Result? = null
        var maxScoreOverall = 0f

        for (i in 0 until numBoxes) {
            var maxScoreInBox = 0f
            var maxClassInBox = -1
            for (j in 0 until numClasses) {
                val score = output[i + (4 + j) * numBoxes]
                if (score > maxScoreInBox) {
                    maxScoreInBox = score
                    maxClassInBox = j
                }
            }

            if (maxScoreInBox > maxScoreOverall) {
                maxScoreOverall = maxScoreInBox
                val cx = output[i]
                val cy = output[i + numBoxes]
                val w = output[i + 2 * numBoxes]
                val h = output[i + 3 * numBoxes]

                val x_scale = imgWidth.toFloat() / modelInputWidth
                val y_scale = imgHeight.toFloat() / modelInputHeight
                val left = (cx - w / 2) * x_scale
                val top = (cy - h / 2) * y_scale
                val right = (cx + w / 2) * x_scale
                val bottom = (cy + h / 2) * y_scale
                bestResult = Result(maxClassInBox, maxScoreInBox, RectF(left, top, right, bottom))
            }
        }

        if (bestResult != null && bestResult.score > 0.5f) {
            return bestResult
        }
        return null
    }

    data class Result(val classIndex: Int, val score: Float, val rectF: RectF)

    override fun onDestroy() {
        super.onDestroy()
        interpreter.close()
    }

    @SuppressLint("MissingPermission")
    fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                val surfaceTexture = textureView.surfaceTexture
                if (surfaceTexture != null) {
                    val surface = Surface(surfaceTexture)
                    val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    captureRequest.addTarget(surface)
                    cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(p0: CameraCaptureSession) { p0.setRepeatingRequest(captureRequest.build(), null, null) }
                        override fun onConfigureFailed(p0: CameraCaptureSession) {}
                    }, handler)
                }
            }
            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    fun get_permission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            get_permission()
        }
    }
}
