package com.example.snapy

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.OutputStream

class CollagePreviewActivity : AppCompatActivity() {

    companion object {
        var finalCollageBitmap: Bitmap? = null
    }

    private lateinit var collageImageView: ImageView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var btnCancel: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collage_preview)

        collageImageView = findViewById(R.id.collageImageView)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        btnCancel = findViewById(R.id.btnCancel)

        finalCollageBitmap?.let {
            collageImageView.setImageBitmap(it)
        } ?: run {
            Toast.makeText(this, "Collage not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        btnSave.setOnClickListener {
            saveCollageToGallery(true)
        }

        btnShare.setOnClickListener {
            shareCollage()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveCollageToGallery(showToast: Boolean): Uri? {
        val bitmap = finalCollageBitmap ?: return null
        val filename = "Snapy_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            val contentResolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Snapy")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            imageUri?.let { uri ->
                fos = contentResolver.openOutputStream(uri)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos!!)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                }
                
                if (showToast) Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (showToast) Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            fos?.close()
        }
        return imageUri
    }

    private fun shareCollage() {
        val uri = saveCollageToGallery(false)
        if (uri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Collage"))
        } else {
            Toast.makeText(this, "Failed to prepare sharing", Toast.LENGTH_SHORT).show()
        }
    }
}
