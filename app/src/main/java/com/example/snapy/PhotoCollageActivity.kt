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
import java.io.File
import java.io.FileOutputStream
import java.util.*

class PhotoCollageActivity : AppCompatActivity() {
    private lateinit var selectedPhotosRecyclerView: RecyclerView
    private lateinit var collageTypeEditText: TextInputEditText
    private lateinit var progressBar: ProgressBar
    private lateinit var collagePhotoAdapter: CollagePhotoAdapter
    private val selectedPhotos = mutableListOf<Uri>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.e("PhotoCollageActivity", "Permission result: $isGranted")
        if (isGranted) {
            Log.e("PhotoCollageActivity", "Permission granted, launching image picker")
            pickImagesLauncher.launch("image/*")
        } else {
            Log.e("PhotoCollageActivity", "Permission denied")
            Toast.makeText(this, "Permission required to select photos", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        Log.e("PhotoCollageActivity", "Received ${uris.size} photos from picker")
        if (uris.isNotEmpty()) {
            selectedPhotos.apply {
                clear()
                addAll(uris)
            }
            
            Log.e("PhotoCollageActivity", "Updated selectedPhotos list with ${selectedPhotos.size} photos")
            Log.e("PhotoCollageActivity", "Selected photos: $selectedPhotos")
            
            selectedPhotosRecyclerView.apply {
                visibility = View.VISIBLE
                collagePhotoAdapter.updatePhotos(selectedPhotos)
                requestLayout()
                
                postDelayed({
                    Log.e("PhotoCollageActivity", "RecyclerView item count: ${collagePhotoAdapter.itemCount}")
                    Log.e("PhotoCollageActivity", "RecyclerView visibility after update: $visibility")
                    Log.e("PhotoCollageActivity", "RecyclerView height after update: $height")
                    Log.e("PhotoCollageActivity", "RecyclerView width after update: $width")
                    Log.e("PhotoCollageActivity", "Selected photos count: ${selectedPhotos.size}")
                    Log.e("PhotoCollageActivity", "Selected photos: $selectedPhotos")
                }, 100)
            }
            
            Toast.makeText(this, "Selected ${uris.size} photos", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("PhotoCollageActivity", "No photos selected")
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
        Log.e("PhotoCollageActivity", "Views initialized")
    }

    private fun setupRecyclerView() {
        Log.e("PhotoCollageActivity", "Setting up RecyclerView")
        collagePhotoAdapter = CollagePhotoAdapter(selectedPhotos) { position ->
            Log.e("PhotoCollageActivity", "Removing photo at position $position")
            if (position in selectedPhotos.indices) {
                selectedPhotos.removeAt(position)
                collagePhotoAdapter.removePhoto(position)
                Log.e("PhotoCollageActivity", "Photo removed. New count: ${selectedPhotos.size}")
            }
        }

        selectedPhotosRecyclerView.apply {
            Log.e("PhotoCollageActivity", "RecyclerView visibility: $visibility")
            Log.e("PhotoCollageActivity", "RecyclerView height: $height")
            Log.e("PhotoCollageActivity", "RecyclerView width: $width")
            layoutManager = LinearLayoutManager(this@PhotoCollageActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = collagePhotoAdapter
            setHasFixedSize(false)
            visibility = View.VISIBLE
            Log.e("PhotoCollageActivity", "RecyclerView setup complete")
        }
    }

    private fun setupClickListeners() {
        findViewById<MaterialButton>(R.id.btnSelectPhotos).setOnClickListener {
            Log.e("PhotoCollageActivity", "Select photos button clicked")
            checkPermissionAndPickImages()
        }

        findViewById<MaterialButton>(R.id.btnCreateCollage).setOnClickListener {
            when {
                selectedPhotos.isEmpty() -> {
                    Log.e("PhotoCollageActivity", "No photos selected for collage")
                    Toast.makeText(this, "Please select at least one photo", Toast.LENGTH_SHORT).show()
                }
                collageTypeEditText.text.isNullOrEmpty() -> {
                    Log.e("PhotoCollageActivity", "No collage type entered")
                    Toast.makeText(this, "Please enter a collage type", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.e("PhotoCollageActivity", "Creating collage with ${selectedPhotos.size} photos")
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

        Log.e("PhotoCollageActivity", "Checking permission: $permission")
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                Log.e("PhotoCollageActivity", "Permission already granted, launching image picker")
                pickImagesLauncher.launch("image/*")
            }
            else -> {
                Log.e("PhotoCollageActivity", "Requesting permission: $permission")
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
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    showCollageResult(collageUri)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PhotoCollageActivity, "Error creating collage: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun generateQuote(collageType: String): String {
        val quotes = when (collageType.lowercase()) {
            "birthday" -> listOf(
                "🎉 Happy Birthday! May your day be filled with joy and laughter! 🎂",
                "🎈 Wishing you a fantastic birthday filled with amazing moments! 🎁",
                "✨ Another year older, another year wiser! Happy Birthday! 🎊"
            )
            "travel" -> listOf(
                "✈️ Adventure awaits! Making memories around the world 🌍",
                "🗺️ Exploring new places, creating unforgettable stories 🌟",
                "🌅 Life is an adventure, let's make it count! 🏔️"
            )
            "family" -> listOf(
                "👨‍👩‍👧‍👦 Family is where life begins and love never ends ❤️",
                "🏡 Home is wherever we are together 💕",
                "👪 Making memories with the ones we love most 💝"
            )
            else -> listOf(
                "✨ Making memories that last a lifetime 🌟",
                "📸 Capturing moments, creating memories 💫",
                "💫 Every picture tells a story 📸"
            )
        }
        return quotes.random()
    }

    private fun createCollageWithQuote(photos: List<Uri>, quote: String): Uri {
        Log.e("PhotoCollageActivity", "Creating collage with ${photos.size} photos")
        try {
            val collageBitmap = when (photos.size) {
                1 -> createSinglePhotoCollage(photos[0])
                2 -> createTwoPhotoCollage(photos[0], photos[1])
                3 -> createThreePhotoCollage(photos[0], photos[1], photos[2])
                4 -> createFourPhotoCollage(photos[0], photos[1], photos[2], photos[3])
                else -> createGridCollage(photos)
            }

            val finalBitmap = addQuoteToCollage(collageBitmap, quote)
            val collageFile = saveCollageToFile(finalBitmap)
            Log.e("PhotoCollageActivity", "Collage saved to: ${collageFile.absolutePath}")
            return Uri.fromFile(collageFile)
        } catch (e: Exception) {
            Log.e("PhotoCollageActivity", "Error creating collage: ${e.message}")
            throw e
        }
    }

    private fun createSinglePhotoCollage(photo: Uri): Bitmap = loadBitmapFromUri(photo)

    private fun createTwoPhotoCollage(photo1: Uri, photo2: Uri): Bitmap {
        val (bitmap1, bitmap2) = listOf(photo1, photo2).map { loadBitmapFromUri(it) }
        val collageBitmap = Bitmap.createBitmap(bitmap1.width, bitmap1.height, Bitmap.Config.ARGB_8888)
        Canvas(collageBitmap).apply {
            drawBitmap(bitmap1, 0f, 0f, null)
            drawBitmap(bitmap2, collageBitmap.width / 2f, 0f, null)
        }
        return collageBitmap
    }

    private fun createThreePhotoCollage(photo1: Uri, photo2: Uri, photo3: Uri): Bitmap {
        val (bitmap1, bitmap2, bitmap3) = listOf(photo1, photo2, photo3).map { loadBitmapFromUri(it) }
        val collageBitmap = Bitmap.createBitmap(bitmap1.width, bitmap1.height, Bitmap.Config.ARGB_8888)
        Canvas(collageBitmap).apply {
            drawBitmap(bitmap1, 0f, 0f, null)
            drawBitmap(bitmap2, collageBitmap.width / 2f, 0f, null)
            drawBitmap(bitmap3, collageBitmap.width / 4f, collageBitmap.height / 2f, null)
        }
        return collageBitmap
    }

    private fun createFourPhotoCollage(photo1: Uri, photo2: Uri, photo3: Uri, photo4: Uri): Bitmap {
        val (bitmap1, bitmap2, bitmap3, bitmap4) = listOf(photo1, photo2, photo3, photo4).map { loadBitmapFromUri(it) }
        val collageBitmap = Bitmap.createBitmap(bitmap1.width, bitmap1.height, Bitmap.Config.ARGB_8888)
        Canvas(collageBitmap).apply {
            drawBitmap(bitmap1, 0f, 0f, null)
            drawBitmap(bitmap2, collageBitmap.width / 2f, 0f, null)
            drawBitmap(bitmap3, 0f, collageBitmap.height / 2f, null)
            drawBitmap(bitmap4, collageBitmap.width / 2f, collageBitmap.height / 2f, null)
        }
        return collageBitmap
    }

    private fun createGridCollage(photos: List<Uri>): Bitmap {
        val bitmaps = photos.map { loadBitmapFromUri(it) }
        val gridSize = kotlin.math.ceil(kotlin.math.sqrt(photos.size.toDouble())).toInt()
        
        val collageBitmap = Bitmap.createBitmap(bitmaps[0].width, bitmaps[0].height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(collageBitmap)
        
        val (cellWidth, cellHeight) = collageBitmap.width / gridSize to collageBitmap.height / gridSize
        
        bitmaps.forEachIndexed { index, bitmap ->
            val row = index / gridSize
            val col = index % gridSize
            canvas.drawBitmap(bitmap, col * cellWidth.toFloat(), row * cellHeight.toFloat(), null)
        }
        
        return collageBitmap
    }

    private fun addQuoteToCollage(bitmap: Bitmap, quote: String): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(resultBitmap).apply {
            drawText(
                quote,
                bitmap.width / 2f,
                bitmap.height - 100f,
                Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 60f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(10f, 0f, 0f, android.graphics.Color.BLACK)
                }
            )
        }
        return resultBitmap
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            return BitmapFactory.decodeStream(inputStream)
        }
        throw IllegalStateException("Failed to load bitmap from URI: $uri")
    }

    private fun saveCollageToFile(bitmap: Bitmap): File {
        val fileName = "collage_${System.currentTimeMillis()}.jpg"
        val file = File(getExternalFilesDir(null), fileName)
        
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        
        return file
    }

    private fun showCollageResult(collageUri: Uri) {
        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                File(collageUri.path!!)
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Collage"))
            
            android.app.AlertDialog.Builder(this)
                .setTitle("Your Collage")
                .setMessage("Collage created successfully! You can share it using the share dialog.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        } catch (e: Exception) {
            Log.e("PhotoCollageActivity", "Error sharing collage: ${e.message}")
            Toast.makeText(this, "Error sharing collage: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
} 