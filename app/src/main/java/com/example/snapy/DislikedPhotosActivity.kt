package com.example.snapy

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class DislikedPhotosActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var gridAdapter: GridPhotoAdapter
    private lateinit var listAdapter: PhotoAdapter
    private lateinit var undoFab: FloatingActionButton
    private lateinit var deleteFab: FloatingActionButton
    private lateinit var emptyStateText: android.widget.TextView

    private val dislikedPhotos get() = PhotoSwipeActivity.dislikedPhotos
    private val undoStack = ArrayDeque<Photo>()
    private var isGridMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_collection)

        recyclerView = findViewById(R.id.recyclerView)
        undoFab = findViewById(R.id.fabUndoDislike)
        deleteFab = findViewById(R.id.fabDelete)
        emptyStateText = findViewById(R.id.emptyStateText)

        // Show only disliked buttons, hide liked buttons
        findViewById<android.view.View>(R.id.dislikedButtonsLayout).visibility = android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.likedButtonsLayout).visibility = android.view.View.GONE

        // Initialize adapters
        gridAdapter = GridPhotoAdapter(
            onPhotoClick = { photo ->
                // Handle photo click for grid view
                showFullscreenDialog(photo)
            },
            onPhotoLongClick = { photo ->
                // Handle long press for deletion
                showDeleteConfirmationDialog(photo)
            }
        )
        
        listAdapter = PhotoAdapter { photo ->
            // Handle photo click for list view (already handled in adapter)
        }

        // Set initial layout
        setGridMode(true)

        updateAdapter()

        undoFab.setOnClickListener {
            undoLastDislikedPhoto()
        }

        deleteFab.setOnClickListener {
            deleteAllDislikedPhotos()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_photo_collection, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_view -> {
                isGridMode = !isGridMode
                setGridMode(isGridMode)
                item.setIcon(if (isGridMode) R.drawable.ic_list else R.drawable.ic_grid)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setGridMode(gridMode: Boolean) {
        // Add fade transition
        recyclerView.alpha = 0f
        recyclerView.animate().alpha(1f).setDuration(300).start()
        
        if (gridMode) {
            // Calculate optimal column count based on screen width
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val columnCount = (screenWidth / (120f * displayMetrics.density)).toInt().coerceAtLeast(2).coerceAtMost(4)
            
            recyclerView.layoutManager = GridLayoutManager(this, columnCount)
            recyclerView.adapter = gridAdapter
        } else {
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = listAdapter
        }
        updateAdapter()
    }

    private fun showFullscreenDialog(photo: Photo) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val fullImageView = android.widget.ImageView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        if (photo.imageUri != null) {
            com.bumptech.glide.Glide.with(this).load(photo.imageUri).into(fullImageView)
        } else if (photo.imageResId != 0) {
            com.bumptech.glide.Glide.with(this).load(photo.imageResId).into(fullImageView)
        }

        fullImageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(fullImageView)
        dialog.show()
    }

    private fun undoLastDislikedPhoto() {
        if (dislikedPhotos.isNotEmpty()) {
            dislikedPhotos.removeAt(dislikedPhotos.size - 1)
            updateAdapter()
            Snackbar.make(recyclerView, "Removed from disliked photos", Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(recyclerView, "No disliked photos to undo", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun deleteAllDislikedPhotos() {
        if (dislikedPhotos.isNotEmpty()) {
            val photoCount = dislikedPhotos.size
            dislikedPhotos.clear()
            updateAdapter()
            Snackbar.make(recyclerView, "$photoCount photos deleted", Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(recyclerView, "No photos to delete", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog(photo: Photo) {
        AlertDialog.Builder(this)
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to delete this photo?")
            .setPositiveButton("Delete") { _, _ ->
                dislikedPhotos.remove(photo)
                updateAdapter()
                Snackbar.make(recyclerView, "Photo deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateAdapter() {
        if (isGridMode) {
            gridAdapter.submitList(dislikedPhotos.toList())
        } else {
            listAdapter.submitList(dislikedPhotos.toList())
        }
        
        // Show/hide empty state
        if (dislikedPhotos.isEmpty()) {
            recyclerView.visibility = android.view.View.GONE
            emptyStateText.visibility = android.view.View.VISIBLE
        } else {
            recyclerView.visibility = android.view.View.VISIBLE
            emptyStateText.visibility = android.view.View.GONE
        }
    }
}
