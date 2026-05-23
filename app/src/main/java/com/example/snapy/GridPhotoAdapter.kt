package com.example.snapy

import android.view.LayoutInflater
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
    private val onPhotoClick: ((Photo) -> Unit)? = null,
    private val onPhotoLongClick: ((Photo) -> Unit)? = null
) : ListAdapter<GalleryItem, RecyclerView.ViewHolder>(GalleryDiffCallback()) {

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
                GridPhotoViewHolder(binding, onPhotoClick, onPhotoLongClick)
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
            is GridPhotoViewHolder -> holder.bind((item as GalleryItem.PhotoItem).photo)
        }
    }

    class HeaderViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(title: String) {
            textView.text = title
        }
    }

    class GridPhotoViewHolder(
        private val binding: ItemGridPhotoBinding,
        private val onPhotoClick: ((Photo) -> Unit)?,
        private val onPhotoLongClick: ((Photo) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: Photo) {
            val requestOptions = RequestOptions()
                .fitCenter() // Preserve aspect ratio
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)

            Glide.with(binding.root)
                .load(photo.imageUri ?: photo.imageResId)
                .apply(requestOptions)
                .transition(DrawableTransitionOptions.withCrossFade())
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
