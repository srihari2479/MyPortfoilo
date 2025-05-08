@file:Suppress("DEPRECATION", "PrivatePropertyName")

package com.example.csmstudentapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class EditStudentActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var rollNumber: String
    private lateinit var textViewRollNumber: TextView
    private lateinit var editTextName: TextInputEditText
    private lateinit var editTextCgpa: TextInputEditText
    private lateinit var editTextBacklogs: TextInputEditText
    private lateinit var editTextPercentage: TextInputEditText
    private lateinit var fabSave: FloatingActionButton
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var imageViewStudent: ImageView
    private lateinit var buttonChangeImage: MaterialButton
    private var selectedImageUri: Uri? = null
    private var existingImageBase64: String? = null // To store the existing image from Firestore
    private val PICK_IMAGE_REQUEST = 1
    private val TAG = "EditStudentActivity"

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        setContentView(R.layout.activity_edit_student)

        try {
            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            toolbar.setNavigationOnClickListener {
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            Log.d(TAG, "Toolbar set up")

            db = FirebaseFirestore.getInstance()
            Log.d(TAG, "Firestore initialized")

            rollNumber = intent.getStringExtra("ROLL_NUMBER")?.uppercase() ?: ""
            if (rollNumber.isEmpty()) {
                Snackbar.make(findViewById(android.R.id.content), R.string.toast_no_roll_number, Snackbar.LENGTH_SHORT).show()
                finish()
                return
            }

            textViewRollNumber = findViewById(R.id.textViewRollNumber)
            editTextName = findViewById(R.id.editTextName)
            editTextCgpa = findViewById(R.id.editTextCgpa)
            editTextBacklogs = findViewById(R.id.editTextBacklogs)
            editTextPercentage = findViewById(R.id.editTextPercentage)
            fabSave = findViewById(R.id.fabSave)
            fabRefresh = findViewById(R.id.fabRefresh)
            imageViewStudent = findViewById(R.id.imageViewStudent)
            buttonChangeImage = findViewById(R.id.buttonChangeImage)

            textViewRollNumber.text = getString(R.string.label_roll_number_with_value, rollNumber)
            try {
                textViewRollNumber.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
            } catch (e: Exception) {
                Log.w(TAG, "Roll number fade animation failed: ${e.message}")
            }

            // Restore selected image URI from saved state if available
            selectedImageUri = savedInstanceState?.getParcelable("selectedImageUri")
            if (selectedImageUri != null) {
                previewImage(selectedImageUri!!)
            }

            fetchStudentData()

            fabSave.setOnClickListener { view ->
                try {
                    view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce_scale_animation))
                } catch (e: Exception) {
                    Log.w(TAG, "Save FAB animation failed: ${e.message}")
                }
                updateStudent()
            }

            fabRefresh.setOnClickListener { view ->
                view.animate().rotationBy(360f).setDuration(500).start()
                refreshFields()
            }

            buttonChangeImage.setOnClickListener {
                openImagePicker()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Snackbar.make(findViewById(android.R.id.content), "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("selectedImageUri", selectedImageUri)
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedImageUri = data.data
            selectedImageUri?.let { previewImage(it) }
        }
    }

    private fun previewImage(uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            imageViewStudent.setImageBitmap(bitmap)
            imageViewStudent.visibility = View.VISIBLE
            imageViewStudent.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in))
            Log.d(TAG, "Image preview set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error previewing image: ${e.message}", e)
            Snackbar.make(findViewById(android.R.id.content), "Error previewing image", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun fetchStudentData() {
        db.collection("STUDENTS").document(rollNumber).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    editTextName.setText(document.getString("NAME") ?: "")
                    editTextCgpa.setText(document.getDouble("CGPA")?.toString() ?: "")
                    editTextBacklogs.setText(document.getString("BACKLOGS") ?: "0")
                    editTextPercentage.setText(document.getDouble("PERCENTAGE")?.toString() ?: "")

                    // Store the existing image Base64 string
                    existingImageBase64 = document.getString("imageBase64")
                    if (!existingImageBase64.isNullOrEmpty() && selectedImageUri == null) {
                        val decodedBytes = Base64.decode(existingImageBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        if (bitmap != null) {
                            imageViewStudent.setImageBitmap(bitmap)
                            imageViewStudent.visibility = View.VISIBLE
                        }
                    }

                    try {
                        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
                        editTextName.startAnimation(fadeIn)
                        editTextCgpa.startAnimation(fadeIn)
                        editTextBacklogs.startAnimation(fadeIn)
                        editTextPercentage.startAnimation(fadeIn)
                        if (imageViewStudent.isVisible) {
                            imageViewStudent.startAnimation(fadeIn)
                        }
                        Log.d(TAG, "Student data fetched and animated: $rollNumber")
                    } catch (e: Exception) {
                        Log.w(TAG, "Field fade animations failed: ${e.message}")
                    }
                } else {
                    Snackbar.make(findViewById(android.R.id.content), R.string.toast_student_not_found, Snackbar.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching data: ${e.message}", e)
                Snackbar.make(findViewById(android.R.id.content), R.string.toast_error_fetching_data, Snackbar.LENGTH_LONG).show()
                finish()
            }
    }

    @Suppress("DEPRECATION")
    private fun updateStudent() {
        editTextName.error = null
        editTextCgpa.error = null
        editTextBacklogs.error = null
        editTextPercentage.error = null

        val name = editTextName.text.toString().trim()
        val cgpa = editTextCgpa.text.toString().toDoubleOrNull()
        val backlogs = editTextBacklogs.text.toString().trim()
        val percentage = editTextPercentage.text.toString().toDoubleOrNull()
        val newImageBase64 = selectedImageUri?.let { uriToBase64(it) } // Only set if a new image is selected

        when {
            name.isEmpty() -> {
                editTextName.error = "Name cannot be empty"
                shakeView(editTextName)
                return
            }
            cgpa == null || cgpa.isNaN() -> {
                editTextCgpa.error = "Enter a valid CGPA"
                shakeView(editTextCgpa)
                return
            }
            backlogs.isEmpty() -> {
                editTextBacklogs.error = "Enter backlogs"
                shakeView(editTextBacklogs)
                return
            }
            percentage == null || percentage.isNaN() -> {
                editTextPercentage.error = "Enter a valid percentage"
                shakeView(editTextPercentage)
                return
            }
            cgpa !in 0.0..10.0 -> {
                editTextCgpa.error = "CGPA must be between 0.0 and 10.0"
                shakeView(editTextCgpa)
                return
            }
            percentage !in 0.0..100.0 -> {
                editTextPercentage.error = "Percentage must be between 0.0 and 100.0"
                shakeView(editTextPercentage)
                return
            }
        }

        fabSave.isEnabled = false
        fabSave.animate().rotation(360f).setDuration(500).start()

        val updates = mutableMapOf<String, Any>(
            "NAME" to name,
            "ROLL_NUMBER" to rollNumber,
            "CGPA" to cgpa,
            "BACKLOGS" to backlogs,
            "PERCENTAGE" to percentage
        )

        // Only update the image field if a new image was selected
        if (newImageBase64 != null) {
            updates["imageBase64"] = newImageBase64
        } else if (existingImageBase64 != null) {
            updates["imageBase64"] = existingImageBase64!! // Preserve existing image
        }

        db.collection("STUDENTS").document(rollNumber).set(updates)
            .addOnSuccessListener {
                Log.d(TAG, "Student updated: $rollNumber")
                Snackbar.make(findViewById(android.R.id.content), R.string.toast_student_updated, Snackbar.LENGTH_SHORT)
                    .setAction("OK") {
                        finish()
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                    .show()
                fabSave.isEnabled = true
                fabSave.rotation = 0f
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating student: ${e.message}", e)
                Snackbar.make(findViewById(android.R.id.content), R.string.toast_error_updating_student, Snackbar.LENGTH_LONG).show()
                fabSave.isEnabled = true
                fabSave.rotation = 0f
            }
    }

    private fun uriToBase64(uri: Uri): String {
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun refreshFields() {
        fetchStudentData()
        editTextName.error = null
        editTextCgpa.error = null
        editTextBacklogs.error = null
        editTextPercentage.error = null
        fabSave.isEnabled = true
        fabSave.rotation = 0f
        selectedImageUri = null
        if (existingImageBase64 != null) {
            val decodedBytes = Base64.decode(existingImageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                imageViewStudent.setImageBitmap(bitmap)
                imageViewStudent.visibility = View.VISIBLE
            }
        } else {
            imageViewStudent.visibility = View.GONE
        }
        Log.d(TAG, "Fields refreshed")
        Snackbar.make(findViewById(android.R.id.content), "Fields refreshed", Snackbar.LENGTH_SHORT).show()
    }

    private fun shakeView(view: View) {
        try {
            val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
            view.startAnimation(shake)
        } catch (e: Exception) {
            Log.w(TAG, "Shake animation failed: ${e.message}")
        }
    }
}