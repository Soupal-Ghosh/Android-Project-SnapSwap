package com.example.snapy

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream

class CategoryAdapter(private val categories: Map<String, List<Uri>>) :
    RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryTitle: TextView = view.findViewById(R.id.categoryTitleTextView)
        val photosRecyclerView: RecyclerView = view.findViewById(R.id.photosRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories.keys.elementAt(position)
        val photos = categories[category] ?: emptyList()

        holder.categoryTitle.text = "$category (${photos.size} photos)"
        holder.photosRecyclerView.layoutManager = LinearLayoutManager(
            holder.itemView.context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        holder.photosRecyclerView.adapter = CategoryPhotoAdapter(photos)

        // Make the entire category card clickable
        holder.itemView.setOnClickListener {
            showCategoryPhotosDialog(holder.itemView.context, category, photos)
        }
    }

    override fun getItemCount() = categories.size

    private fun showCategoryPhotosDialog(context: android.content.Context, category: String, photos: List<Uri>) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_category_photos, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.titleTextView)
        val photosRecyclerView = dialogView.findViewById<RecyclerView>(R.id.photosRecyclerView)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)
        val btnShare = dialogView.findViewById<MaterialButton>(R.id.btnShare)

        titleTextView.text = "$category (${photos.size} photos)"
        photosRecyclerView.layoutManager = GridLayoutManager(context, 3)
        photosRecyclerView.adapter = CategoryPhotoAdapter(photos)

        btnShare.setOnClickListener {
            shareCategoryPhotos(context, category, photos)
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun shareCategoryPhotos(context: android.content.Context, category: String, photos: List<Uri>) {
        if (photos.isEmpty()) {
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_SUBJECT, "Photos from $category category")
            putExtra(Intent.EXTRA_TEXT, "Check out these photos from the $category category!")
            
            // Add all photo URIs to the intent
            val photoUris = ArrayList<Uri>()
            photos.forEach { uri ->
                try {
                    // Create a temporary file for each photo
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File.createTempFile("shared_photo_", ".jpg", context.cacheDir)
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream?.copyTo(outputStream)
                    }
                    
                    // Get URI for the temporary file using FileProvider
                    val photoUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        tempFile
                    )
                    photoUris.add(photoUri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, photoUris)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Photos"))
    }
} 