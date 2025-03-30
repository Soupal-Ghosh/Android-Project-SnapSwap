package com.example.snapy

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

class GalleryAdapter(
    private val onPhotosSelected: (List<Uri>) -> Unit
) : ListAdapter<Uri, GalleryAdapter.GalleryViewHolder>(GalleryDiffCallback()) {

    private val selectedPhotos = mutableSetOf<Uri>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return GalleryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GalleryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val galleryImageView: ImageView = itemView.findViewById(R.id.galleryImageView)
        private val checkmarkView: View = itemView.findViewById(R.id.checkmarkView)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val uri = getItem(position)
                    if (selectedPhotos.contains(uri)) {
                        selectedPhotos.remove(uri)
                        checkmarkView.visibility = View.GONE
                    } else {
                        selectedPhotos.add(uri)
                        checkmarkView.visibility = View.VISIBLE
                    }
                    notifyItemChanged(position)
                }
            }
        }

        fun bind(uri: Uri) {
            val options = RequestOptions()
                .centerCrop()
                .override(300, 300)
                .dontAnimate()

            Glide.with(itemView.context)
                .load(uri)
                .apply(options)
                .into(galleryImageView)

            checkmarkView.visibility = if (selectedPhotos.contains(uri)) View.VISIBLE else View.GONE
        }
    }

    fun getSelectedPhotos(): List<Uri> = selectedPhotos.toList()

    private class GalleryDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }
    }
} 