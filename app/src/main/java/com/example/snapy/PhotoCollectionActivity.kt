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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.snapy.databinding.ActivityPhotoCollectionBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PhotoCollectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoCollectionBinding
    private lateinit var adapter: GridPhotoAdapter
    private var collectionType: String = ""
    private val photos = mutableListOf<Photo>()
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        // Get the collection type and photos from intent
        collectionType = intent.getStringExtra("type") ?: ""
        
        when (collectionType) {
            "liked" -> {
                photos.addAll(PhotoSwipeActivity.likedPhotos)
                setupRecyclerView()
                setupButtons()
                adapter.submitList(photos)
                binding.toolbar.title = "Liked Photos"
            }
            "disliked" -> {
                photos.addAll(PhotoSwipeActivity.dislikedPhotos)
                setupRecyclerView()
                setupButtons()
                adapter.submitList(photos)
                binding.toolbar.title = "Trash"
            }
            else -> {
                // Main entry: Load all gallery images
                setupRecyclerView()
                setupButtons()
                checkPermissionAndLoadImages()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Snapy"
        binding.toolbar.inflateMenu(R.menu.main_overflow_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_photo_swipe -> {
                    startActivity(Intent(this, PhotoSwipeActivity::class.java))
                    true
                }
                R.id.menu_categorization -> {
                    startActivity(Intent(this, AICategorizationActivity::class.java))
                    true
                }
                R.id.menu_collage -> {
                    startActivity(Intent(this, PhotoCollageActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun checkPermissionAndLoadImages() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        } else {
            loadGalleryImagesAndSetup()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadGalleryImagesAndSetup()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadGalleryImagesAndSetup() {
        CoroutineScope(Dispatchers.IO).launch {
            val loadedPhotos = loadGalleryImages()
            withContext(Dispatchers.Main) {
                photos.clear()
                photos.addAll(loadedPhotos)
                adapter.submitList(photos.toList())

                if (photos.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emptyStateText.text = "No photos found on device."
                } else {
                    binding.emptyStateText.visibility = View.GONE
                }
            }
        }
    }

    private fun loadGalleryImages(): List<Photo> {
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
        adapter = GridPhotoAdapter { photo ->
            val intent = Intent(this, ImageViewerActivity::class.java).apply {
                putExtra("imageUri", photo.imageUri.toString())  // send as String
            }
            startActivity(intent)
        }

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@PhotoCollectionActivity, 3)
            adapter = this@PhotoCollectionActivity.adapter
        }
    }


    private fun setupButtons() {
        binding.likedButtonsLayout.visibility = View.GONE
        binding.dislikedButtonsLayout.visibility = View.GONE

        when (collectionType) {
            "liked" -> {
                binding.likedButtonsLayout.visibility = View.VISIBLE
                binding.dislikedButtonsLayout.visibility = View.GONE

                // Setup Undo button
                binding.fabUndo.setOnClickListener {
                    if (photos.isNotEmpty()) {
                        val last = photos.removeAt(photos.size - 1)
                        PhotoSwipeActivity.likedPhotos.remove(last)
                        adapter.submitList(photos.toList())
                        Toast.makeText(this, "Moved back to gallery", Toast.LENGTH_SHORT).show()
                    }
                }

                // Setup Share button
                binding.fabShare.setOnClickListener {
                    sharePhotos()
                }
            }
            "disliked" -> {
                binding.likedButtonsLayout.visibility = View.GONE
                binding.dislikedButtonsLayout.visibility = View.VISIBLE

                // Setup Undo button
                binding.fabUndoDislike.setOnClickListener {
                    if (photos.isNotEmpty()) {
                        val last = photos.removeAt(photos.size - 1)
                        PhotoSwipeActivity.dislikedPhotos.remove(last)
                        adapter.submitList(photos.toList())
                        Toast.makeText(this, "Moved back to gallery", Toast.LENGTH_SHORT).show()
                    }
                }

                // Setup Delete button
                binding.fabDelete.setOnClickListener {
                    showDeleteConfirmationDialog()
                }
            }
        }
    }

    private fun sharePhotos() {
        try {
            if (photos.isEmpty()) {
                Toast.makeText(this, "No photos to share", Toast.LENGTH_SHORT).show()
                return
            }

            // Create a sharing intent for multiple images
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Create a list to hold the URIs
            val imageUris = ArrayList<Uri>()

            // Process each photo
            photos.forEach { photo ->
                photo.imageUri?.let { uri ->
                    imageUris.add(uri)
                }
            }

            if (imageUris.isEmpty()) {
                Toast.makeText(this, "No valid photos to share", Toast.LENGTH_SHORT).show()
                return
            }

            // Add the list of URIs to the intent
            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)

            // Start the sharing activity
            startActivity(Intent.createChooser(shareIntent, "Share Photos"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share photos: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Photos")
            .setMessage("Are you sure you want to delete these photos from the trash?")
            .setPositiveButton("Delete") { _, _ ->
                PhotoSwipeActivity.dislikedPhotos.clear()
                photos.clear()
                adapter.submitList(photos.toList())
                Toast.makeText(this, "Trash cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
