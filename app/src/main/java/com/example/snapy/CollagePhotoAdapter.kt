package com.example.snapy

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable

class CollagePhotoAdapter(
    private var photos: MutableList<Uri>,
    private val onRemoveClick: (Int) -> Unit
) : RecyclerView.Adapter<CollagePhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val photoImageView: ImageView = view.findViewById(R.id.photoImageView)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        Log.e("CollagePhotoAdapter", "Creating new ViewHolder")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collage_photo, parent, false)
        Log.e("CollagePhotoAdapter", "View inflated successfully")
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        if (position >= photos.size) {
            Log.e("CollagePhotoAdapter", "Invalid position: $position, photos size: ${photos.size}")
            return
        }
        
        val photo = photos[position]
        Log.e("CollagePhotoAdapter", "Binding photo at position $position: $photo")
        
        // Configure Glide options
        val options = RequestOptions()
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .override(800, 800)
            .dontTransform()

        // Load image using Glide
        Glide.with(holder.itemView.context)
            .load(photo)
            .apply(options)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e("CollagePhotoAdapter", "Failed to load image at position $position")
                    Log.e("CollagePhotoAdapter", "Error: ${e?.message}")
                    Log.e("CollagePhotoAdapter", "Model: $model")
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e("CollagePhotoAdapter", "Successfully loaded image at position $position")
                    Log.e("CollagePhotoAdapter", "Resource size: ${resource.intrinsicWidth}x${resource.intrinsicHeight}")
                    return false
                }
            })
            .into(holder.photoImageView)

        holder.btnRemove.setOnClickListener {
            Log.e("CollagePhotoAdapter", "Remove button clicked for position $position")
            onRemoveClick(position)
        }
    }

    override fun getItemCount(): Int {
        Log.e("CollagePhotoAdapter", "getItemCount called: ${photos.size}")
        return photos.size
    }

    fun updatePhotos(newPhotos: List<Uri>) {
        Log.e("CollagePhotoAdapter", "Updating photos. Old count: ${photos.size}, New count: ${newPhotos.size}")
        Log.e("CollagePhotoAdapter", "Old photos: $photos")
        Log.e("CollagePhotoAdapter", "New photos: $newPhotos")
        
        // Create a new list to avoid reference issues
        val updatedPhotos = ArrayList(newPhotos)
        photos.clear()
        photos.addAll(updatedPhotos)
        
        notifyDataSetChanged()
        Log.e("CollagePhotoAdapter", "Photos updated and adapter notified")
        Log.e("CollagePhotoAdapter", "Final photos list: $photos")
    }

    fun removePhoto(position: Int) {
        if (position in 0 until photos.size) {
            Log.e("CollagePhotoAdapter", "Removing photo at position $position")
            photos.removeAt(position)
            notifyItemRemoved(position)
            Log.e("CollagePhotoAdapter", "Photo removed. New count: ${photos.size}")
        } else {
            Log.e("CollagePhotoAdapter", "Invalid position for removal: $position")
        }
    }
} 