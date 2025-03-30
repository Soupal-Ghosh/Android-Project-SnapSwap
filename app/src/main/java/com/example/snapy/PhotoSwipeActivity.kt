package com.example.snapy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.animation.AnimationUtils
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoSwipeActivity : AppCompatActivity() {
    private lateinit var photoAdapter: PhotoAdapter
    private lateinit var galleryAdapter: GalleryAdapter
    private val photos = mutableListOf<Photo>()
    private val likedPhotos = mutableListOf<Photo>()
    private val dislikedPhotos = mutableListOf<Photo>()
    private val galleryImages = mutableListOf<Uri>()
    private val PERMISSION_REQUEST_CODE = 123
    private val REQUEST_LIKED_PHOTOS = 1
    private val REQUEST_DISLIKED_PHOTOS = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_swipe)

        // Initialize RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        photoAdapter = PhotoAdapter { photo ->
            // Handle photo click
            Toast.makeText(this, "Photo ${photo.id} clicked", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = photoAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Add example photos
        addExamplePhotos()

        // Setup swipe gestures
        setupSwipeGestures(recyclerView)

        // Setup FABs with animations
        setupFABs()
    }

    private fun setupFABs() {
        val fabAddPhoto = findViewById<FloatingActionButton>(R.id.fabAddPhoto)
        val fabLikedPhotos = findViewById<FloatingActionButton>(R.id.fabLikedPhotos)
        val fabDislikedPhotos = findViewById<FloatingActionButton>(R.id.fabDislikedPhotos)

        // Add photo FAB
        fabAddPhoto.setOnClickListener {
            // Scale down animation
            val scaleDown = AnimationUtils.loadAnimation(this, R.anim.scale_down)
            fabAddPhoto.startAnimation(scaleDown)
            
            // Rotate animation
            val rotate = AnimationUtils.loadAnimation(this, R.anim.rotate)
            fabAddPhoto.startAnimation(rotate)
            
            // Scale up animation
            val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
            fabAddPhoto.startAnimation(scaleUp)
            
            checkPermissionAndShowGallery()
        }

        // Liked photos FAB
        fabLikedPhotos.setOnClickListener {
            // Scale down animation
            val scaleDown = AnimationUtils.loadAnimation(this, R.anim.scale_down)
            fabLikedPhotos.startAnimation(scaleDown)
            
            // Scale up animation
            val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
            fabLikedPhotos.startAnimation(scaleUp)
            
            showLikedPhotos()
        }

        // Disliked photos FAB
        fabDislikedPhotos.setOnClickListener {
            // Scale down animation
            val scaleDown = AnimationUtils.loadAnimation(this, R.anim.scale_down)
            fabDislikedPhotos.startAnimation(scaleDown)
            
            // Scale up animation
            val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
            fabDislikedPhotos.startAnimation(scaleUp)
            
            showDislikedPhotos()
        }
    }

    private fun checkPermissionAndShowGallery() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            showImagePickerDialog()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showImagePickerDialog()
            } else {
                Toast.makeText(this, "Permission denied. Cannot access gallery.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addExamplePhotos() {
        // Add the 5 example photos
        for (i in 1..5) {
            val resourceId = resources.getIdentifier("image$i", "drawable", packageName)
            photos.add(Photo(i, imageResId = resourceId))
        }
        photoAdapter.submitList(photos.toList())
    }

    private fun setupSwipeGestures(recyclerView: RecyclerView) {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val photo = photos[position]

                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        // Dislike - swipe left
                        photo.isDisliked = true
                        dislikedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Disliked", Toast.LENGTH_SHORT).show()
                    }
                    ItemTouchHelper.RIGHT -> {
                        // Like - swipe right
                        photo.isLiked = true
                        likedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Liked", Toast.LENGTH_SHORT).show()
                    }
                }
                photos.removeAt(position)
                photoAdapter.submitList(photos.toList())
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                val itemView = viewHolder.itemView
                
                // Calculate alpha based on swipe distance
                val alpha = 1f - Math.abs(dX) / itemView.width
                itemView.alpha = alpha

                // Add color feedback based on swipe direction
                if (isCurrentlyActive) {
                    val paint = android.graphics.Paint()
                    paint.color = when {
                        dX > 0 -> android.graphics.Color.parseColor("#00C853") // Brighter green for like
                        dX < 0 -> android.graphics.Color.parseColor("#FF1744") // Brighter red for dislike
                        else -> android.graphics.Color.TRANSPARENT
                    }
                    // Increase opacity for more vibrant colors
                    paint.alpha = (Math.abs(dX) / itemView.width * 180).toInt()

                    // Draw background color
                    c.drawRect(
                        itemView.left.toFloat(),
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        paint
                    )

                    // Draw icon based on swipe direction
                    val iconSize = 120f // Increased icon size
                    val iconMargin = 60f // Increased margin
                    val icon = when {
                        dX > 0 -> resources.getDrawable(R.drawable.ic_thumb_up, theme)
                        dX < 0 -> resources.getDrawable(R.drawable.ic_thumb_down, theme)
                        else -> null
                    }

                    icon?.let {
                        val iconLeft = if (dX > 0) {
                            itemView.left + iconMargin
                        } else {
                            itemView.right - iconMargin - iconSize
                        }
                        val iconTop = itemView.top + (itemView.height - iconSize) / 2
                        it.setBounds(
                            iconLeft.toInt(),
                            iconTop.toInt(),
                            (iconLeft + iconSize).toInt(),
                            (iconTop + iconSize).toInt()
                        )
                        it.draw(c)
                    }
                }
            }
        }

        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)
    }

    private fun showImagePickerDialog() {
        // Clear previous images
        galleryImages.clear()
        
        // Load gallery images in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                loadGalleryImages()
                
                withContext(Dispatchers.Main) {
                    if (galleryImages.isEmpty()) {
                        Toast.makeText(this@PhotoSwipeActivity, "No images found in gallery", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val dialogView = layoutInflater.inflate(R.layout.dialog_image_picker, null)
                    val galleryRecyclerView = dialogView.findViewById<RecyclerView>(R.id.galleryRecyclerView)
                    
                    var dialog: AlertDialog? = null
                    
                    galleryAdapter = GalleryAdapter { selectedPhotos ->
                        // Add all selected photos to the list
                        selectedPhotos.forEach { uri ->
                            val newPhoto = Photo(photos.size + 1, imageUri = uri)
                            photos.add(newPhoto)
                        }
                        photoAdapter.submitList(photos.toList())
                        
                        // Dismiss the dialog after selection
                        dialog?.dismiss()
                        
                        // Show a confirmation message
                        Toast.makeText(this@PhotoSwipeActivity, "${selectedPhotos.size} photos added to swipe list", Toast.LENGTH_SHORT).show()
                    }
                    
                    galleryRecyclerView.apply {
                        layoutManager = GridLayoutManager(this@PhotoSwipeActivity, 3)
                        adapter = galleryAdapter
                    }
                    
                    galleryAdapter.submitList(galleryImages)
                    
                    dialog = AlertDialog.Builder(this@PhotoSwipeActivity)
                        .setView(dialogView)
                        .setPositiveButton("Done") { dialog, _ ->
                            val selectedPhotos = galleryAdapter.getSelectedPhotos()
                            if (selectedPhotos.isNotEmpty()) {
                                // Add all selected photos to the list
                                selectedPhotos.forEach { uri ->
                                    val newPhoto = Photo(photos.size + 1, imageUri = uri)
                                    photos.add(newPhoto)
                                }
                                photoAdapter.submitList(photos.toList())
                                Toast.makeText(this@PhotoSwipeActivity, "${selectedPhotos.size} photos added to swipe list", Toast.LENGTH_SHORT).show()
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhotoSwipeActivity, "Error loading gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun loadGalleryImages() {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.MIME_TYPE} LIKE ?"
        val selectionArgs = arrayOf("image/%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        
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
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                galleryImages.add(contentUri)
            }
        }
    }

    private fun showLikedPhotos() {
        val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
            putExtra("type", "liked")
            putExtra("photos", ArrayList(likedPhotos))
        }
        startActivityForResult(intent, REQUEST_LIKED_PHOTOS)
    }

    private fun showDislikedPhotos() {
        val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
            putExtra("type", "disliked")
            putExtra("photos", ArrayList(dislikedPhotos))
        }
        startActivityForResult(intent, REQUEST_DISLIKED_PHOTOS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_LIKED_PHOTOS -> {
                    when (data?.getStringExtra("action")) {
                        "undo_like" -> {
                            val photosToUndo = data.getParcelableArrayListExtra<Photo>("photos")
                            photosToUndo?.forEach { photo ->
                                photo.isLiked = false
                                likedPhotos.remove(photo)
                                photos.add(photo)
                            }
                            photoAdapter.submitList(photos.toList())
                            Toast.makeText(this, "Photos moved back to main list", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                REQUEST_DISLIKED_PHOTOS -> {
                    when (data?.getStringExtra("action")) {
                        "undo_dislike" -> {
                            val photosToUndo = data.getParcelableArrayListExtra<Photo>("photos")
                            photosToUndo?.forEach { photo ->
                                photo.isDisliked = false
                                dislikedPhotos.remove(photo)
                                photos.add(photo)
                            }
                            photoAdapter.submitList(photos.toList())
                            Toast.makeText(this, "Photos moved back to main list", Toast.LENGTH_SHORT).show()
                        }
                        "delete" -> {
                            val photosToDelete = data.getParcelableArrayListExtra<Photo>("photos")
                            photosToDelete?.forEach { photo ->
                                dislikedPhotos.remove(photo)
                            }
                            Toast.makeText(this, "Photos deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_LIKED_PHOTOS = 1
        private const val REQUEST_DISLIKED_PHOTOS = 2
    }
}
