package com.example.snapy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ThirdActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_third)

        // Get the PhotoSwipeButton from the layout
        val photoSwipeButton = findViewById<Button>(R.id.photoSwipeButton)

        // Set onClickListener to navigate to PhotoSwipeActivity
        photoSwipeButton.setOnClickListener {
            val intent = Intent(this, PhotoSwipeActivity::class.java)
            startActivity(intent)
        }
    }
}
