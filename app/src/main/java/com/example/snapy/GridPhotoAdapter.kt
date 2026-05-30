package com.example.snapy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.example.snapy.databinding.ItemGridPhotoBinding

class GridPhotoAdapter(
    private val onPhotoClick: (Photo) -> Unit,
    private val onPhotoLongClick: (Photo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<GalleryItem, RecyclerView.ViewHolder>(GalleryDiffCallback()) {

    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<Int>()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PHOTO = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GalleryItem.Header -> TYPE_HEADER
            is GalleryItem.PhotoItem -> TYPE_PHOTO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_gallery_header, parent, false)
                HeaderViewHolder(view as TextView)
            }
            else -> {
                val binding = ItemGridPhotoBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                GridPhotoViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        
        // Handle StaggeredGridLayoutManager full span for headers
        val layoutParams = holder.itemView.layoutParams
        if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
            layoutParams.isFullSpan = item is GalleryItem.Header
        }

        when (holder) {
            is HeaderViewHolder -> holder.bind((item as GalleryItem.Header).title)
            is GridPhotoViewHolder -> {
                val photoItem = item as GalleryItem.PhotoItem
                holder.bind(photoItem.photo, selectedItems.contains(photoItem.photo.id))
                
                holder.itemView.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(photoItem.photo.id)
                    } else {
                        onPhotoClick(photoItem.photo)
                    }
                }
                
                holder.itemView.setOnLongClickListener {
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        toggleSelection(photoItem.photo.id)
                        onPhotoLongClick(photoItem.photo)
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    private fun toggleSelection(photoId: Int) {
        if (selectedItems.contains(photoId)) {
            selectedItems.remove(photoId)
        } else {
            selectedItems.add(photoId)
        }
        notifyDataSetChanged() 
        onSelectionChanged(selectedItems.size)
    }

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) selectedItems.clear()
            notifyDataSetChanged()
        }
    }

    fun getSelectedPhotos(): List<Photo> {
        return currentList.filterIsInstance<GalleryItem.PhotoItem>()
            .map { it.photo }
            .filter { selectedItems.contains(it.id) }
    }
    
    fun selectAll() {
        currentList.filterIsInstance<GalleryItem.PhotoItem>().forEach {
            selectedItems.add(it.photo.id)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedItems.size)
    }

    class HeaderViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(title: String) {
            textView.text = title
        }
    }

    class GridPhotoViewHolder(
        private val binding: ItemGridPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: Photo, isSelected: Boolean) {
            val requestOptions = RequestOptions()
                .fitCenter() 
                .override(400, 400) // Lower resolution for grid thumbnails
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)

            Glide.with(binding.root)
                .load(photo.imageUri ?: photo.imageResId)
                .apply(requestOptions)
                .thumbnail(0.25f) // Load a 20% resolution thumbnail first
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.imageView)
            
            binding.selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.ivFavorite.visibility = if (photo.isLiked) View.VISIBLE else View.GONE
            binding.root.alpha = if (isSelected) 0.7f else 1.0f
        }
    }

    private class GalleryDiffCallback : DiffUtil.ItemCallback<GalleryItem>() {
        override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
            return if (oldItem is GalleryItem.Header && newItem is GalleryItem.Header) {
                oldItem.title == newItem.title
            } else if (oldItem is GalleryItem.PhotoItem && newItem is GalleryItem.PhotoItem) {
                oldItem.photo.id == newItem.photo.id
            } else false
        }

        override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem): Boolean {
            return oldItem == newItem
        }
    }
}
