package com.example.snapy

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CategoryPhotoAdapter(private val photoUris: List<Uri>) :
    RecyclerView.Adapter<CategoryPhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grid_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = photoUris[position]
        Glide.with(holder.itemView.context)
            .load(uri)
            .centerCrop()
            .into(holder.imageView)
    }

    override fun getItemCount() = photoUris.size
} 