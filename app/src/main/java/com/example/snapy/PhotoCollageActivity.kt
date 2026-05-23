package com.example.snapy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            selectedPhotos.addAll(uris)
            selectedPhotosRecyclerView.visibility = View.VISIBLE
            collagePhotoAdapter.updatePhotos(selectedPhotos)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_collage)

        setupViews()
        setupRecyclerView()
        setupClickListeners()
        
        // Handle photos passed from intent (e.g. from Gallery)
        intent.getParcelableArrayListExtra<Photo>("photos")?.let { photos ->
            val uris = photos.mapNotNull { it.imageUri }
            if (uris.isNotEmpty()) {
                selectedPhotos.addAll(uris)
                selectedPhotosRecyclerView.visibility = View.VISIBLE
                collagePhotoAdapter.updatePhotos(selectedPhotos)
            }
        }
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
                if (selectedPhotos.isEmpty()) {
                    selectedPhotosRecyclerView.visibility = View.GONE
                }
            }
        }

        selectedPhotosRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PhotoCollageActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = collagePhotoAdapter
        }
    }

    private fun setupClickListeners() {
        findViewById<MaterialButton>(R.id.btnSelectPhotos).setOnClickListener {
            checkPermissionAndPickImages()
        }

        findViewById<MaterialButton>(R.id.btnCreateCollage).setOnClickListener {
            if (selectedPhotos.isEmpty()) {
                Toast.makeText(this, "Please select at least one photo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createCollage(collageTypeEditText.text.toString())
        }
    }

    private fun checkPermissionAndPickImages() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImagesLauncher.launch("image/*")
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun createCollage(collageType: String) {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val quote = generateQuote(collageType)
                val finalBitmap = createCollageWithQuote(selectedPhotos, quote)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    CollagePreviewActivity.finalCollageBitmap = finalBitmap
                    startActivity(Intent(this@PhotoCollageActivity, CollagePreviewActivity::class.java))
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

    private fun createCollageWithQuote(photos: List<Uri>, quote: String): Bitmap {
        val collageBitmap = when (photos.size) {
            1 -> loadBitmapFromUri(photos[0])
            2 -> createSideBySide(photos[0], photos[1])
            else -> createGridCollage(photos.take(4))
        }
        return addQuote(collageBitmap, quote)
    }

    private fun createSideBySide(uri1: Uri, uri2: Uri): Bitmap {
        val bmp1 = loadBitmapFromUri(uri1)
        val bmp2 = loadBitmapFromUri(uri2)

        val width = bmp1.width + bmp2.width
        val height = maxOf(bmp1.height, bmp2.height)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bmp1, 0f, 0f, null)
        canvas.drawBitmap(bmp2, bmp1.width.toFloat(), (height - bmp2.height) / 2f, null)
        return result
    }

    private fun createGridCollage(photos: List<Uri>): Bitmap {
        val bitmaps = photos.map { loadBitmapFromUri(it) }
        val cellSize = 800
        val gridSize = 2
        val size = cellSize * gridSize
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.BLACK)
        
        for (i in bitmaps.indices) {
            val originalBmp = bitmaps[i]
            // Center crop scaling
            val scale = cellSize.toFloat() / minOf(originalBmp.width, originalBmp.height)
            val scaledWidth = (originalBmp.width * scale).toInt()
            val scaledHeight = (originalBmp.height * scale).toInt()
            val scaledBmp = Bitmap.createScaledBitmap(originalBmp, scaledWidth, scaledHeight, true)
            
            val cropX = (scaledWidth - cellSize) / 2
            val cropY = (scaledHeight - cellSize) / 2
            val finalCellBmp = Bitmap.createBitmap(scaledBmp, cropX, cropY, cellSize, cellSize)

            val row = i / gridSize
            val col = i % gridSize
            canvas.drawBitmap(finalCellBmp, (col * cellSize).toFloat(), (row * cellSize).toFloat(), null)
        }
        return result
    }

    private fun addQuote(bitmap: Bitmap, quote: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = bitmap.height / 15f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(10f, 0f, 0f, android.graphics.Color.BLACK)
        }
        canvas.drawText(quote, bitmap.width / 2f, bitmap.height - (bitmap.height / 20f), paint)
        return result
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Failed to decode bitmap from URI: $uri")
    }
}
