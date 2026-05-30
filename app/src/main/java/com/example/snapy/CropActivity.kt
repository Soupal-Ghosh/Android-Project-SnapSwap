package com.example.snapy

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.snapy.databinding.ActivityCropBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CropActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCropBinding
    private var sourceUri: Uri? = null
    private var finalCroppedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCropBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString = intent.getStringExtra("uri")
        if (uriString == null) {
            finish()
            return
        }
        sourceUri = Uri.parse(uriString)

        setupToolbar()
        setupCropView()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupCropView() {
        binding.cropImageView.setImageUriAsync(sourceUri)
    }

    private fun setupButtons() {
        binding.btnCrop.setOnClickListener {
            val cropped = binding.cropImageView.getCroppedImage()
            if (cropped != null) {
                finalCroppedBitmap = cropped
                binding.previewImageView.setImageBitmap(cropped)
                binding.previewImageView.visibility = View.VISIBLE
                binding.cropImageView.visibility = View.GONE
                
                binding.btnCrop.isEnabled = false
                binding.btnSave.isEnabled = true
                
                Toast.makeText(this, "Crop applied. Press Save to finish.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to apply crop", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            saveCroppedImage()
        }
    }

    private fun saveCroppedImage() {
        val bitmap = finalCroppedBitmap ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filename = "Cropped_Snapy_${System.currentTimeMillis()}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Snapy")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(it, values, null, null)
                    }

                    // Notify the system that a new file was added
                    contentResolver.notifyChange(it, null)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CropActivity, "Image cropped and saved successfully", Toast.LENGTH_SHORT).show()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
