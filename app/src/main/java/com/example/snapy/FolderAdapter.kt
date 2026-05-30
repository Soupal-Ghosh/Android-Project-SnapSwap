package com.example.snapy

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.snapy.databinding.ItemFolderCardBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderAdapter(
    private val repository: PhotoRepository,
    private val onFolderClick: (Folder) -> Unit,
    private val onFolderLongClick: (Folder) -> Unit
) : ListAdapter<Folder, FolderAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(private val binding: ItemFolderCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(folder: Folder) {
            binding.tvFolderName.text = folder.name
            
            // Load photo count and thumbnail asynchronously
            CoroutineScope(Dispatchers.IO).launch {
                val count = repository.getPhotoCount(folder.id)
                val thumbUri = repository.getFolderThumbnail(folder.id)
                
                withContext(Dispatchers.Main) {
                    binding.tvPhotoCount.text = "$count photos"
                    if (thumbUri != null) {
                        Glide.with(binding.root).load(thumbUri).into(binding.ivFolderCover)
                    } else {
                        binding.ivFolderCover.setImageResource(R.drawable.ic_grid)
                    }
                }
            }

            binding.root.setOnClickListener { onFolderClick(folder) }
            binding.root.setOnLongClickListener { 
                onFolderLongClick(folder)
                true 
            }
        }
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Folder>() {
        override fun areItemsTheSame(oldItem: Folder, newItem: Folder) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Folder, newItem: Folder) = oldItem == newItem
    }
}
