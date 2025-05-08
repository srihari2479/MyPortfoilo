package com.example.csmstudentapp

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest

@Suppress("DEPRECATION")
class AdminActivity : AppCompatActivity() {
    @Suppress("PrivatePropertyName")
    private val TAG = "AdminActivity"
    private lateinit var db: FirebaseFirestore
    private var adminName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        db = FirebaseFirestore.getInstance()

        setupToolbar()
        initializeAdmin(savedInstanceState)
        setupButtons()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToMainActivity()
            }
        })
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            navigateToMainActivity()
        }
    }

    private fun initializeAdmin(savedInstanceState: Bundle?) {
        adminName = savedInstanceState?.getString("ADMIN_NAME") ?: intent.getStringExtra("ADMIN_NAME")
        if (adminName != null) {
            updateWelcomeText()
            checkMasterAdminStatus()
        } else {
            Log.w(TAG, "Admin name not provided in intent or saved state")
            Snackbar.make(findViewById(android.R.id.content), "Admin name missing", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun setupButtons() {
        val buttonAddStudent = findViewById<MaterialButton>(R.id.buttonAddStudent)
        val buttonEditStudent = findViewById<MaterialButton>(R.id.buttonEditStudent)
        val buttonChangePassword = findViewById<MaterialButton>(R.id.buttonChangePassword)
        val buttonListStudents = findViewById<MaterialButton>(R.id.buttonListStudents)
        val buttonStudentIssues = findViewById<MaterialButton>(R.id.buttonStudentIssues)

        buttonAddStudent.setOnClickListener {
            startActivity(Intent(this, AddStudentActivity::class.java))
        }
        buttonEditStudent.setOnClickListener {
            showEditStudentDialog()
        }
        buttonChangePassword.setOnClickListener {
            val intent = Intent(this, ChangePasswordActivity::class.java)
            intent.putExtra("ADMIN_NAME", adminName)
            startActivity(intent)
        }
        buttonListStudents.setOnClickListener {
            val intent = Intent(this, ListStudentsActivity::class.java)
            intent.putExtra("SHOW_LOADING", true) // Pass flag to show loading
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        buttonStudentIssues.setOnClickListener {
            startActivity(Intent(this, StudentIssuesActivity::class.java))
        }
    }

    private fun checkMasterAdminStatus() {
        if (adminName == "@srima12") {
            db.collection("admins").document("@srima12").get()
                .addOnSuccessListener { document ->
                    if (document.exists() && document.getBoolean("is_master") == true) {
                        Log.d(TAG, "Master admin @srima12 confirmed")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking master admin status: ${e.message}", e)
                }
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showAddNewAdminDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_student, null)
        val textInputLayoutAdminName = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutRollNumber)
            ?.apply { hint = "New Admin Name" }
        val editTextAdminName = dialogView.findViewById<TextInputEditText>(R.id.editTextRollNumber)
        val buttonSubmit = dialogView.findViewById<MaterialButton>(R.id.buttonSubmit)
        val buttonPaste = dialogView.findViewById<MaterialButton>(R.id.buttonPaste)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Add New Admin")
            .create()

        buttonSubmit.setOnClickListener {
            val newAdminName = editTextAdminName?.text.toString().trim()
            if (newAdminName.isNotEmpty() && newAdminName != "@srima12") {
                registerNewAdmin(newAdminName, dialog)
            } else {
                textInputLayoutAdminName?.error = "Enter a valid new admin name (not @srima12)"
            }
        }

        buttonPaste.setOnClickListener {
            pasteFromClipboard(editTextAdminName, textInputLayoutAdminName, dialogView)
        }

        dialog.show()
    }

    private fun registerNewAdmin(newAdminName: String, dialog: AlertDialog) {
        val defaultPassword = "admin123"
        val defaultHash = hashPassword(defaultPassword)

        val adminData = hashMapOf(
            "master_password_hash" to defaultHash,
            "created_at" to System.currentTimeMillis(),
            "created_by" to "@srima12",
            "is_master" to false
        )

        db.collection("admins").document(newAdminName).set(adminData)
            .addOnSuccessListener {
                Log.d(TAG, "New admin $newAdminName registered successfully")
                Snackbar.make(findViewById(android.R.id.content),
                    "Admin $newAdminName added with default password 'admin123'. They must change it.",
                    Snackbar.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding new admin: ${e.message}", e)
                Snackbar.make(findViewById(android.R.id.content),
                    "Failed to add admin: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun pasteFromClipboard(editText: TextInputEditText?, textInputLayout: TextInputLayout?, dialogView: View) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text?.toString()?.trim()
                if (!pastedText.isNullOrEmpty()) {
                    editText?.setText(pastedText)
                    textInputLayout?.error = null
                    Snackbar.make(dialogView, "Pasted successfully", Snackbar.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Paste failed: ${e.message}", e)
            Snackbar.make(dialogView, "Paste error: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun hashPassword(@Suppress("SameParameterValue") password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Hashing error: ${e.message}", e)
            ""
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("ADMIN_NAME", adminName)
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
        Log.d(TAG, "Navigating back to MainActivity")
    }

    @SuppressLint("SetTextI18n")
    private fun updateWelcomeText() {
        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        welcomeText.text = "Welcome to $adminName"
    }

    @SuppressLint("MissingInflatedId")
    private fun showEditStudentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_student, null)
        val textInputLayoutRollNumber = dialogView.findViewById<TextInputLayout>(R.id.textInputLayoutRollNumber)
            ?: return logError("textInputLayoutRollNumber not found")
        val editTextRollNumber = dialogView.findViewById<TextInputEditText>(R.id.editTextRollNumber)
            ?: return logError("editTextRollNumber not found")
        val buttonSubmit = dialogView.findViewById<MaterialButton>(R.id.buttonSubmit)
            ?: return logError("buttonSubmit not found")
        val buttonPaste = dialogView.findViewById<MaterialButton>(R.id.buttonPaste)
            ?: return logError("buttonPaste not found")

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        buttonSubmit.setOnClickListener {
            animateButton(it)
            val rollNumber = editTextRollNumber.text.toString().trim().uppercase()
            if (rollNumber.isNotEmpty()) {
                launchEditStudentActivity(rollNumber, dialog)
            } else {
                textInputLayoutRollNumber.error = getString(R.string.toast_enter_roll_number)
                animateError(editTextRollNumber)
            }
        }

        buttonPaste.setOnClickListener {
            animateButton(it)
            pasteFromClipboard(editTextRollNumber, textInputLayoutRollNumber, dialogView)
        }

        dialog.show()
        applyDialogAnimations(textInputLayoutRollNumber, buttonSubmit, buttonPaste)
    }

    private fun animateButton(view: View) {
        try {
            val bounceAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce_scale_animation)
            view.startAnimation(bounceAnimation)
        } catch (e: Exception) {
            Log.w(TAG, "Button animation failed: ${e.message}")
        }
    }

    private fun animateError(view: TextInputEditText) {
        try {
            view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_error))
        } catch (e: Exception) {
            Log.w(TAG, "Shake animation failed: ${e.message}")
        }
    }

    private fun applyDialogAnimations(vararg views: View) {
        try {
            val fadeInAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            views.forEach { it.startAnimation(fadeInAnimation) }
            Log.d(TAG, "Edit student dialog shown")
        } catch (e: Exception) {
            Log.w(TAG, "Dialog animation failed: ${e.message}")
        }
    }

    private fun launchEditStudentActivity(rollNumber: String, dialog: AlertDialog) {
        try {
            val intent = Intent(this, EditStudentActivity::class.java)
            intent.putExtra("ROLL_NUMBER", rollNumber)
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            dialog.dismiss()
            Log.d(TAG, "Launching EditStudentActivity with roll number: $rollNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching EditStudentActivity: ${e.message}", e)
            Snackbar.make(findViewById(android.R.id.content),
                "Failed to open Edit Student: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun logError(message: String) {
        Log.e(TAG, message)
        Snackbar.make(findViewById(android.R.id.content), "Error: $message", Snackbar.LENGTH_LONG).show()
    }
}