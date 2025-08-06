package com.example.snapy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import java.text.SimpleDateFormat
import java.util.*

class PhotoAdapter(
    private val onPhotoClick: (Photo) -> Unit
) : ListAdapter<Photo, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val photoImageView: ImageView = itemView.findViewById(R.id.photoImageView)
        private val photoDateTextView: TextView = itemView.findViewById(R.id.photoDateTextView)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPhotoClick(getItem(position))
                }
            }
        }

        fun bind(photo: Photo) {
            // Load image
            val options = RequestOptions()
                .centerCrop()
                .dontAnimate()

            if (photo.imageUri != null) {
                Glide.with(itemView.context)
                    .load(photo.imageUri)
                    .apply(options)
                    .into(photoImageView)
            } else if (photo.imageResId != 0) {
                Glide.with(itemView.context)
                    .load(photo.imageResId)
                    .apply(options)
                    .into(photoImageView)
            }

            // Format and set the date
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val formattedDate = formatter.format(Date(photo.dateTaken))
            photoDateTextView.text = formattedDate
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
