package com.example.snapy
import android.Manifest
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CaptionResultAdapter(
    private val items: Map<Uri, String>
) : RecyclerView.Adapter<CaptionResultAdapter.ViewHolder>() {

    private val data = items.entries.toList()

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val img: ImageView = itemView.findViewById(R.id.imageView)   // already exists in your category item
        val caption: TextView = itemView.findViewById(R.id.captionText) // text already in adapter layout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // IMPORTANT: Use the category list item layout, NOT activity_ai_categorization
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (uri, captionText) = data[position]

        Glide.with(holder.itemView.context)
            .load(uri)
            .centerCrop()
            .into(holder.img)

        holder.caption.text = captionText.ifEmpty { "No caption" }
    }

    override fun getItemCount(): Int = data.size
}
