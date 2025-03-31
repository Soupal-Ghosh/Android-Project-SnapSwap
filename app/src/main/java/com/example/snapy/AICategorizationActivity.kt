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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AICategorizationActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AIGridAdapter
    private lateinit var btnSelectPhotos: MaterialButton
    private lateinit var btnStartCategorization: MaterialButton
    private lateinit var progressBar: ProgressBar
    private var selectedPhotos = mutableListOf<Uri>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickImages.launch("image/*")
        } else {
            Toast.makeText(this, "Permission required to select photos", Toast.LENGTH_SHORT).show()
        }
    }

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

        adapter = AIGridAdapter { uri ->
            // Handle photo click if needed
        }

        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        btnSelectPhotos.setOnClickListener {
            checkPermissionAndPickImages()
        }

        btnStartCategorization.setOnClickListener {
            if (selectedPhotos.isNotEmpty()) {
                startCategorization()
            } else {
                Toast.makeText(this, "Please select photos first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionAndPickImages() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) == PackageManager.PERMISSION_GRANTED -> {
                pickImages.launch("image/*")
            }
            else -> {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun getGeneralCategory(labels: List<String>): String {
        val categories = mapOf(
            "People" to listOf("person", "people", "human", "face", "portrait", "selfie", "group", "crowd", "family", "baby", "child", "adult", "man", "woman", "boy", "girl"),
            "Nature" to listOf("nature", "landscape", "mountain", "beach", "ocean", "sea", "river", "lake", "forest", "tree", "plant", "flower", "grass", "sky", "sunset", "sunrise", "cloud", "rain", "snow", "desert", "jungle", "waterfall"),
            "Food" to listOf("food", "meal", "dish", "restaurant", "cooking", "fruit", "vegetable", "dessert", "cake", "bread", "pizza", "burger", "sandwich", "salad", "soup", "drink", "coffee", "tea", "wine", "beer"),
            "Architecture" to listOf("building", "architecture", "house", "apartment", "office", "tower", "bridge", "monument", "temple", "church", "mosque", "palace", "castle", "museum", "stadium", "school", "hospital"),
            "Art" to listOf("art", "painting", "drawing", "sculpture", "photography", "gallery", "museum", "exhibition", "design", "craft", "handmade", "artwork", "illustration", "graffiti", "mural"),
            "Technology" to listOf("computer", "laptop", "phone", "tablet", "device", "gadget", "electronics", "robot", "machine", "screen", "keyboard", "mouse", "camera", "headphone", "speaker", "watch", "drone"),
            "Sports" to listOf("sport", "game", "player", "team", "stadium", "field", "court", "ball", "racket", "bike", "bicycle", "car", "vehicle", "motorcycle", "skateboard", "surfboard", "gym", "fitness", "exercise"),
            "Animals" to listOf("animal", "pet", "dog", "cat", "bird", "fish", "wildlife", "zoo", "farm", "horse", "cow", "sheep", "elephant", "lion", "tiger", "bear", "monkey", "deer", "rabbit", "insect"),
            "Fashion" to listOf("fashion", "clothing", "dress", "shirt", "pants", "shoes", "accessory", "jewelry", "bag", "watch", "glasses", "hat", "jacket", "coat", "suit", "uniform", "costume"),
            "Travel" to listOf("travel", "tourism", "vacation", "holiday", "trip", "tourist", "landmark", "attraction", "resort", "hotel", "airport", "airplane", "train", "bus", "boat", "cruise", "camping", "hiking"),
            "Other" to listOf("other", "miscellaneous", "object", "thing", "item", "stuff", "material", "texture", "pattern", "color", "shape", "form", "structure", "system", "process", "activity", "event", "occasion")
        )

        // Count occurrences of each category's keywords
        val categoryCounts = categories.mapValues { (_, keywords) ->
            labels.count { label ->
                keywords.any { keyword ->
                    label.contains(keyword, ignoreCase = true)
                }
            }
        }

        // Find the category with the most matches
        return categoryCounts.maxByOrNull { it.value }?.key ?: "Other"
    }

    private fun startCategorization() {
        progressBar.visibility = View.VISIBLE
        btnStartCategorization.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            val categorizedPhotos = mutableMapOf<String, MutableList<Uri>>()

            for (uri in selectedPhotos) {
                try {
                    val image = InputImage.fromFilePath(this@AICategorizationActivity, uri)
                    val labels = labeler.process(image).await()
                    val labelTexts = labels.map { it.text }
                    val category = getGeneralCategory(labelTexts)

                    if (!categorizedPhotos.containsKey(category)) {
                        categorizedPhotos[category] = mutableListOf()
                    }
                    categorizedPhotos[category]?.add(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                btnStartCategorization.isEnabled = true
                showCategoriesDialog(categorizedPhotos)
            }
        }
    }

    private fun showCategoriesDialog(categories: Map<String, List<Uri>>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_categories, null)
        val categoriesRecyclerView = dialogView.findViewById<RecyclerView>(R.id.categoriesRecyclerView)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)

        categoriesRecyclerView.layoutManager = LinearLayoutManager(this)
        categoriesRecyclerView.adapter = CategoryAdapter(categories)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 100
    }
} 