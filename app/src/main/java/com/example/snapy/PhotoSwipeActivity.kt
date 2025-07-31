package com.example.snapy

import android.Manifest
import android.content.Intent
import android.content.IntentSender
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoSwipeActivity : AppCompatActivity() {

    private lateinit var onboardingContainer: LinearLayout
    private lateinit var line1: TextView
    private lateinit var line2: TextView
    private lateinit var line3: TextView
    private lateinit var line4: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabLikedPhotos: FloatingActionButton
    private lateinit var fabDislikedPhotos: FloatingActionButton
    private lateinit var fabAddPhoto: FloatingActionButton

    private var isAnimationFinished = false

    private val photos = mutableListOf<Photo>()
    private val likedPhotos = mutableListOf<Photo>()
    private val dislikedPhotos = mutableListOf<Photo>()
    private val galleryImages = mutableListOf<Uri>()

    private lateinit var photoAdapter: PhotoAdapter

    private val PERMISSION_REQUEST_CODE = 123
    private val REQUEST_LIKED_PHOTOS = 1
    private val REQUEST_DISLIKED_PHOTOS = 2
    private val DELETE_REQUEST_CODE = 1001

    private var pendingDeletePhoto: Photo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_swipe)

        onboardingContainer = findViewById(R.id.onboardingContainer)
        line1 = findViewById(R.id.line1)
        line2 = findViewById(R.id.line2)
        line3 = findViewById(R.id.line3)
        line4 = findViewById(R.id.line4)
        recyclerView = findViewById(R.id.recyclerView)
        fabLikedPhotos = findViewById(R.id.fabLikedPhotos)
        fabDislikedPhotos = findViewById(R.id.fabDislikedPhotos)
        fabAddPhoto = findViewById(R.id.fabAddPhoto)

        recyclerView.visibility = View.GONE
        fabLikedPhotos.visibility = View.GONE
        fabDislikedPhotos.visibility = View.GONE
        fabAddPhoto.visibility = View.GONE

        line1.text = "Here we categorise pictures which helps cleaning your gallery and file selection seamless with just a bunch of swipes"
        line2.text = "Click left to like and right to dislike and tap anywhere on the screen to begin !"
        line3.text = " *👍 stores liked images"
        line4.text = " *👎 stores disliked images"

        onboardingContainer.visibility = View.VISIBLE
        playOnboardingAnimation()

        onboardingContainer.setOnClickListener {
            if (isAnimationFinished) {
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

    private fun playOnboardingAnimation() {
        val animationDuration = 600L
        val delayIncrement = 200L
        val textViews = listOf(line1, line2, line3, line4)

        for ((index, textView) in textViews.withIndex()) {
            val isLast = index == textViews.size - 1
            textView.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(index * delayIncrement)
                .setDuration(animationDuration)
                .setInterpolator(OvershootInterpolator())
                .withEndAction {
                    if (isLast) isAnimationFinished = true
                }.start()
        }
    }

    private fun loadGalleryImagesAndSetup() {
        recyclerView.visibility = View.VISIBLE
        fabLikedPhotos.visibility = View.VISIBLE
        fabDislikedPhotos.visibility = View.VISIBLE
        fabAddPhoto.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            loadGalleryImages()
            withContext(Dispatchers.Main) {
                if (galleryImages.isEmpty()) {
                    Toast.makeText(this@PhotoSwipeActivity, "No images found in gallery", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                photos.clear()
                galleryImages.forEachIndexed { index, uri ->
                    photos.add(Photo(id = index + 1, imageUri = uri))
                }
                photoAdapter.submitList(photos.toList())
            }
        }
    }

    private suspend fun loadGalleryImages() {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.MIME_TYPE} LIKE ?"
        val selectionArgs = arrayOf("image/%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        galleryImages.clear()

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                galleryImages.add(contentUri)
            }
        }
    }

    private fun setupSwipeGestures() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
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
            pickImagesFromGallery()
        } else {
            Toast.makeText(this, "Permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickImagesFromGallery() {
        CoroutineScope(Dispatchers.IO).launch {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.MIME_TYPE} LIKE ?"
            val selectionArgs = arrayOf("image/%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val imageUris = mutableListOf<Uri>()

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    imageUris.add(contentUri)
                }
            }

            withContext(Dispatchers.Main) {
                if (imageUris.isEmpty()) {
                    Toast.makeText(this@PhotoSwipeActivity, "No images found", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                val dialogView = layoutInflater.inflate(R.layout.dialog_image_picker, null)
                val galleryRecyclerView = dialogView.findViewById<RecyclerView>(R.id.galleryRecyclerView)

                val adapter = GalleryAdapter { /* onSelectedPhotosChanged */ }

                galleryRecyclerView.apply {
                    layoutManager = LinearLayoutManager(this@PhotoSwipeActivity)
                    this.adapter = adapter
                }

                adapter.submitList(imageUris)

                AlertDialog.Builder(this@PhotoSwipeActivity)
                    .setTitle("Select Images")
                    .setView(dialogView)
                    .setPositiveButton("Done") { dialog, _ ->
                        val selectedPhotos = adapter.getSelectedPhotos()
                        selectedPhotos.forEach { uri ->
                            val newPhoto = Photo(photos.size + 1, imageUri = uri)
                            photos.add(newPhoto)
                        }
                        photoAdapter.submitList(photos.toList())
                        Toast.makeText(this@PhotoSwipeActivity, "${selectedPhotos.size} photos added", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }

    private fun showPhotoCollection(type: String, photoList: MutableList<Photo>, requestCode: Int) {
        val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
            putExtra("type", type)
            putExtra("photos", ArrayList(photoList))
        }
        startActivityForResult(intent, requestCode)
    }

    // Add your onActivityResult and SAF delete logic here (unchanged if already present)
}
