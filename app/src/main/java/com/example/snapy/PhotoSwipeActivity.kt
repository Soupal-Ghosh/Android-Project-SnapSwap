package com.example.snapy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*

class PhotoSwipeActivity : AppCompatActivity() {

    private lateinit var onboardingContainer: LinearLayout
    private lateinit var line1: TextView
    private lateinit var line2: TextView
    private lateinit var line3: TextView
    private lateinit var line4: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgressBar: android.widget.ProgressBar
    private lateinit var fabLikedPhotos: FloatingActionButton
    private lateinit var fabDislikedPhotos: FloatingActionButton
    private lateinit var fabAddPhoto: FloatingActionButton

    private var isOnboardingFinished = false

    private val photos = mutableListOf<Photo>()
    private val likedPhotos = mutableListOf<Photo>()
    private val dislikedPhotos = mutableListOf<Photo>()
    private val galleryImages = mutableListOf<Uri>()

    private lateinit var photoAdapter: PhotoAdapter

    private val PERMISSION_REQUEST_CODE = 123
    private val REQUEST_LIKED_PHOTOS = 1
    private val REQUEST_DISLIKED_PHOTOS = 2

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

        photoAdapter = PhotoAdapter { photo ->
            Toast.makeText(this, "Photo ${photo.id} clicked", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = photoAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        setupSwipeGestures()
        setupFABs()
        setupAddPhotoFAB()
    }

    private fun showTextOnboarding() {
        line1.text = "Here we categorise pictures which helps cleaning your gallery and file selection seamless with just a bunch of swipes"
        line2.text = "Click left to like and right to dislike and tap anywhere on the screen to begin !"
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
                    if (index == textViews.lastIndex) {
                        isOnboardingFinished = true
                    }
                }
                .start()
        }
    }

    private fun loadGalleryImagesAndSetup() {
        // Show loading indicator
        loadingProgressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        fabLikedPhotos.visibility = View.GONE
        fabDislikedPhotos.visibility = View.GONE
        fabAddPhoto.visibility = View.GONE

        // Check permission first
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission required to access gallery", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
            loadingProgressBar.visibility = View.GONE
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val loadedPhotos = loadGalleryImages()
            withContext(Dispatchers.Main) {
                loadingProgressBar.visibility = View.GONE
                
                if (loadedPhotos.isEmpty()) {
                    Toast.makeText(this@PhotoSwipeActivity, "No images found in gallery. Try adding photos from the + button.", Toast.LENGTH_LONG).show()
                    // Show the FABs even if no images are found, so user can add photos
                    fabLikedPhotos.visibility = View.VISIBLE
                    fabDislikedPhotos.visibility = View.VISIBLE
                    fabAddPhoto.visibility = View.VISIBLE
                    return@withContext
                }
                
                recyclerView.visibility = View.VISIBLE
                fabLikedPhotos.visibility = View.VISIBLE
                fabDislikedPhotos.visibility = View.VISIBLE
                fabAddPhoto.visibility = View.VISIBLE
                
                photos.clear()
                photos.addAll(loadedPhotos)
                photoAdapter.submitList(photos.toList())
            }
        }
    }

    private suspend fun loadGalleryImages(): List<Photo> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val selection = "${MediaStore.Images.Media.MIME_TYPE} LIKE ?"
        val selectionArgs = arrayOf("image/%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val loadedPhotos = mutableListOf<Photo>()
        val loadedUris = mutableListOf<Uri>()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())

                loadedUris.add(contentUri)

                val photo = Photo(
                    id = loadedPhotos.size + 1,
                    imageUri = contentUri,
                    dateTaken = dateTaken
                )
                loadedPhotos.add(photo)
            }
        }
        
        // Update the global galleryImages list
        galleryImages.clear()
        galleryImages.addAll(loadedUris)
        
        return loadedPhotos
    }


    private fun setupSwipeGestures() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val photo = photos[position]
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        photo.isDisliked = true
                        dislikedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Disliked", Toast.LENGTH_SHORT).show()
                    }
                    ItemTouchHelper.RIGHT -> {
                        photo.isLiked = true
                        likedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Liked", Toast.LENGTH_SHORT).show()
                    }
                }
                photos.removeAt(position)
                photoAdapter.submitList(photos.toList())
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun setupFABs() {
        fabLikedPhotos.setOnClickListener {
            fabLikedPhotos.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_down))
            fabLikedPhotos.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_up))
            showPhotoCollection("liked", likedPhotos, REQUEST_LIKED_PHOTOS)
        }

        fabDislikedPhotos.setOnClickListener {
            fabDislikedPhotos.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_down))
            fabDislikedPhotos.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_up))
            showPhotoCollection("disliked", dislikedPhotos, REQUEST_DISLIKED_PHOTOS)
        }
    }

    private fun setupAddPhotoFAB() {
        fabAddPhoto.setOnClickListener {
            val scaleDown = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_down)
            val rotate = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate)
            val scaleUp = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.scale_up)
            fabAddPhoto.startAnimation(scaleDown)
            fabAddPhoto.startAnimation(rotate)
            fabAddPhoto.startAnimation(scaleUp)
            checkPermissionAndPickImages()
        }
    }

    private fun checkPermissionAndPickImages() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImagesFromGallery()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Reload gallery images when permission is granted
            loadGalleryImagesAndSetup()
        } else {
            Toast.makeText(this, "Permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickImagesFromGallery() {
        CoroutineScope(Dispatchers.IO).launch {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN)
            val selection = "${MediaStore.Images.Media.MIME_TYPE} LIKE ?"
            val selectionArgs = arrayOf("image/%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val imageUris = mutableListOf<Pair<Uri, Long>>()  // Also keep dateTaken

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val date = cursor.getLong(dateColumn)
                    val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    imageUris.add(contentUri to date)
                }
            }

            withContext(Dispatchers.Main) {
                if (imageUris.isEmpty()) {
                    Toast.makeText(this@PhotoSwipeActivity, "No images found", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val dialogView = layoutInflater.inflate(R.layout.dialog_image_picker, null)
                val galleryRecyclerView = dialogView.findViewById<RecyclerView>(R.id.galleryRecyclerView)

                val flatUris = imageUris.map { it.first } // only Uri list
                val adapter = GalleryAdapter {}

                galleryRecyclerView.layoutManager = GridLayoutManager(this@PhotoSwipeActivity, 3)
                galleryRecyclerView.adapter = adapter
                adapter.submitList(flatUris)

                AlertDialog.Builder(this@PhotoSwipeActivity)
                    .setView(dialogView)
                    .setCancelable(false)
                    .setPositiveButton("Done") { dialog, _ ->
                        val selectedUris = adapter.getSelectedPhotos()

                        if (selectedUris.isNotEmpty()) {
                            photos.clear() // Only show selected
                            selectedUris.forEachIndexed { index, uri ->
                                val dateTaken = imageUris.find { it.first == uri }?.second ?: 0L
                                photos.add(Photo(id = index + 1, imageUri = uri, dateTaken = dateTaken))
                            }
                            photoAdapter.submitList(photos.toList())
                            Toast.makeText(this@PhotoSwipeActivity, "${selectedUris.size} photos added", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@PhotoSwipeActivity, "No images selected. Using gallery by default.", Toast.LENGTH_SHORT).show()
                            loadGalleryImagesAndSetup()
                        }

                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        loadGalleryImagesAndSetup()
                    }
                    .show()
            }
        }
    }


    private fun showPhotoCollection(type: String, photoList: MutableList<Photo>, requestCode: Int) {
        val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
            putExtra("type", type)
            putExtra("photos", ArrayList(photoList))
        }
        startActivity(intent)
    }
}