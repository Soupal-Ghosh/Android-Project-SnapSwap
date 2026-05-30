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
import android.graphics.Color

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
    private fun createGalleryStyleCollage(
        photos: List<Uri>
    ): Bitmap {

        val size = 3600
        val spacing = 4

        val result =
            Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888
            )

        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        when (photos.size) {

            1 -> {

                drawUriPhoto(
                    canvas,
                    photos[0],
                    0,
                    0,
                    size,
                    size
                )
            }

            2 -> {

                val w = (size - spacing) / 2

                drawUriPhoto(canvas, photos[0], 0, 0, w, size)

                drawUriPhoto(
                    canvas,
                    photos[1],
                    w + spacing,
                    0,
                    w,
                    size
                )
            }

            3 -> {

                val topHeight = (size - spacing) / 2
                val bottomWidth = (size - spacing) / 2

                drawUriPhoto(
                    canvas,
                    photos[0],
                    0,
                    0,
                    size,
                    topHeight
                )

                drawUriPhoto(
                    canvas,
                    photos[1],
                    0,
                    topHeight + spacing,
                    bottomWidth,
                    topHeight
                )

                drawUriPhoto(
                    canvas,
                    photos[2],
                    bottomWidth + spacing,
                    topHeight + spacing,
                    bottomWidth,
                    topHeight
                )
            }

            4 -> {

                val cell = (size - spacing) / 2

                for (i in 0 until 4) {

                    val row = i / 2
                    val col = i % 2

                    drawUriPhoto(
                        canvas,
                        photos[i],
                        col * (cell + spacing),
                        row * (cell + spacing),
                        cell,
                        cell
                    )
                }
            }

            else -> {

                val columns = when {
                    photos.size <= 6 -> 3
                    photos.size <= 12 -> 4
                    else -> 5
                }

                val rows =
                    kotlin.math.ceil(
                        photos.size / columns.toDouble()
                    ).toInt()

                val normalCellWidth =
                    (size - spacing * (columns - 1)) / columns

                val cellHeight =
                    (size - spacing * (rows - 1)) / rows

                for (row in 0 until rows) {

                    val startIndex = row * columns
                    val endIndex =
                        minOf(startIndex + columns, photos.size)

                    val itemsInThisRow =
                        endIndex - startIndex

                    val cellWidth =
                        (size - spacing * (itemsInThisRow - 1))/ itemsInThisRow

                    for (i in startIndex until endIndex) {

                        val col = i - startIndex

                        drawUriPhoto(
                            canvas,
                            photos[i],
                            col * (cellWidth + spacing),
                            row * (cellHeight + spacing),
                            cellWidth,
                            cellHeight
                        )
                    }
                }
            }
            }

        return result
    }

    private fun drawUriPhoto(
        canvas: Canvas,
        uri: Uri,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {

        val bitmap = loadBitmapFromUri(uri)

        val cropped =
            cropCenter(
                bitmap,
                width,
                height
            )

        canvas.drawBitmap(
            cropped,
            x.toFloat(),
            y.toFloat(),
            null
        )
    }

    private fun cropCenter(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {

        val scale =
            maxOf(
                targetWidth.toFloat() / bitmap.width,
                targetHeight.toFloat() / bitmap.height
            )

        val scaledWidth =
            (bitmap.width * scale).toInt()

        val scaledHeight =
            (bitmap.height * scale).toInt()

        val scaled =
            Bitmap.createScaledBitmap(
                bitmap,
                scaledWidth,
                scaledHeight,
                true
            )

        val cropX =
            (scaledWidth - targetWidth) / 2

        val cropY =
            (scaledHeight - targetHeight) / 2

        return Bitmap.createBitmap(
            scaled,
            cropX,
            cropY,
            targetWidth,
            targetHeight
        )
    }

    private fun createCollage(collageType: String) {

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val finalBitmap =
                    createGalleryStyleCollage(selectedPhotos)

                withContext(Dispatchers.Main) {

                    progressBar.visibility = View.GONE

                    CollagePreviewActivity.finalCollageBitmap =
                        finalBitmap

                    startActivity(
                        Intent(
                            this@PhotoCollageActivity,
                            CollagePreviewActivity::class.java
                        )
                    )
                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    progressBar.visibility = View.GONE

                    Toast.makeText(
                        this@PhotoCollageActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Failed to decode bitmap from URI: $uri")
    }
}
