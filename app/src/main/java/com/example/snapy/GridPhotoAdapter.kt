package com.example.snapy

import android.app.AlertDialog
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.snapy.databinding.ItemGridPhotoBinding

class GridPhotoAdapter(
    private val onPhotoClick: ((Photo) -> Unit)? = null,
    private val onPhotoLongClick: ((Photo) -> Unit)? = null
) : ListAdapter<Photo, GridPhotoAdapter.GridPhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridPhotoViewHolder {
        val binding = ItemGridPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GridPhotoViewHolder(binding, onPhotoClick, onPhotoLongClick)
    }

    override fun onBindViewHolder(holder: GridPhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GridPhotoViewHolder(
        private val binding: ItemGridPhotoBinding,
        private val onPhotoClick: ((Photo) -> Unit)?,
        private val onPhotoLongClick: ((Photo) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: Photo) {
            
            val requestOptions = RequestOptions()
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)

            Glide.with(binding.root)
                .load(photo.imageUri ?: photo.imageResId)
                .apply(requestOptions)
                .transition(DrawableTransitionOptions.withCrossFade())
                .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: com.bumptech.glide.load.engine.GlideException?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                        dataSource: com.bumptech.glide.load.DataSource?,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
                .into(binding.imageView)
            
            binding.root.setOnClickListener {
                onPhotoClick?.invoke(photo)
            }
            
            binding.root.setOnLongClickListener {
                onPhotoLongClick?.invoke(photo)
                true
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