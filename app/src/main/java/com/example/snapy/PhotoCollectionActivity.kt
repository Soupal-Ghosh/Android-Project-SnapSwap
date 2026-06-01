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
    
    private val contentObserver = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            android.util.Log.d("SnapySync", "MediaStore changed, refreshing context: $collectionType")
            refreshCurrentView()
        }
    }

    private fun refreshCurrentView() {
        when (collectionType) {
            "liked" -> loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_LIKED)
            "disliked" -> loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_DISLIKED)
            "trash" -> loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_TRASH)
            "" -> loadGalleryImagesAndSetup()
            else -> loadPhotosFromDatabaseFolder(collectionType) // Custom albums
        }
    }

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
                binding.toolbar.title = "Disliked"
                loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_DISLIKED)
                setupRecyclerView()
                setupButtons()
            }
            "trash" -> {
                binding.toolbar.title = "Trash"
                loadPhotosFromDatabaseFolder(PhotoSwipeActivity.FOLDER_TRASH)
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
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
        // Ensure gallery is fresh when returning from viewer or cropper
        if (collectionType == "") {
            checkPermissionAndLoadImages()
        }
    }

    override fun onStop() {
        super.onStop()
        contentResolver.unregisterContentObserver(contentObserver)
    }

    private fun loadPhotosFromDatabaseFolder(folderName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val folderId = repository.getOrCreateFolderId(folderName)
            repository.getPhotosInFolder(folderId).collect { folderUriStrings ->
                val allPhotos = loadGalleryImages()
                val folderUriSet = folderUriStrings.toSet()
                
                // Map to real photo objects from MediaStore to get correct dates
                val filtered = allPhotos.filter { folderUriSet.contains(it.imageUri.toString()) }
                
                withContext(Dispatchers.Main) {
                    photos.clear()
                    photos.addAll(filtered)
                    displayPhotos(photos)
                    
                    binding.emptyStateText.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
                }
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
                R.id.menu_disliked -> {
                    val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
                        putExtra("type", "disliked")
                    }
                    startActivity(intent)
                    true
                }
                R.id.menu_trash -> {
                    val intent = Intent(this, PhotoCollectionActivity::class.java).apply {
                        putExtra("type", "trash")
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
        lifecycleScope.launch(Dispatchers.IO) {
            // First, load the base images from MediaStore
            val loadedPhotos = loadGalleryImages()

            // Then, observe the database for status updates (Favorites, Trash, etc)
            // We use getOrCreateFolderId to ensure system folders exist
            val likedFolderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_LIKED)
            val dislikedFolderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_DISLIKED)
            val trashFolderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_TRASH)

            // Combine MediaStore data with Database status updates
            repository.allFolders.collect {
                val likedUris = repository.getPhotoUrisInFolder(likedFolderId).toSet()
                val trashedUris = repository.getPhotoUrisInFolder(trashFolderId).toSet()
                val dislikedUris = repository.getPhotoUrisInFolder(dislikedFolderId).toSet()

                // Filter out trashed items and enrich the rest
                val filteredAndEnriched = loadedPhotos
                    .filter { !trashedUris.contains(it.imageUri.toString()) }
                    .map { photo ->
                        val uriStr = photo.imageUri.toString()
                        photo.copy(
                            isLiked = likedUris.contains(uriStr),
                            isDisliked = dislikedUris.contains(uriStr)
                        )
                    }

                withContext(Dispatchers.Main) {
                    photos.clear()
                    photos.addAll(filteredAndEnriched)
                    PhotoViewerData.currentPhotos = photos.toList()
                    displayPhotos(photos)
                    binding.emptyStateText.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun loadGalleryImages(): List<Photo> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val loadedPhotos = mutableListOf<Photo>()
        contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                
                // Date Fallback: Taken -> Modified -> Added
                var date = cursor.getLong(takenCol)
                if (date == 0L) {
                    // DATE_MODIFIED and DATE_ADDED are often in seconds, convert to ms
                    date = cursor.getLong(modifiedCol) * 1000
                }
                if (date == 0L) {
                    date = cursor.getLong(addedCol) * 1000
                }
                
                val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                loadedPhotos.add(Photo(id = id.toInt(), imageUri = contentUri, dateTaken = date))
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
        
        val title = if (collectionType == "trash") "Delete Permanently" else "Move to Trash"
        val message = if (collectionType == "trash") 
            "Are you sure you want to permanently delete these ${selectedPhotos.size} photos? This cannot be undone."
            else "Move ${selectedPhotos.size} photos to Trash?"

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(if (collectionType == "trash") "Delete" else "Move") { _, _ ->
                if (collectionType == "trash") {
                    requestBatchDelete(selectedPhotos)
                } else {
                    moveToTrash(selectedPhotos)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun moveToTrash(selectedPhotos: List<Photo>) {
        lifecycleScope.launch {
            try {
                val folderId = repository.getOrCreateFolderId(PhotoSwipeActivity.FOLDER_TRASH)
                repository.addPhotosToFolder(folderId, selectedPhotos.mapNotNull { it.imageUri?.toString() })
                Toast.makeText(this@PhotoCollectionActivity, "Moved to Trash", Toast.LENGTH_SHORT).show()
                loadGalleryImagesAndSetup()
            } catch (e: Exception) {
                Toast.makeText(this@PhotoCollectionActivity, "Failed to move to trash", Toast.LENGTH_SHORT).show()
            }
            actionMode?.finish()
        }
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
                    "trash" -> PhotoSwipeActivity.FOLDER_TRASH
                    else -> collectionType // If using custom name
                }
                
                val folderId = repository.getOrCreateFolderId(folderName)
                repository.removePhotosFromFolder(folderId, selectedPhotos.mapNotNull { it.imageUri?.toString() })
                
                val message = if (collectionType == "trash") "Restored to gallery" else "Removed from $folderName"
                Toast.makeText(this@PhotoCollectionActivity, message, Toast.LENGTH_SHORT).show()
                // Refresh is automatic due to flow collection
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
