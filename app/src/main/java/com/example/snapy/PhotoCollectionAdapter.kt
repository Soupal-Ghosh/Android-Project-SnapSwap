package com.example.snapy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class PhotoCollectionAdapter(
    private val onPhotoClick: (Photo) -> Unit
) : ListAdapter<Photo, PhotoCollectionAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_collection_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val photoImageView: ImageView = itemView.findViewById(R.id.photoImageView)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPhotoClick(getItem(position))
                }
            }
        }

        fun bind(photo: Photo) {
            val options = RequestOptions()
                .centerCrop()
                .dontAnimate()

            when {
                photo.imageUri != null -> {
                    Glide.with(itemView.context)
                        .load(photo.imageUri)
                        .apply(options)
                        .into(photoImageView)
                }
                photo.imageResId != 0 -> {
                    Glide.with(itemView.context)
                        .load(photo.imageResId)
                        .apply(options)
                        .into(photoImageView)
                }
            }
        }
    }

    private class PhotoDiffCallback : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean {
            return oldItem == newItem
        }
    }
} 