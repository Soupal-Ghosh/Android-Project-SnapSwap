package com.example.snapy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView

class ViewerAdapter(
    private var photos: List<Photo>,
    private val onPhotoTap: () -> Unit
) : RecyclerView.Adapter<ViewerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        Glide.with(holder.photoView)
            .load(photo.imageUri ?: photo.imageResId)
            .into(holder.photoView)
        
        holder.photoView.setOnViewTapListener { _, _, _ -> onPhotoTap() }
    }

    override fun getItemCount(): Int = photos.size

    fun updatePhotos(newPhotos: List<Photo>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoView: PhotoView = view.findViewById(R.id.photoView)
    }
}
