package com.example.snapy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class CollageActivity : AppCompatActivity() {
    private lateinit var collageImageView: ImageView
    private lateinit var photos: ArrayList<Photo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collage)

        collageImageView = findViewById(R.id.collageImageView)
        photos = intent.getParcelableArrayListExtra("photos") ?: ArrayList()

        createCollage()
    }

    private fun createCollage() {
        // Create a 2x2 grid collage
        val size = 800 // Size of the final collage
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        // Calculate grid dimensions
        val gridSize = 2
        val cellSize = size / gridSize

        // Draw photos in grid
        for (i in 0 until minOf(photos.size, gridSize * gridSize)) {
            val photo = photos[i]
            val bitmap = BitmapFactory.decodeResource(resources, photo.imageResId)
            
            // Calculate position in grid
            val row = i / gridSize
            val col = i % gridSize
            
            // Scale bitmap to fit cell
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                cellSize,
                cellSize,
                true
            )
            
            // Draw bitmap in grid cell
            canvas.drawBitmap(
                scaledBitmap,
                col * cellSize.toFloat(),
                row * cellSize.toFloat(),
                null
            )
        }

        // Save collage to file
        saveCollage(bitmap)
        
        // Display collage
        collageImageView.setImageBitmap(bitmap)
    }

    private fun saveCollage(bitmap: Bitmap) {
        try {
            val fileName = "collage_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, fileName)
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
} 