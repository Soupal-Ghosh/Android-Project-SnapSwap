package com.example.snapy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.snapy.databinding.ActivityYourFoldersBinding
import kotlinx.coroutines.launch

class YourFoldersActivity : AppCompatActivity() {
    private lateinit var binding: ActivityYourFoldersBinding
    private lateinit var repository: PhotoRepository
    private lateinit var adapter: FolderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityYourFoldersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PhotoRepository(this)
        setupToolbar()
        setupRecyclerView()
        observeFolders()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FolderAdapter(repository, 
            onFolderClick = { folder ->
                // Open folder photos
                val intent = Intent(this, FolderPhotosActivity::class.java).apply {
                    putExtra("folderId", folder.id)
                    putExtra("folderName", folder.name)
                }
                startActivity(intent)
            },
            onFolderLongClick = { folder ->
                showFolderOptions(folder)
            }
        )
        binding.rvYourFolders.layoutManager = GridLayoutManager(this, 2)
        binding.rvYourFolders.adapter = adapter
    }

    private fun observeFolders() {
        lifecycleScope.launch {
            repository.allFolders.collect { folders ->
                adapter.submitList(folders)
                binding.emptyFoldersText.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showFolderOptions(folder: Folder) {
        val options = arrayOf("Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle(folder.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(folder)
                    1 -> showDeleteConfirmation(folder)
                }
            }
            .show()
    }

    private fun showRenameDialog(folder: Folder) {
        val editText = EditText(this).apply {
            setText(folder.name)
            setSelection(folder.name.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Album")
            .setView(editText)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.updateFolder(folder.copy(name = newName))
                        Toast.makeText(this@YourFoldersActivity, "Album renamed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(folder: Folder) {
        AlertDialog.Builder(this)
            .setTitle("Delete Album")
            .setMessage("Are you sure you want to delete '${folder.name}'? Photos will remain in your gallery.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteFolder(folder)
                    Toast.makeText(this@YourFoldersActivity, "Album deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
