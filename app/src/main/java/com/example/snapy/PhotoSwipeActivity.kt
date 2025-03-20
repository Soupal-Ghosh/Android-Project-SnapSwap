package com.example.snapy

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class PhotoSwipeActivity : AppCompatActivity() {
    private lateinit var photoAdapter: PhotoAdapter
    private val imageList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_swipe)

        // Get the ViewPager and Upload Button from the layout
        val viewPager = findViewById<ViewPager2>(R.id.photoViewPager)
        val uploadButton = findViewById<Button>(R.id.uploadButton)

        // List of preloaded images
        val preloadedImages = listOf(
            "android.resource://$packageName/${R.drawable.image1}",
            "android.resource://$packageName/${R.drawable.image2}",
            "android.resource://$packageName/${R.drawable.image3}",
            "android.resource://$packageName/${R.drawable.image5}",
            "android.resource://$packageName/${R.drawable.image6}"
        )

        // Add preloaded images to the list
        imageList.addAll(preloadedImages)

        // Create PhotoAdapter and set it to ViewPager
        photoAdapter = PhotoAdapter(imageList)
        viewPager.adapter = photoAdapter

        // Launch gallery to select an image
        val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val imagePath = it.toString()
                imageList.add(imagePath)
                photoAdapter.notifyItemInserted(imageList.size - 1)
            }
        }

        // Set onClickListener for the upload button
        uploadButton.setOnClickListener {
            getContent.launch("image/*")
        }
    }
}
