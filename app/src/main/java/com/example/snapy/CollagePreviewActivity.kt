package com.example.snapy

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.OutputStream

class CollagePreviewActivity : AppCompatActivity() {

    companion object {
        var finalCollageBitmap: Bitmap? = null  // Set this before starting activity
    }

    private lateinit var collageImageView: ImageView
    private lateinit var btnSave: Button
    private lateinit var btnShare: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collage_preview)

        collageImageView = findViewById(R.id.collageImageView)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        btnCancel = findViewById(R.id.btnCancel)

        finalCollageBitmap?.let {
            collageImageView.setImageBitmap(it)
        }

        btnSave.setOnClickListener {
            finalCollageBitmap?.let { bitmap ->
                val uri = saveBitmapToGallery(bitmap)
                if (uri != null) {
                    Toast.makeText(this, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnShare.setOnClickListener {
            finalCollageBitmap?.let { bitmap ->
                val uri = saveBitmapToGallery(bitmap, shareOnly = true)
                uri?.let {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, it)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Collage"))
                }
            }
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, shareOnly: Boolean = false): Uri? {
        val uri = MediaStore.Images.Media.insertImage(
            contentResolver,
            bitmap,
            "SnapyCollage_${System.currentTimeMillis()}",
            "Collage created using Snapy"
        )
        return if (uri != null) Uri.parse(uri) else null
    }
}
