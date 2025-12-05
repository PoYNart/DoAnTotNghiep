// File: MainActivity.kt (PHIÊN BẢN ĐÃ SỬA LỖI CÚ PHÁP)
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
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    lateinit var labels: List<String>
    val colors = listOf(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.MAGENTA)
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
    private var isAdmin = false

    lateinit var captureButton: Button
    lateinit var galleryButton: Button
    lateinit var debugTextView: TextView
    private var isResultScreen = false

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.let { uri ->
                bitmap = uriToBitmap(uri)
                processImage(bitmap)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        checkUserRole()
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
        galleryButton = findViewById(R.id.gallery_button)
        debugTextView = findViewById(R.id.debug_textview)

        imageView.visibility = View.GONE
        debugTextView.visibility = View.GONE

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) { open_camera() }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean = false
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {}
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        captureButton.setOnClickListener {
            if (isResultScreen) {
                backToInitialState()
            } else {
                if (textureView.bitmap == null) return@setOnClickListener
                bitmap = textureView.bitmap!!
                processImage(bitmap)
            }
        }

        galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }
    }

    private fun checkUserRole() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    if (role == "admin") {
                        isAdmin = true
                        invalidateOptionsMenu()
                    }
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val adminMenuItem = menu?.findItem(R.id.menu_admin_panel)
        adminMenuItem?.isVisible = isAdmin
        val userEmailMenuItem = menu?.findItem(R.id.menu_user_email)
        val currentUser = auth.currentUser
        if (currentUser != null) {
            userEmailMenuItem?.title = "Xin chào, ${currentUser.email}"
        } else {
            userEmailMenuItem?.title = "Xin chào"
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            R.id.menu_logout -> {
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                true
            }
            R.id.menu_admin_panel -> {
                startActivity(Intent(this, AdminActivity::class.java))
                true
            }
            R.id.menu_change_password -> {
                showChangePasswordDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun backToInitialState() {
        imageView.visibility = View.GONE
        debugTextView.visibility = View.GONE
        textureView.visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.buttons_layout).visibility = View.VISIBLE
        galleryButton.visibility = View.VISIBLE
        captureButton.visibility = View.VISIBLE
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

        val allResults = yolov8TFLiteDetector(outputBuffer.floatArray, image.width, image.height)
        val mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val debugInfo = StringBuilder()

        if (allResults.isNotEmpty()) {
            debugInfo.append("Đã nhận diện được ${allResults.size} đối tượng:\n\n")

            for ((index, result) in allResults.withIndex()) {
                val objectNumber = index + 1
                debugInfo.append("${objectNumber}. Lớp: ${labels[result.classIndex]}, Điểm: ${"%.2f".format(result.score)}\n")

                paint.color = colors[result.classIndex % colors.size]
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                canvas.drawRect(result.rectF, paint)

                paint.style = Paint.Style.FILL
                canvas.drawRect(result.rectF.left, result.rectF.top - 40f, result.rectF.right, result.rectF.top, paint)

                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                paint.textSize = 30f
                val labelText = "${objectNumber}. ${labels[result.classIndex]}"
                canvas.drawText(labelText, result.rectF.left + 10, result.rectF.top - 10, paint)

                uploadImageAndSaveHistory(image, result)
            }
        } else {
            debugInfo.append("Không nhận diện được đối tượng nào (ngưỡng > 50%)")
        }

        imageView.setImageBitmap(mutableBitmap)
        debugTextView.text = debugInfo.toString()
        debugTextView.visibility = View.VISIBLE
        imageView.visibility = View.VISIBLE
        textureView.visibility = View.GONE
        findViewById<LinearLayout>(R.id.buttons_layout).visibility = View.VISIBLE
        galleryButton.visibility = View.GONE
        captureButton.text = "Quay lại"
        isResultScreen = true
    }

    private fun uploadImageAndSaveHistory(image: Bitmap, result: Result) {
        val userId = auth.currentUser?.uid ?: return
        val baos = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val data = baos.toByteArray()

        MediaManager.get().upload(data)
            .option("folder", "history_images/$userId/")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val imageUrl = resultData["secure_url"].toString()
                    saveHistoryToFirestore(imageUrl, result)
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    Toast.makeText(this@MainActivity, "Lỗi khi tải ảnh lên: ${error.description}", Toast.LENGTH_LONG).show()
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            }).dispatch()
    }

    private fun saveHistoryToFirestore(imageUrl: String, result: Result) {
        val userId = auth.currentUser?.uid ?: return
        val historyEntry = History(
            imageUrl = imageUrl,
            label = labels[result.classIndex],
            score = result.score,
            timestamp = Timestamp.now()
        )
        db.collection("users").document(userId)
            .collection("history")
            .add(historyEntry)
            .addOnSuccessListener {
                // Giữ im lặng để không hiển thị Toast nhiều lần
            }
            .addOnFailureListener {
                Toast.makeText(this, "Lỗi khi lưu lịch sử vào DB", Toast.LENGTH_SHORT).show()
            }
    } // <-- DẤU NGOẶC NHỌN BỊ THIẾU Ở ĐÂY

    private fun uriToBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(this.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
        }.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun yolov8TFLiteDetector(output: FloatArray, imgWidth: Int, imgHeight: Int): List<Result> {
        val numClasses = labels.size
        val numBoxes = 8400
        val results = mutableListOf<Result>()

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
            if (maxScoreInBox > 0.5f) {
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
                results.add(Result(maxClassInBox, maxScoreInBox, RectF(left, top, right, bottom)))
            }
        }
        return nonMaxSuppression(results, 0.45f)
    }

    private fun nonMaxSuppression(results: List<Result>, iouThreshold: Float): List<Result> {
        val sortedResults = results.sortedByDescending { it.score }
        val finalResults = mutableListOf<Result>()
        for (res1 in sortedResults) {
            var shouldAdd = true
            for (res2 in finalResults) {
                val x1 = max(res1.rectF.left, res2.rectF.left)
                val y1 = max(res1.rectF.top, res2.rectF.top)
                val x2 = min(res1.rectF.right, res2.rectF.right)
                val y2 = min(res1.rectF.bottom, res2.rectF.bottom)
                val intersection = max(0f, x2 - x1) * max(0f, y2 - y1)
                val area1 = (res1.rectF.right - res1.rectF.left) * (res1.rectF.bottom - res1.rectF.top)
                val area2 = (res2.rectF.right - res2.rectF.left) * (res2.rectF.bottom - res2.rectF.top)
                val union = area1 + area2 - intersection
                val iou = if (union > 0) intersection / union else 0f
                if (iou > iouThreshold) {
                    shouldAdd = false
                    break
                }
            }
            if (shouldAdd) {
                finalResults.add(res1)
            }
        }
        return finalResults
    }
    // Trong file MainActivity.kt, thay thế hàm cũ bằng hàm mới này

    // ===== HÀM HIỂN THỊ HỘP THOẠI ĐỔI MẬT KHẨU (ĐÃ SỬA LỖI) =====
    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null)
        val oldPasswordEditText = dialogView.findViewById<EditText>(R.id.et_old_password)
        val newPasswordEditText = dialogView.findViewById<EditText>(R.id.et_new_password)
        val confirmPasswordEditText = dialogView.findViewById<EditText>(R.id.et_confirm_password)

        // ===== SỬA ĐỔI CÁCH TẠO DIALOG Ở ĐÂY =====
        val dialog = AlertDialog.Builder(this)
            .setTitle("Đổi mật khẩu")
            .setView(dialogView)
            // Thêm một nút "Positive" với listener là null.
            // Điều này sẽ tạo ra nút nhưng không làm gì khi nhấn.
            .setPositiveButton("Xác nhận", null)
            .setNegativeButton("Hủy", null)
            .create()

        // GHI ĐÈ LÊN SỰ KIỆN ONCLICK CỦA NÚT "POSITIVE" SAU KHI DIALOG HIỂN THỊ
        // Điều này ngăn dialog tự động đóng lại
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val oldPassword = oldPasswordEditText.text.toString().trim()
                val newPassword = newPasswordEditText.text.toString().trim()
                val confirmPassword = confirmPasswordEditText.text.toString().trim()

                // --- Các bước kiểm tra ---
                if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener // Giữ hộp thoại lại
                }
                if (newPassword.length < 6) {
                    Toast.makeText(this, "Mật khẩu mới phải có ít nhất 6 ký tự.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener // Giữ hộp thoại lại
                }
                if (newPassword != confirmPassword) {
                    Toast.makeText(this, "Mật khẩu xác nhận không khớp.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener // Giữ hộp thoại lại
                }
                // ---------------------------------

                val user = auth.currentUser
                val email = user?.email

                if (user == null || email == null) {
                    Toast.makeText(this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener // Giữ hộp thoại lại
                }

                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, oldPassword)
                user.reauthenticate(credential)
                    .addOnCompleteListener { reauthTask ->
                        if (reauthTask.isSuccessful) {
                            user.updatePassword(newPassword)
                                .addOnCompleteListener { updateTask ->
                                    if (updateTask.isSuccessful) {
                                        Toast.makeText(this, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss() // <<< CHỈ ĐÓNG HỘP THOẠI KHI THÀNH CÔNG
                                    } else {
                                        Log.e("ChangePassword", "Lỗi khi cập nhật mật khẩu mới", updateTask.exception)
                                        Toast.makeText(this, "Lỗi khi cập nhật mật khẩu mới.", Toast.LENGTH_LONG).show()
                                    }
                                }
                        } else {
                            Log.e("ChangePassword", "Xác thực lại thất bại", reauthTask.exception)
                            Toast.makeText(this, "Mật khẩu cũ không chính xác.", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        dialog.show()
    }
// ================================================================

// ================================================================



    data class Result(val classIndex: Int, val score: Float, val rectF: RectF)

    override fun onDestroy() {
        super.onDestroy()
        if (::interpreter.isInitialized) {
            interpreter.close()
        }
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
