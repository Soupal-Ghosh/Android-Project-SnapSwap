package com.example.snapy

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.example.snapy.databinding.ActivityPhotoCollectionBinding
import com.example.snapy.databinding.DialogAddToFolderBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoCollectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoCollectionBinding
    private lateinit var adapter: GridPhotoAdapter
    private lateinit var repository: PhotoRepository
    private var collectionType: String = ""
    private val photos = mutableListOf<Photo>()
    private val PERMISSION_REQUEST_CODE = 123
    private var actionMode: ActionMode? = null

    private val intentSenderLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Action completed", Toast.LENGTH_SHORT).show()
            loadGalleryImagesAndSetup()
        }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadGalleryImagesAndSetup()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PhotoRepository(this)
        setupToolbar()

        // Get the collection type and photos from intent
        collectionType = intent.getStringExtra("type") ?: ""
        
        when (collectionType) {
            "liked" -> {
                binding.toolbar.title = "Favorites"
                loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_LIKED)
                setupRecyclerView()
                setupButtons()
            }
            "disliked" -> {
                binding.toolbar.title = "Trash"
                loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_DISLIKED)
                setupRecyclerView()
                setupButtons()
            }
            else -> {
                // Main entry: Load all gallery images
                setupRecyclerView()
                setupButtons()
                checkPermissionAndLoadImages()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Ensure gallery is fresh when returning from viewer or cropper
        if (collectionType == "") {
            checkPermissionAndLoadImages()
        }
    }

    private fun loadPhotosFromDatabaseFolder(folderName: String) {
        lifecycleScope.launch {
            val folderId = repository.getOrCreateFolderId(folderName)
            repository.getPhotosInFolder(folderId).collect { uris ->
                // Map URIs to Photo objects
                // For simplicity, we create temporary Photo objects. 
                // In a production app, we'd query MediaStore for full metadata if needed.
                val folderPhotos = uris.mapIndexed { index, uriString ->
                    Photo(id = index, imageUri = Uri.parse(uriString))
                }
                photos.clear()
                photos.addAll(folderPhotos)
                displayPhotos(photos)
                
                binding.emptyStateText.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
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
                R.id.menu_favorites -> {
                    val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
                        putExtra("type", "liked")
                    }
                    startActivity(intent)
                    true
                }
                R.id.menu_your_folders -> {
                    startActivity(Intent(this, YourFoldersActivity::class.java))
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
            
            // Enrich with database status - OPTIMIZED
            val folderIdLiked = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_LIKED)
            val folderIdDisliked = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_DISLIKED)
            
            val likedUris = repository.getPhotoUrisInFolder(folderIdLiked).toSet()
            val dislikedUris = repository.getPhotoUrisInFolder(folderIdDisliked).toSet()
            
            loadedPhotos.forEach { photo ->
                val uriString = photo.imageUri?.toString() ?: return@forEach
                photo.isLiked = likedUris.contains(uriString)
                photo.isDisliked = dislikedUris.contains(uriString)
            }

            withContext(Dispatchers.Main) {
                photos.clear()
                photos.addAll(loadedPhotos)
                
                // Update singleton to ensure other activities see the latest data
                PhotoViewerData.currentPhotos = photos.toList()

                displayPhotos(photos)

                if (photos.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emptyStateText.text = "Quiet night in the gallery."
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
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val dateTaken = cursor.getLong(dateCol)
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                // Use the real MediaStore ID instead of a counter
                loadedPhotos.add(Photo(id = id.toInt(), imageUri = contentUri, dateTaken = dateTaken))
            }
        }
        return loadedPhotos
    }

    private fun setupRecyclerView() {
        adapter = GridPhotoAdapter(
            onPhotoClick = { photo ->
                // Filter current list to get ONLY photos (exclude headers) for the viewer
                val photoItems = adapter.currentList.filterIsInstance<GalleryItem.PhotoItem>().map { it.photo }
                val index = photoItems.indexOfFirst { it.id == photo.id || it.imageUri == photo.imageUri }
                
                if (index != -1) {
                    PhotoViewerData.currentPhotos = photoItems
                    val intent = Intent(this, ImageViewerActivity::class.java).apply {
                        putExtra("startIndex", index)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Could not find image in collection", Toast.LENGTH_SHORT).show()
                }
            },
            onPhotoLongClick = { photo ->
                startSelectionMode()
            },
            onSelectionChanged = { count ->
                updateActionModeTitle(count)
            }
        )

        val layoutManager = StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL)

        binding.recyclerView.apply {
            this.layoutManager = layoutManager
            this.adapter = this@PhotoCollectionActivity.adapter
        }
    }

    private fun startSelectionMode() {
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionModeCallback)
            adapter.setSelectionMode(true)
        }
    }

    private fun updateActionModeTitle(count: Int) {
        actionMode?.title = "$count Selected"
    }

    private fun displayPhotos(photosList: List<Photo>) {
        val groupedItems = groupPhotosByDate(photosList)
        adapter.submitList(groupedItems)
    }

    private fun groupPhotosByDate(photosList: List<Photo>): List<GalleryItem> {
        val sortedPhotos = photosList.sortedByDescending { it.dateTaken }
        val galleryItems = mutableListOf<GalleryItem>()
        
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val yesterday = today - 86400000
        val thisWeek = today - (86400000 * 7)

        var currentGroup = ""

        sortedPhotos.forEach { photo ->
            val dateTaken = photo.dateTaken
            val groupTitle = when {
                dateTaken >= today -> "Today"
                dateTaken >= yesterday -> "Yesterday"
                dateTaken >= thisWeek -> "This Week"
                else -> {
                    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    sdf.format(Date(dateTaken))
                }
            }

            if (groupTitle != currentGroup) {
                galleryItems.add(GalleryItem.Header(groupTitle))
                currentGroup = groupTitle
            }
            galleryItems.add(GalleryItem.PhotoItem(photo))
        }
        return galleryItems
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
                        val last = photos.last()
                        lifecycleScope.launch {
                            val folderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_LIKED)
                            repository.removePhotosFromFolder(folderId, listOf(last.imageUri.toString()))
                            Toast.makeText(this@PhotoCollectionActivity, "Removed from Favorites", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Setup Share button
                binding.fabShare.setOnClickListener {
                    sharePhotos(photos)
                }
            }
            "disliked" -> {
                binding.likedButtonsLayout.visibility = View.GONE
                binding.dislikedButtonsLayout.visibility = View.VISIBLE

                // Setup Undo button
                binding.fabUndoDislike.setOnClickListener {
                    if (photos.isNotEmpty()) {
                        val last = photos.last()
                        lifecycleScope.launch {
                            val folderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_DISLIKED)
                            repository.removePhotosFromFolder(folderId, listOf(last.imageUri.toString()))
                            Toast.makeText(this@PhotoCollectionActivity, "Restored to gallery", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Setup Delete button
                binding.fabDelete.setOnClickListener {
                    showDeleteConfirmationDialog(photos)
                }
            }
        }
    }

    private fun sharePhotos(selectedPhotos: List<Photo>) {
        try {
            if (selectedPhotos.isEmpty()) {
                Toast.makeText(this, "No photos to share", Toast.LENGTH_SHORT).show()
                return
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val imageUris = ArrayList<Uri>()
            selectedPhotos.forEach { photo ->
                photo.imageUri?.let { uri ->
                    imageUris.add(uri)
                }
            }

            if (imageUris.isEmpty()) {
                Toast.makeText(this, "No valid photos to share", Toast.LENGTH_SHORT).show()
                return
            }

            shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
            startActivity(Intent.createChooser(shareIntent, "Share Photos"))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share photos: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog(selectedPhotos: List<Photo>) {
        if (selectedPhotos.isEmpty()) return
        
        AlertDialog.Builder(this)
            .setTitle("Delete Photos")
            .setMessage("Are you sure you want to delete these ${selectedPhotos.size} photos from storage?")
            .setPositiveButton("Delete") { _, _ ->
                requestBatchDelete(selectedPhotos)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestBatchDelete(selectedPhotos: List<Photo>) {
        val uris = selectedPhotos.mapNotNull { it.imageUri }
        if (uris.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
            val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            intentSenderLauncher.launch(intentSenderRequest)
        } else {
            // Android 10 and below logic
            var successCount = 0
            selectedPhotos.forEach { photo ->
                try {
                    photo.imageUri?.let { uri ->
                        contentResolver.delete(uri, null, null)
                        successCount++
                    }
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        val intentSenderRequest = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                        intentSenderLauncher.launch(intentSenderRequest)
                        return@forEach
                    }
                }
            }
            if (successCount > 0) {
                Toast.makeText(this, "Deleted $successCount photos", Toast.LENGTH_SHORT).show()
                loadGalleryImagesAndSetup()
                actionMode?.finish()
            }
        }
    }

    private fun startCrop(photo: Photo) {
        val sourceUri = photo.imageUri ?: return
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra("uri", sourceUri.toString())
        }
        cropLauncher.launch(intent)
    }

    private fun showAddToFolderDialog(selectedPhotos: List<Photo>) {
        val dialog = BottomSheetDialog(this, R.style.Theme_Snapy_PopupOverlay)
        val dialogBinding = DialogAddToFolderBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        // Setup existing folders list
        val folderAdapter = FolderAdapter(repository,
            onFolderClick = { folder ->
                lifecycleScope.launch {
                    repository.addPhotosToFolder(folder.id, selectedPhotos.mapNotNull { it.imageUri.toString() })
                    Toast.makeText(this@PhotoCollectionActivity, "Added to ${folder.name}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    actionMode?.finish()
                }
            },
            onFolderLongClick = {}
        )
        dialogBinding.rvFolders.layoutManager = GridLayoutManager(this, 1)
        dialogBinding.rvFolders.adapter = folderAdapter

        lifecycleScope.launch {
            repository.allFolders.collect { folders ->
                folderAdapter.submitList(folders)
            }
        }

        dialogBinding.btnCreateNewFolder.setOnClickListener {
            showCreateFolderDialog(selectedPhotos)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCreateFolderDialog(selectedPhotos: List<Photo>) {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("New Album")
            .setMessage("Enter album name:")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val folderId = repository.createFolder(name)
                        repository.addPhotosToFolder(folderId, selectedPhotos.mapNotNull { it.imageUri.toString() })
                        Toast.makeText(this@PhotoCollectionActivity, "Album created and photos added", Toast.LENGTH_SHORT).show()
                        actionMode?.finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addToFavorites(selectedPhotos: List<Photo>) {
        lifecycleScope.launch {
            try {
                val folderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_LIKED)
                
                var addedCount = 0
                var removedCount = 0
                
                selectedPhotos.forEach { photo ->
                    val uri = photo.imageUri?.toString() ?: return@forEach
                    if (repository.isPhotoInFolder(folderId, uri)) {
                        repository.removePhotosFromFolder(folderId, listOf(uri))
                        removedCount++
                    } else {
                        repository.addPhotosToFolder(folderId, listOf(uri))
                        addedCount++
                    }
                }
                
                if (addedCount > 0 && removedCount > 0) {
                    Toast.makeText(this@PhotoCollectionActivity, "Favorites updated ($addedCount added, $removedCount removed)", Toast.LENGTH_SHORT).show()
                } else if (addedCount > 0) {
                    Toast.makeText(this@PhotoCollectionActivity, "$addedCount photos added to Favorites", Toast.LENGTH_SHORT).show()
                } else if (removedCount > 0) {
                    Toast.makeText(this@PhotoCollectionActivity, "$removedCount photos removed from Favorites", Toast.LENGTH_SHORT).show()
                }
                
                // Refresh if currently in liked view
                if (collectionType == "liked") {
                    loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_LIKED)
                }
                
            } catch (e: Exception) {
                Toast.makeText(this@PhotoCollectionActivity, "Error updating favorites: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            actionMode?.finish()
        }
    }

    private fun removeFromCurrentAlbum(selectedPhotos: List<Photo>) {
        if (collectionType == "") return
        
        lifecycleScope.launch {
            try {
                val folderName = when (collectionType) {
                    "liked" -> PhotoSwipeActivity.FOLDER_LIKED
                    "disliked" -> PhotoSwipeActivity.FOLDER_DISLIKED
                    else -> collectionType // If using custom name
                }
                
                val folderId = repository.getOrCreateFolderId(folderName)
                repository.removePhotosFromFolder(folderId, selectedPhotos.mapNotNull { it.imageUri?.toString() })
                
                Toast.makeText(this@PhotoCollectionActivity, "Removed from $folderName", Toast.LENGTH_SHORT).show()
                // The flow collection in loadPhotosFromDatabaseFolder will automatically refresh the UI
            } catch (e: Exception) {
                Toast.makeText(this@PhotoCollectionActivity, "Error removing from album", Toast.LENGTH_SHORT).show()
            }
            actionMode?.finish()
        }
    }

    private fun showPhotoDetails(photo: Photo) {
        val details = StringBuilder()
        photo.imageUri?.let { uri ->
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val dateIndex = it.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)

                    if (nameIndex != -1) details.append("Name: ${it.getString(nameIndex)}\n\n")
                    if (sizeIndex != -1) details.append("Size: ${it.getLong(sizeIndex) / 1024} KB\n\n")
                    if (dateIndex != -1) {
                        val date = Date(it.getLong(dateIndex))
                        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                        details.append("Date: ${sdf.format(date)}\n\n")
                    }
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Photo Details")
            .setMessage(details.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_selection, menu)
            
            // Context-aware menu visibility
            if (collectionType == "") {
                menu.findItem(R.id.action_remove_from_album)?.isVisible = false
            } else {
                menu.findItem(R.id.action_favorites)?.isVisible = false
                menu.findItem(R.id.action_add_to_folder)?.isVisible = false
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selected = adapter.getSelectedPhotos()
            return when (item.itemId) {
                R.id.action_share -> {
                    sharePhotos(selected)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmationDialog(selected)
                    true
                }
                R.id.action_collage -> {
                    val intent = Intent(this@PhotoCollectionActivity, PhotoCollageActivity::class.java).apply {
                        putParcelableArrayListExtra("photos", ArrayList(selected))
                    }
                    startActivity(intent)
                    mode.finish()
                    true
                }
                R.id.action_crop -> {
                    if (selected.size == 1) {
                        startCrop(selected[0])
                        mode.finish()
                    } else {
                        Toast.makeText(this@PhotoCollectionActivity, "Select one photo to crop", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_add_to_folder -> {
                    showAddToFolderDialog(selected)
                    true
                }
                R.id.action_favorites -> {
                    addToFavorites(selected)
                    true
                }
                R.id.action_remove_from_album -> {
                    removeFromCurrentAlbum(selected)
                    true
                }
                R.id.action_details -> {
                    if (selected.size == 1) {
                        showPhotoDetails(selected[0])
                    } else {
                        Toast.makeText(this@PhotoCollectionActivity, "Select one photo to see details", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_select_all -> {
                    adapter.selectAll()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.setSelectionMode(false)
            actionMode = null
        }
    }
}
