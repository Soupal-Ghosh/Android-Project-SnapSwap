package com.example.snapy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.example.snapy.databinding.ActivityPhotoCollectionBinding
import java.io.File

class PhotoCollectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoCollectionBinding
    private lateinit var adapter: GridPhotoAdapter<Photo>
    private var collectionType: String = ""
    private val photos = mutableListOf<Photo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the collection type and photos from intent
        collectionType = intent.getStringExtra("type") ?: ""
        intent.getParcelableArrayListExtra<Photo>("photos")?.let { photos.addAll(it) }

        setupRecyclerView()
        setupButtons()
        adapter.submitList(photos)
    }

    private fun setupRecyclerView() {
        adapter = GridPhotoAdapter { photo ->
            val intent = Intent(this, PhotoSwipeActivity::class.java).apply {
                putExtra("photo_id", photo.id)
                putExtra("photo_uri", photo.imageUri.toString())
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
                    // Move photos back to main list
                    val intent = Intent().apply {
                        putExtra("action", "undo_like")
                        putExtra("photos", ArrayList(photos))
                    }
                    setResult(RESULT_OK, intent)
                    finish()
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
                    // Move photos back to main list
                    val intent = Intent().apply {
                        putExtra("action", "undo_dislike")
                        putExtra("photos", ArrayList(photos))
                    }
                    setResult(RESULT_OK, intent)
                    finish()
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
                    when {
                        uri.scheme == "content" -> {
                            // For content URIs, use the URI directly
                            imageUris.add(uri)
                        }
                        uri.scheme == "file" -> {
                            // For file URIs, use FileProvider
                            val file = File(uri.path ?: return@forEach)
                            val contentUri = FileProvider.getUriForFile(
                                this,
                                "${packageName}.fileprovider",
                                file
                            )
                            imageUris.add(contentUri)
                        }
                        else -> {
                            // For other URIs, try to use them directly
                            imageUris.add(uri)
                        }
                    }
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
            .setMessage("Are you sure you want to delete these photos?")
            .setPositiveButton("Delete") { _, _ ->
                // Delete photos
                val intent = Intent().apply {
                    putExtra("action", "delete")
                    putExtra("photos", ArrayList(photos))
                }
                setResult(RESULT_OK, intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
} 