package com.example.snapy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GridPhotoAdapter(private val onPhotoClick: (Photo) -> Unit) :
    ListAdapter<Photo, GridPhotoAdapter.GridPhotoViewHolder>(GridPhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridPhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grid_photo, parent, false)
        return GridPhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: GridPhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GridPhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
            Glide.with(itemView.context)
                .load(photo.imageUri)
                .centerCrop()
                .into(photoImageView)
        }
    }

    private class GridPhotoDiffCallback : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean {
            return oldItem == newItem
        }
    }
} 