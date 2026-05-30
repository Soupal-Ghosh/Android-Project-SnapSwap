package com.example.snapy

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.example.snapy.databinding.ActivityImageViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding
    private lateinit var adapter: ViewerAdapter
    private lateinit var repository: PhotoRepository
    private var photosList = mutableListOf<Photo>()
    private var isUiVisible = true

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Success", Toast.LENGTH_SHORT).show()
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // When returning from crop, the global data has been updated by PhotoCollectionActivity (onStart)
            // or we need to update it here.
            // Actually, the most reliable way is to let the viewer activity reload the photos
            // from the singleton which was updated by the caller.
            refreshPhotosAndUI()
        }
    }

    private fun refreshPhotosAndUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Clear Glide memory cache is done on main thread, but disk cache on IO
            // Actually, we can just use signature in Glide to avoid full cache clear
            withContext(Dispatchers.Main) {
                photosList.clear()
                photosList.addAll(PhotoViewerData.currentPhotos)
                if (photosList.isEmpty()) {
                    finish()
                } else {
                    adapter.updatePhotos(photosList)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityImageViewerBinding.inflate(layoutInflater)
            setContentView(binding.root)

            repository = PhotoRepository(this)
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayShowTitleEnabled(false)
            binding.toolbar.setNavigationOnClickListener { finish() }

            val startIndex = intent.getIntExtra("startIndex", 0)
            
            // Critical check for singleton data
            if (PhotoViewerData.currentPhotos.isEmpty()) {
                Toast.makeText(this, "Image library is loading, please wait", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            
            photosList.clear()
            photosList.addAll(PhotoViewerData.currentPhotos)

            // Clamp startIndex to prevent crashes
            val safeStartIndex = if (startIndex in 0 until photosList.size) startIndex else 0

            setupViewPager(safeStartIndex)
            setupActions()
            
            // Initial favorite check
            updateFavoriteButtonState(photosList[safeStartIndex])
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open viewer: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViewPager(startIndex: Int) {
        adapter = ViewerAdapter(photosList) {
            toggleUiVisibility()
        }
        binding.viewPager.adapter = adapter
        binding.viewPager.setCurrentItem(startIndex, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateFavoriteButtonState(photosList[position])
            }
        })
    }

    private fun setupActions() {
        binding.btnCrop.setOnClickListener {
            val currentPhoto = photosList[binding.viewPager.currentItem]
            startCrop(currentPhoto)
        }

        binding.btnFavorite.setOnClickListener {
            val currentPhoto = photosList[binding.viewPager.currentItem]
            toggleFavorite(currentPhoto)
        }

        binding.btnShare.setOnClickListener {
            val currentPhoto = photosList[binding.viewPager.currentItem]
            sharePhoto(currentPhoto)
        }

        binding.btnDelete.setOnClickListener {
            val currentPhoto = photosList[binding.viewPager.currentItem]
            showDeleteConfirmation(currentPhoto)
        }
    }

    private fun toggleFavorite(photo: Photo) {
        lifecycleScope.launch {
            try {
                val folderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_LIKED)
                val photoUriString = photo.imageUri.toString()
                
                val isFavorite = repository.isPhotoInFolder(folderId, photoUriString)
                
                if (isFavorite) {
                    repository.removePhotosFromFolder(folderId, listOf(photoUriString))
                    Toast.makeText(this@ImageViewerActivity, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                } else {
                    repository.addPhotosToFolder(folderId, listOf(photoUriString))
                    Toast.makeText(this@ImageViewerActivity, "Added to Favorites", Toast.LENGTH_SHORT).show()
                }
                
                updateFavoriteButtonState(photo)
            } catch (e: Exception) {
                Toast.makeText(this@ImageViewerActivity, "Failed to update favorite", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFavoriteButtonState(photo: Photo) {
        lifecycleScope.launch {
            try {
                val folderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_LIKED)
                val isFavorite = repository.isPhotoInFolder(folderId, photo.imageUri.toString())
                
                withContext(Dispatchers.Main) {
                    if (isFavorite) {
                        binding.btnFavorite.setImageResource(R.drawable.ic_favorite_filled)
                        binding.btnFavorite.setColorFilter(getColor(R.color.lavender_pink))
                    } else {
                        binding.btnFavorite.setImageResource(R.drawable.ic_favorite_outline)
                        binding.btnFavorite.setColorFilter(getColor(R.color.cat_text))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun toggleUiVisibility() {
        isUiVisible = !isUiVisible
        if (isUiVisible) {
            binding.appBar.animate().translationY(0f).setDuration(200).start()
            binding.bottomActionsBar.animate().alpha(1f).setDuration(200).start()
        } else {
            binding.appBar.animate().translationY(-binding.appBar.height.toFloat()).setDuration(200).start()
            binding.bottomActionsBar.animate().alpha(0f).setDuration(200).start()
        }
    }

    private fun startCrop(photo: Photo) {
        val uri = photo.imageUri ?: return
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra("uri", uri.toString())
        }
        cropLauncher.launch(intent)
    }

    private fun sharePhoto(photo: Photo) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, photo.imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Memory"))
    }

    private fun showDeleteConfirmation(photo: Photo) {
        AlertDialog.Builder(this)
            .setTitle("Delete Photo")
            .setMessage("Are you sure you want to delete this photo from device?")
            .setPositiveButton("Delete") { _, _ ->
                deletePhoto(photo)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePhoto(photo: Photo) {
        try {
            photo.imageUri?.let { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                    val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    intentSenderLauncher.launch(intentSenderRequest)
                    // Note: Local list update will happen in loadGalleryImagesAndSetup if using onActivityResult,
                    // but here we just finish or move next after confirm.
                } else {
                    contentResolver.delete(uri, null, null)
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    
                    val currentPos = binding.viewPager.currentItem
                    photosList.removeAt(currentPos)
                    if (photosList.isEmpty()) {
                        finish()
                    } else {
                        adapter.updatePhotos(photosList)
                    }
                }
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                val intentSenderRequest = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                intentSenderLauncher.launch(intentSenderRequest)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
