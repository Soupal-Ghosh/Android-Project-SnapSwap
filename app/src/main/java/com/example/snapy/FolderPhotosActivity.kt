package com.example.snapy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.snapy.databinding.ActivityFolderPhotosBinding
import kotlinx.coroutines.launch

class FolderPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFolderPhotosBinding
    private lateinit var repository: PhotoRepository
    private lateinit var adapter: SimpleGridAdapter
    private var folderId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PhotoRepository(this)
        folderId = intent.getLongExtra("folderId", -1)
        val folderName = intent.getStringExtra("folderName") ?: "Album"

        setupToolbar(folderName)
        setupRecyclerView()
        observePhotos()
    }

    private fun setupToolbar(name: String) {
        binding.toolbar.title = name
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SimpleGridAdapter(
            onPhotoClick = { uri ->
                val intent = Intent(this, ImageViewerActivity::class.java).apply {
                    putExtra("imageUri", uri.toString())
                }
                startActivity(intent)
            },
            onPhotoLongClick = { uri ->
                showPhotoOptions(uri)
            }
        )
        binding.rvFolderPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvFolderPhotos.adapter = adapter
    }

    private fun observePhotos() {
        lifecycleScope.launch {
            repository.getPhotosInFolder(folderId).collect { uris ->
                adapter.submitList(uris.map { Uri.parse(it) })
            }
        }
    }

    private fun showPhotoOptions(uri: Uri) {
        val options = arrayOf("Remove from Album")
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> removeFromFolder(uri)
                }
            }
            .show()
    }

    private fun removeFromFolder(uri: Uri) {
        lifecycleScope.launch {
            repository.removePhotosFromFolder(folderId, listOf(uri.toString()))
            Toast.makeText(this@FolderPhotosActivity, "Removed from album", Toast.LENGTH_SHORT).show()
        }
    }
}
