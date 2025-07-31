package com.example.snapy

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.snapy.databinding.ActivityForgotPasswordBinding

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        binding.resetButton.setOnClickListener {
            val username = binding.username.text.toString().trim()
            val newPassword = binding.newPassword.text.toString().trim()
            val confirmPassword = binding.confirmPassword.text.toString().trim()

            if (username.isEmpty()) {
                binding.username.error = "Username is required"
                return@setOnClickListener
            }

            if (!sharedPreferences.contains(username)) {
                Toast.makeText(this, "Account doesn't exist", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPassword.length < 6) {
                binding.newPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                binding.confirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }

            with(sharedPreferences.edit()) {
                putString(username, newPassword)
                apply()
            }

            Toast.makeText(this, "Password reset successful!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
