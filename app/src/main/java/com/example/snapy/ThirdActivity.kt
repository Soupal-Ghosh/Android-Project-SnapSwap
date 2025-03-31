package com.example.snapy

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class ThirdActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_third)

        // Photo Swipe Button
        findViewById<MaterialButton>(R.id.btnPhotoSwipe).setOnClickListener {
            startActivity(Intent(this, PhotoSwipeActivity::class.java))
        }

        // AI Categorization Button
        findViewById<MaterialButton>(R.id.btnAICategorization).setOnClickListener {
            startActivity(Intent(this, AICategorizationActivity::class.java))
        }

        // Photo Collage Button
        findViewById<MaterialButton>(R.id.btnCollage).setOnClickListener {
            startActivity(Intent(this, CollageActivity::class.java))
        }
    }
}
