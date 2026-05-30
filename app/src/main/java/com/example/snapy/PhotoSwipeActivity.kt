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
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.io.File

class PhotoSwipeActivity : AppCompatActivity() {

    companion object {
        val likedPhotos = mutableListOf<Photo>()
        val dislikedPhotos = mutableListOf<Photo>()
        const val FOLDER_LIKED = "Favorites"
        const val FOLDER_DISLIKED = "Trash"
    }

    private lateinit var onboardingContainer: View
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
    private lateinit var repository: PhotoRepository

    private val PERMISSION_REQUEST_CODE = 123

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

        repository = PhotoRepository(this)
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
        photoAdapter = PhotoAdapter(
            onPhotoClick = { photo ->
                val index = photos.indexOfFirst { it.id == photo.id || it.imageUri == photo.imageUri }
                if (index != -1) {
                    PhotoViewerData.currentPhotos = photos
                    val intent = Intent(this, ImageViewerActivity::class.java).apply {
                        putExtra("startIndex", index)
                    }
                    startActivity(intent)
                }
            },
            onPhotoLongClick = { photo ->
                showPhotoOptionsBottomSheet(photo)
            }
        )
        recyclerView.adapter = photoAdapter
        photoAdapter.submitList(photos.toList())
        setupSwipeGestures()
    }

    private fun showPhotoOptionsBottomSheet(photo: Photo) {
        val dialog = BottomSheetDialog(this, R.style.Theme_Snapy_PopupOverlay)
        val view = layoutInflater.inflate(R.layout.dialog_photo_options, null)
        dialog.setContentView(view)

        val optionRestore = view.findViewById<View>(R.id.optionRestore)
        val optionRemove = view.findViewById<View>(R.id.optionRemove)
        val optionLike = view.findViewById<View>(R.id.optionLike)
        val optionDislike = view.findViewById<View>(R.id.optionDislike)
        val optionSend = view.findViewById<View>(R.id.optionSend)
        val optionDelete = view.findViewById<View>(R.id.optionDelete)

        // Reset visibility based on current status
        optionRestore.visibility = View.GONE
        optionRemove.visibility = View.GONE
        optionLike.visibility = View.GONE
        optionDislike.visibility = View.GONE
        optionSend.visibility = View.GONE

        if (photo.isLiked) {
            optionSend.visibility = View.VISIBLE
            optionDislike.visibility = View.VISIBLE
            optionRemove.visibility = View.VISIBLE
        } else if (photo.isDisliked) {
            optionRestore.visibility = View.VISIBLE
            optionRemove.visibility = View.VISIBLE
            optionLike.visibility = View.VISIBLE
        }

        optionRestore.setOnClickListener {
            photo.isLiked = false
            photo.isDisliked = false
            likedPhotos.remove(photo)
            dislikedPhotos.remove(photo)
            Toast.makeText(this, "Restored to gallery", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        optionRemove.setOnClickListener {
            if (photo.isLiked) likedPhotos.remove(photo)
            if (photo.isDisliked) dislikedPhotos.remove(photo)
            Toast.makeText(this, "Removed from section", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        optionLike.setOnClickListener {
            photo.isLiked = true
            photo.isDisliked = false
            if (!likedPhotos.contains(photo)) likedPhotos.add(photo)
            dislikedPhotos.remove(photo)
            Toast.makeText(this, "Moved to Liked", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        optionDislike.setOnClickListener {
            photo.isLiked = false
            photo.isDisliked = true
            likedPhotos.remove(photo)
            if (!dislikedPhotos.contains(photo)) dislikedPhotos.add(photo)
            Toast.makeText(this, "Moved to Disliked", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        optionSend.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, photo.imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Image"))
            dialog.dismiss()
        }

        optionDelete.setOnClickListener {
            deleteImageFromGallery(photo)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun deleteImageFromGallery(photo: Photo) {
        try {
            photo.imageUri?.let { uri ->
                contentResolver.delete(uri, null, null)
                photos.remove(photo)
                likedPhotos.remove(photo)
                dislikedPhotos.remove(photo)
                photoAdapter.submitList(photos.toList())
                Toast.makeText(this, "Deleted from gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                        photo.isDisliked = false
                        if (!likedPhotos.contains(photo)) likedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Liked", Toast.LENGTH_SHORT).show()
                        
                        CoroutineScope(Dispatchers.IO).launch {
                            val folderId = repository.getOrCreateFolderId(FOLDER_LIKED)
                            repository.addPhotosToFolder(folderId, listOf(photo.imageUri.toString()))
                        }
                    }
                    ItemTouchHelper.LEFT -> {
                        photo.isDisliked = true
                        photo.isLiked = false
                        if (!dislikedPhotos.contains(photo)) dislikedPhotos.add(photo)
                        Toast.makeText(this@PhotoSwipeActivity, "Disliked", Toast.LENGTH_SHORT).show()

                        CoroutineScope(Dispatchers.IO).launch {
                            val folderId = repository.getOrCreateFolderId(FOLDER_DISLIKED)
                            repository.addPhotosToFolder(folderId, listOf(photo.imageUri.toString()))
                        }
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
            val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
                putExtra("type", "liked")
            }
            startActivity(intent)
        }

        fabDislikedPhotos.setOnClickListener {
            val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
                putExtra("type", "disliked")
            }
            startActivity(intent)
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
