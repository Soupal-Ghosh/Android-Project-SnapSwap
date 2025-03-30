package com.example.snapy

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class WelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // Initialize views
        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        val appNameText = findViewById<TextView>(R.id.appNameText)
        val getStartedButton = findViewById<Button>(R.id.getStartedButton)
        val logoImage = findViewById<ImageView>(R.id.logoImage)

        // Set animations
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        
        welcomeText.startAnimation(fadeIn)
        appNameText.startAnimation(fadeIn)
        logoImage.startAnimation(slideUp)
        getStartedButton.startAnimation(slideUp)

        // Set button click listener
        getStartedButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
} 