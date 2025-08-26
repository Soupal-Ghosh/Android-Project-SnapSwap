package com.example.snapy

import android.app.Dialog
import android.graphics.Color
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
import java.io.File
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
                    val photo = getItem(position)
                    onPhotoClick(photo) // callback in case you want to use it elsewhere

                    // Fullscreen dialog
                    val dialog = Dialog(itemView.context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                    val fullImageView = ImageView(itemView.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(Color.BLACK)
                    }

                    if (photo.imageUri != null) {
                        Glide.with(itemView.context).load(photo.imageUri).into(fullImageView)
                    } else if (photo.imageResId != 0) {
                        Glide.with(itemView.context).load(photo.imageResId).into(fullImageView)
                    }

                    fullImageView.setOnClickListener { dialog.dismiss() }
                    dialog.setContentView(fullImageView)
                    dialog.show()
                }
            }
        }

        fun bind(photo: Photo) {
            val options = RequestOptions().centerCrop().dontAnimate()

            // Load image into ImageView
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

            // Show date below photo
            val dateToShow = if (photo.dateTaken > 0L) {
                Date(photo.dateTaken)
            } else {
                val file = File(photo.imageUri?.path ?: "")
                Date(file.lastModified())
            }

            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            photoDateTextView.text = formatter.format(dateToShow)
        }
    }

    private class PhotoDiffCallback : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(oldItem: Photo, newItem: Photo) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Photo, newItem: Photo) = oldItem == newItem
    }
}
