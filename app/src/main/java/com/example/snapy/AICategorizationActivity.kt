package com.example.snapy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class AICategorizationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AIGridAdapter
    private lateinit var btnSelectPhotos: MaterialButton
    private lateinit var btnStartCategorization: MaterialButton
    private lateinit var progressBar: ProgressBar

    private lateinit var captionCache: android.content.SharedPreferences
    private lateinit var blip: BlipInference
    private var selectedPhotos = mutableListOf<Uri>()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotos.clear()
            selectedPhotos.addAll(uris)
            adapter.submitList(uris)
            Toast.makeText(this, "Selected ${uris.size} photos", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_categorization)

        captionCache = getSharedPreferences("ai_cache", Context.MODE_PRIVATE)

        recyclerView = findViewById(R.id.recyclerView)
        btnSelectPhotos = findViewById(R.id.btnSelectPhotos)
        btnStartCategorization = findViewById(R.id.btnStartCategorization)
        progressBar = findViewById(R.id.progressBar)

        adapter = AIGridAdapter {}

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        btnSelectPhotos.setOnClickListener { checkPermission() }

        btnStartCategorization.setOnClickListener {
            if (selectedPhotos.isNotEmpty()) {
                startAICaptioning()
            } else {
                Toast.makeText(this, "Please select photos first", Toast.LENGTH_SHORT).show()
            }
        }
        blip = BlipInference(this)
        btnStartCategorization.isEnabled = false  // disable until model loads

        lifecycleScope.launch(Dispatchers.IO) {
            blip.load()
            withContext(Dispatchers.Main) {
                btnStartCategorization.isEnabled = true
                Toast.makeText(this@AICategorizationActivity, "Model ready", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pickImages.launch("image/*")
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 100 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            pickImages.launch("image/*")
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // ==============================
    // HASH GENERATION FOR CACHING
    // ==============================

    private fun generateImageHash(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)

        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun startAICaptioning() {
        progressBar.visibility = View.VISIBLE
        btnStartCategorization.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val results = mutableMapOf<Uri, String>()

            // ✅ Sequential — not parallel, ONNX sessions aren't thread-safe
            for (uri in selectedPhotos) {
                try {
                    val imageHash = generateImageHash(uri)

                    val cachedCaption = captionCache.getString(imageHash, null)
                    if (cachedCaption != null) {
                        results[uri] = cachedCaption
                        continue
                    }

                    val bitmap = contentResolver.openInputStream(uri).use {
                        BitmapFactory.decodeStream(it)
                    }

                    // ✅ suspend call — respects the mutex in BlipInference
                    val caption = blip.caption(bitmap)

                    captionCache.edit().putString(imageHash, caption).apply()
                    results[uri] = caption

                } catch (e: Exception) {
                    e.printStackTrace()
                    results[uri] = "Error: ${e.message}"
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnStartCategorization.isEnabled = true
                showResults(results)
            }
        }
    }

    private fun showResults(resultMap: Map<Uri, String>) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_categories, null)

        val categoriesRecyclerView =
            dialogView.findViewById<RecyclerView>(R.id.categoriesRecyclerView)
        val btnClose =
            dialogView.findViewById<MaterialButton>(R.id.btnClose)

        categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        categoriesRecyclerView.adapter = CaptionResultAdapter(resultMap)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}