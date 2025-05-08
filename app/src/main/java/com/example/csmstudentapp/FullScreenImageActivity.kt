@file:Suppress("DEPRECATION")

package com.example.csmstudentapp

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

class FullScreenImageActivity : AppCompatActivity() {
    private val tag = "FullScreenImageActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            ImageCache.clearImage(intent.getStringExtra("ROLL_NUMBER") ?: "")
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Get roll number from intent
        val rollNumber = intent.getStringExtra("ROLL_NUMBER")
        if (rollNumber.isNullOrEmpty()) {
            Log.e(tag, "No roll number provided")
            Snackbar.make(findViewById(android.R.id.content), "No image data", Snackbar.LENGTH_LONG).show()
            finish()
            return
        }

        // Retrieve image from cache
        val imageBase64 = ImageCache.getImage(rollNumber)
        if (imageBase64.isNullOrEmpty()) {
            Log.e(tag, "No image data found in cache for roll number: $rollNumber")
            Snackbar.make(findViewById(android.R.id.content), "No image data", Snackbar.LENGTH_LONG).show()
            finish()
            return
        }

        // Load image into PhotoView
        val photoView = findViewById<PhotoView>(R.id.photoView)
        try {
            val cleanBase64 = if (imageBase64.startsWith("data:image")) {
                imageBase64.substringAfter(",")
            } else {
                imageBase64
            }
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                photoView.setImageBitmap(bitmap)
                Log.d(tag, "Image loaded successfully")
            } else {
                throw IllegalArgumentException("Bitmap decoding failed")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error loading image: ${e.message}", e)
            Snackbar.make(findViewById(android.R.id.content), "Error loading image", Snackbar.LENGTH_LONG).show()
            finish()
        } finally {
            ImageCache.clearImage(rollNumber) // Clear cache to free memory
        }
    }
}