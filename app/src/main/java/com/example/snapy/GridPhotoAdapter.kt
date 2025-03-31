package com.example.snapy

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.snapy.databinding.ItemGridPhotoBinding

class GridPhotoAdapter<T : Any>(
    private val onPhotoClick: ((Photo) -> Unit)? = null
) : ListAdapter<T, GridPhotoAdapter.GridPhotoViewHolder>(GridPhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridPhotoViewHolder {
        val binding = ItemGridPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GridPhotoViewHolder(binding, onPhotoClick)
    }

    override fun onBindViewHolder(holder: GridPhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GridPhotoViewHolder(
        private val binding: ItemGridPhotoBinding,
        private val onPhotoClick: ((Photo) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Any) {
            when (item) {
                is Photo -> {
                    Glide.with(binding.root)
                        .load(item.imageUri)
                        .centerCrop()
                        .into(binding.imageView)
                    
                    binding.root.setOnClickListener {
                        onPhotoClick?.invoke(item)
                    }
                }
                is Uri -> {
                    Glide.with(binding.root)
                        .load(item)
                        .centerCrop()
                        .into(binding.imageView)
                }
            }
        }
    }

    private class GridPhotoDiffCallback<T : Any> : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return when {
                oldItem is Photo && newItem is Photo -> oldItem.id == newItem.id
                oldItem is Uri && newItem is Uri -> oldItem == newItem
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return when {
                oldItem is Photo && newItem is Photo -> oldItem == newItem
                oldItem is Uri && newItem is Uri -> oldItem == newItem
                else -> false
            }
        }
    }
} 