package com.example.snapy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class PhotoCollageActivity : AppCompatActivity() {
    private lateinit var selectedPhotosRecyclerView: RecyclerView
    private lateinit var collageTypeEditText: TextInputEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var collagePhotoAdapter: CollagePhotoAdapter
    private val selectedPhotos = mutableListOf<Uri>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickImagesLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "Permission required to select photos", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotos.clear()
            selectedPhotos.addAll(uris)
            selectedPhotosRecyclerView.visibility = View.VISIBLE
            collagePhotoAdapter.updatePhotos(selectedPhotos)
            Toast.makeText(this, "Selected ${uris.size} photos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No photos selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_collage)

        setupViews()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupViews() {
        selectedPhotosRecyclerView = findViewById(R.id.selectedPhotosRecyclerView)
        collageTypeEditText = findViewById(R.id.collageTypeEditText)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupRecyclerView() {
        collagePhotoAdapter = CollagePhotoAdapter(selectedPhotos) { position ->
            if (position in selectedPhotos.indices) {
                selectedPhotos.removeAt(position)
                collagePhotoAdapter.removePhoto(position)
            }
        }

        selectedPhotosRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PhotoCollageActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = collagePhotoAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupClickListeners() {
        findViewById<MaterialButton>(R.id.btnSelectPhotos).setOnClickListener {
            checkPermissionAndPickImages()
        }

        findViewById<MaterialButton>(R.id.btnCreateCollage).setOnClickListener {
            when {
                selectedPhotos.isEmpty() -> {
                    Toast.makeText(this, "Please select at least one photo", Toast.LENGTH_SHORT).show()
                }
                collageTypeEditText.text.isNullOrEmpty() -> {
                    Toast.makeText(this, "Please enter a collage type", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    createCollage(collageTypeEditText.text.toString())
                }
            }
        }
    }

    private fun checkPermissionAndPickImages() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                pickImagesLauncher.launch("image/*")
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun createCollage(collageType: String) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val quote = generateQuote(collageType)
                val collageUri = createCollageWithQuote(selectedPhotos, quote)

                val collageBitmap = BitmapFactory.decodeFile(collageUri.path)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    CollagePreviewActivity.finalCollageBitmap = collageBitmap
                    val intent = Intent(this@PhotoCollageActivity, CollagePreviewActivity::class.java)
                    startActivity(intent)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PhotoCollageActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private fun generateQuote(collageType: String): String {
        val quotes = when (collageType.lowercase()) {
            "birthday" -> listOf("🎉 Happy Birthday!", "🎂 Enjoy your day!", "🎈 Wishing joy!")
            "travel" -> listOf("✈️ Let's travel!", "🌍 Adventure begins!", "🗺️ Explore more!")
            "family" -> listOf("❤️ Family forever", "👨‍👩‍👧‍👦 Together is home", "💝 Precious bonds")
            else -> listOf("📸 Making memories", "💫 Capturing life", "✨ Moments forever")
        }
        return quotes.random()
    }

    private fun createCollageWithQuote(photos: List<Uri>, quote: String): Uri {
        val collageBitmap = when (photos.size) {
            1 -> loadBitmapFromUri(photos[0])
            2 -> createSideBySide(photos[0], photos[1])
            3, 4 -> createGridCollage(photos.take(4))
            else -> {
                runOnUiThread {
                    Toast.makeText(this, "Only the first 4 photos will be used for the collage.", Toast.LENGTH_SHORT).show()
                }
                createGridCollage(photos.take(4))
            }
        }
        val finalBitmap = addQuote(collageBitmap, quote)
        return Uri.fromFile(saveBitmap(finalBitmap))
    }

    private fun createSideBySide(uri1: Uri, uri2: Uri): Bitmap {
        val bmp1 = loadBitmapFromUri(uri1)
        val bmp2 = loadBitmapFromUri(uri2)

        val width = bmp1.width + bmp2.width
        val height = maxOf(bmp1.height, bmp2.height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bmp1, 0f, 0f, null)
        canvas.drawBitmap(bmp2, bmp1.width.toFloat(), 0f, null)
        return result
    }

    private fun createGridCollage(photos: List<Uri>): Bitmap {
        // 2x2 grid
        val bitmaps = photos.mapNotNull {
            try { loadBitmapFromUri(it) } catch (e: Exception) { null }
        }
        val cellSize = 600
        val gridSize = 2
        val size = cellSize * gridSize
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK)
        for (i in bitmaps.indices) {
            val bmp = Bitmap.createScaledBitmap(bitmaps[i], cellSize, cellSize, true)
            val row = i / gridSize
            val col = i % gridSize
            canvas.drawBitmap(bmp, (col * cellSize).toFloat(), (row * cellSize).toFloat(), null)
        }
        return result
    }

    private fun addQuote(bitmap: Bitmap, quote: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 60f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(10f, 0f, 0f, android.graphics.Color.BLACK)
        }
        canvas.drawText(quote, bitmap.width / 2f, bitmap.height - 100f, paint)
        return result
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        contentResolver.openInputStream(uri)?.use {
            return BitmapFactory.decodeStream(it)
        }
        throw IllegalArgumentException("Failed to decode bitmap from URI: $uri")
    }

    private fun saveBitmap(bitmap: Bitmap): File {
        val file = File(getExternalFilesDir(null), "collage_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file
    }

    private suspend fun uploadImageToCloudinary(context: Context, uri: Uri): String {
        val cloudName = "dntby7zeo"
        val uploadPreset = "SNAPSWAP"

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open input stream")
        val fileBytes = inputStream.readBytes()
        inputStream.close()

        val mediaType = "image/jpeg".toMediaTypeOrNull()
            ?: throw Exception("Invalid media type")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", "image.jpg",
                RequestBody.create(mediaType, fileBytes)
            )
            .addFormDataPart("upload_preset", uploadPreset)
            .build()

        val request = Request.Builder()
            .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
            .post(requestBody)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Upload failed: ${response.code}")

        val responseBody = response.body?.string()
        if (responseBody == null) throw Exception("Empty response body")
        
        val json = JSONObject(responseBody)
        return json.getString("secure_url")
    }

    private fun showCollageResult(imageUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))
        startActivity(intent)

        android.app.AlertDialog.Builder(this)
            .setTitle("Your Collage")
            .setMessage("Uploaded to Cloudinary!\n\n$imageUrl")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
