package com.example.snapy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AICategorizationActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AIGridAdapter
    private lateinit var btnSelectPhotos: MaterialButton
    private lateinit var btnStartCategorization: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var selectedPhotos = mutableListOf<Uri>()
    private val client = OkHttpClient()

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

        recyclerView = findViewById(R.id.recyclerView)
        btnSelectPhotos = findViewById(R.id.btnSelectPhotos)
        btnStartCategorization = findViewById(R.id.btnStartCategorization)
        progressBar = findViewById(R.id.progressBar)

        adapter = AIGridAdapter { }

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        btnSelectPhotos.setOnClickListener {
            checkPermission()
        }

        btnStartCategorization.setOnClickListener {
            if (selectedPhotos.isNotEmpty()) {
                startAICaptioning()
            } else {
                Toast.makeText(this, "Please select photos first", Toast.LENGTH_SHORT).show()
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

    // Convert Uri → File so it can be sent to backend
    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return file
    }

    private fun startAICaptioning() {
        progressBar.visibility = View.VISIBLE
        btnStartCategorization.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {

            val results = mutableMapOf<Uri, String>()

            for (uri in selectedPhotos) {
                try {
                    val imageFile = uriToFile(uri)

                    // Detect correct mime type
                    val mime = contentResolver.getType(uri) ?: "image/jpeg"

                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "image",                      // MUST MATCH BACKEND
                            imageFile.name,
                            imageFile.asRequestBody(mime.toMediaType())
                        )
                        .build()

                    val request = Request.Builder()
                        .url("https://android-aicategorization-backend.onrender.com/describe")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    val responseString = response.body?.string()

                    val caption = JSONObject(responseString ?: "{}")
                        .optString("caption", "No caption")

                    results[uri] = caption

                } catch (e: Exception) {
                    e.printStackTrace()
                    results[uri] = "Error processing image"
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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_categories, null)
        val categoriesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.categoriesRecyclerView)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)

        categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        categoriesRecyclerView.adapter = CaptionResultAdapter(resultMap)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
