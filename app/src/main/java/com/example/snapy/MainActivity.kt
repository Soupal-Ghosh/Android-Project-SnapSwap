package com.example.snapy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get reference to the button
        val getStartedButton: Button = findViewById(R.id.getStartedButton)

        // Set click listener
        getStartedButton.setOnClickListener {
            // Go to another screen (replace SecondActivity with actual activity)
            val intent = Intent(this, SecondActivity::class.java)
            startActivity(intent)
        }
    }
}

