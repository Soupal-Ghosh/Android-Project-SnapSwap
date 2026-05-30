package com.example.snapy

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.snapy.databinding.ItemGridPhotoBinding

class SimpleGridAdapter(
    private val onPhotoClick: (Uri) -> Unit,
    private val onPhotoLongClick: (Uri) -> Unit
) : ListAdapter<Uri, SimpleGridAdapter.ViewHolder>(UriDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGridPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemGridPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uri: Uri) {
            Glide.with(binding.root).load(uri).centerCrop().into(binding.imageView)
            binding.root.setOnClickListener { onPhotoClick(uri) }
            binding.root.setOnLongClickListener { 
                onPhotoLongClick(uri)
                true 
            }
        }
    }

    private class UriDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
        override fun areContentsTheSame(oldItem: Uri, newItem: Uri) = oldItem == newItem
    }
}
