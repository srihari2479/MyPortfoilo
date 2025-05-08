@file:Suppress("PrivatePropertyName")

package com.example.csmstudentapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore

@Suppress("DEPRECATION")
class ReportActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var editTextRollNumber: TextInputEditText
    private lateinit var editTextDescription: TextInputEditText
    private lateinit var buttonSubmit: MaterialButton
    private val TAG = "ReportActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        db = FirebaseFirestore.getInstance()

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        editTextRollNumber = findViewById(R.id.editTextRollNumber)
        editTextDescription = findViewById(R.id.editTextDescription)
        buttonSubmit = findViewById(R.id.buttonSubmit)

        buttonSubmit.setOnClickListener {
            buttonSubmit.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce_scale_animation))
            submitReport()
        }
    }

    private fun submitReport() {
        val rollNumber = editTextRollNumber.text.toString().trim().uppercase()
        val description = editTextDescription.text.toString().trim()

        if (rollNumber.isEmpty()) {
            editTextRollNumber.error = "Please enter Roll Number"
            return
        }

        if (description.isEmpty()) {
            editTextDescription.error = "Please describe the issue"
            return
        }

        val report = hashMapOf(
            "ROLL_NUMBER" to rollNumber,
            "DESCRIPTION" to description,
            "TIMESTAMP" to System.currentTimeMillis(),
            "STATUS" to "PENDING"
        )

        db.collection("STUDENT_ISSUES")
            .document("${rollNumber}_${System.currentTimeMillis()}")
            .set(report)
            .addOnSuccessListener {
                Log.d(TAG, "Report submitted: $rollNumber")
                Toast.makeText(this, "Issue submitted. Expect resolution within 24 hrs.", Toast.LENGTH_LONG).show()
                hideKeyboard()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error submitting: ${e.message}", e)
                Toast.makeText(this, "Submission failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun hideKeyboard() {
        val view: View? = this.currentFocus
        if (view != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
