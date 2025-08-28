package com.example.snapy

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.io.File

class PhotoSwipeActivity : AppCompatActivity() {

    companion object {
        val likedPhotos = mutableListOf<Photo>()
        val dislikedPhotos = mutableListOf<Photo>()
    }

    private lateinit var onboardingContainer: LinearLayout
    private lateinit var line1: TextView
    private lateinit var line2: TextView
    private lateinit var line3: TextView
    private lateinit var line4: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var fabLikedPhotos: FloatingActionButton
    private lateinit var fabDislikedPhotos: FloatingActionButton
    private lateinit var fabAddPhoto: FloatingActionButton

    private var isOnboardingFinished = false
    private val photos = mutableListOf<Photo>()
    private lateinit var photoAdapter: PhotoAdapter

    private val PERMISSION_REQUEST_CODE = 123
    private val PICK_IMAGES_REQUEST_CODE = 456

    // Activity result launcher for picking multiple images
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            addSelectedImagesToPhotos(uris)
        } else {
            Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_swipe)

        onboardingContainer = findViewById(R.id.onboardingContainer)
        line1 = findViewById(R.id.line1)
        line2 = findViewById(R.id.line2)
        line3 = findViewById(R.id.line3)
        line4 = findViewById(R.id.line4)

        recyclerView = findViewById(R.id.recyclerView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        fabLikedPhotos = findViewById(R.id.fabLikedPhotos)
        fabDislikedPhotos = findViewById(R.id.fabDislikedPhotos)
        fabAddPhoto = findViewById(R.id.fabAddPhoto)

        recyclerView.visibility = View.GONE
        loadingProgressBar.visibility = View.GONE
        fabLikedPhotos.visibility = View.GONE
        fabDislikedPhotos.visibility = View.GONE
        fabAddPhoto.visibility = View.GONE
        onboardingContainer.visibility = View.VISIBLE

        showTextOnboarding()

        onboardingContainer.setOnClickListener {
            if (isOnboardingFinished) {
                onboardingContainer.visibility = View.GONE
                loadGalleryImagesAndSetup()
            }
        }
    }

    private fun showTextOnboarding() {
        line1.text = "Here we categorise pictures which helps cleaning your gallery and file selection seamless with just a bunch of swipes"
        line2.text = "Click left to dislike and right to like. Tap anywhere to begin!"
        line3.text = " *👍 stores liked images"
        line4.text = " *👎 stores disliked images"

        val textViews = listOf(line1, line2, line3, line4)
        val animationDuration = 600L
        val delayIncrement = 200L

        for ((index, textView) in textViews.withIndex()) {
            textView.alpha = 0f
            textView.translationY = 100f
            textView.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(index * delayIncrement)
                .setDuration(animationDuration)
                .setInterpolator(OvershootInterpolator())
                .withEndAction {
                    if (index == textViews.lastIndex) isOnboardingFinished = true
                }
                .start()
        }
    }

    private fun loadGalleryImagesAndSetup() {
        loadingProgressBar.visibility = View.VISIBLE

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
            loadingProgressBar.visibility = View.GONE
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val loadedPhotos = loadGalleryImages()
            withContext(Dispatchers.Main) {
                loadingProgressBar.visibility = View.GONE
                photos.clear()
                photos.addAll(loadedPhotos)
                setupRecyclerView()
                setupFABs()
            }
        }
    }

    private suspend fun loadGalleryImages(): List<Photo> {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val loadedPhotos = mutableListOf<Photo>()
        contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            var idCounter = 1
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val dateTaken = cursor.getLong(dateCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                loadedPhotos.add(Photo(id = idCounter++, imageUri = contentUri, dateTaken = dateTaken))
            }
        }
        return loadedPhotos
    }

    private fun setupRecyclerView() {
        recyclerView.visibility = View.VISIBLE
        recyclerView.layoutManager = LinearLayoutManager(this)
        photoAdapter = PhotoAdapter { /* fullscreen handled in adapter */ }
        recyclerView.adapter = photoAdapter
        photoAdapter.submitList(photos.toList())
        setupSwipeGestures()
    }

    private fun setupSwipeGestures() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val photo = photos[position]

                when (direction) {
                    ItemTouchHelper.RIGHT -> {
                        photo.isLiked = true
                        likedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Liked", Toast.LENGTH_SHORT).show()
                    }
                    ItemTouchHelper.LEFT -> {
                        photo.isDisliked = true
                        dislikedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Disliked", Toast.LENGTH_SHORT).show()
                    }
                }

                photos.removeAt(position)
                photoAdapter.submitList(photos.toList())
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun setupFABs() {
        fabLikedPhotos.visibility = View.VISIBLE
        fabDislikedPhotos.visibility = View.VISIBLE
        fabAddPhoto.visibility = View.VISIBLE

        fabLikedPhotos.setOnClickListener {
            startActivity(Intent(this, LikedPhotosActivity::class.java))
        }

        fabDislikedPhotos.setOnClickListener {
            startActivity(Intent(this, DislikedPhotosActivity::class.java))
        }

        fabAddPhoto.setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }
    }

    private fun addSelectedImagesToPhotos(uris: List<Uri>) {
        try {
            // Clear current photos and replace with selected ones
            photos.clear()

            val newPhotos = uris.mapIndexed { index, uri ->
                Photo(
                    id = index + 1,
                    imageUri = uri,
                    dateTaken = System.currentTimeMillis()
                )
            }

            photos.addAll(newPhotos)
            photoAdapter.submitList(photos.toList())
            Toast.makeText(this, "${newPhotos.size} images selected for swiping", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error adding images: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

}
