package com.example.csmstudentapp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest

@Suppress("DEPRECATION", "PrivatePropertyName", "SameParameterValue")
class ChangePasswordActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var editTextNewPassword: TextInputEditText
    private lateinit var editTextConfirmPassword: TextInputEditText
    private lateinit var buttonSave: MaterialButton
    private lateinit var buttonCopyPassword: MaterialButton
    private lateinit var fabRefresh: FloatingActionButton
    private var adminName: String? = null

    private val TAG = "ChangePasswordActivity"

    @SuppressLint("SetTextI18n")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        try {
            setContentView(R.layout.activity_change_password)
            setupToolbar()
            initializeFirebase()
            initializeUI()

            adminName = intent.getStringExtra("ADMIN_NAME")
            if (adminName != null) {
                findViewById<TextView>(R.id.welcomeText).text = "You want to update your password, $adminName"
            }

            setupListeners()
        } catch (e: Exception) {
            handleCriticalError(e)
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        Log.d(TAG, "Toolbar set up")
    }

    private fun initializeFirebase() {
        db = FirebaseFirestore.getInstance()
        Log.d(TAG, "Firestore initialized")
    }

    private fun initializeUI() {
        editTextNewPassword = findViewById(R.id.editTextNewPassword)
            ?: throw IllegalStateException("editTextNewPassword not found")
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword)
            ?: throw IllegalStateException("editTextConfirmPassword not found")
        buttonSave = findViewById(R.id.buttonSave)
            ?: throw IllegalStateException("buttonSave not found")
        buttonCopyPassword = findViewById(R.id.buttonCopyPassword)
            ?: throw IllegalStateException("buttonCopyPassword not found")
        fabRefresh = findViewById(R.id.fabRefresh)
            ?: throw IllegalStateException("fabRefresh not found")
        Log.d(TAG, "UI elements initialized")
    }

    private fun setupListeners() {
        buttonSave.setOnClickListener {
            animateButton(it)
            changePassword()
        }

        buttonCopyPassword.setOnClickListener {
            animateButton(it)
            copyPassword()
        }

        fabRefresh.setOnClickListener {
            fabRefresh.animate().rotationBy(360f).setDuration(500).start()
            refreshFields()
        }
    }

    private fun animateButton(view: android.view.View) {
        try {
            val bounceAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce_scale_animation)
            view.startAnimation(bounceAnimation)
        } catch (e: Exception) {
            Log.w(TAG, "Button animation failed: ${e.message}")
        }
    }

    private fun changePassword() {
        editTextNewPassword.error = null
        editTextConfirmPassword.error = null

        val newPassword = editTextNewPassword.text.toString().trim()
        val confirmPassword = editTextConfirmPassword.text.toString().trim()

        if (!validatePasswords(newPassword, confirmPassword)) return

        buttonSave.isEnabled = false
        startLoadingAnimation()

        val newHash = hashPassword(newPassword)
        val adminDoc = adminName ?: run {
            showError("Admin name not provided")
            return
        }

        db.collection("admins").document(adminDoc)
            .update("master_password_hash", newHash)
            .addOnSuccessListener {
                Log.d(TAG, "Password updated successfully for $adminDoc")
                Toast.makeText(this, R.string.toast_password_changed, Toast.LENGTH_SHORT).show()
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating password: ${e.message}", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                buttonSave.isEnabled = true
                buttonSave.clearAnimation()
            }
    }

    private fun validatePasswords(newPassword: String, confirmPassword: String): Boolean {
        if (newPassword.isEmpty()) {
            showFieldError(editTextNewPassword, "New password cannot be empty")
            return false
        }
        if (confirmPassword.isEmpty()) {
            showFieldError(editTextConfirmPassword, "Confirm password cannot be empty")
            return false
        }
        if (newPassword != confirmPassword) {
            showFieldError(editTextConfirmPassword, "Passwords do not match")
            return false
        }
        if (newPassword.length < 6) {
            showFieldError(editTextNewPassword, "Password must be at least 6 characters")
            return false
        }
        return true
    }

    private fun showFieldError(field: TextInputEditText, message: String) {
        field.error = message
        try {
            field.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
        } catch (e: Exception) {
            Log.w(TAG, "Shake animation failed: ${e.message}")
        }
    }

    private fun startLoadingAnimation() {
        try {
            buttonSave.startAnimation(AnimationUtils.loadAnimation(this, R.anim.loading_spin))
        } catch (e: Exception) {
            Log.w(TAG, "Loading animation failed: ${e.message}")
        }
    }

    private fun copyPassword() {
        val newPassword = editTextNewPassword.text.toString().trim()
        if (newPassword.isEmpty()) {
            Toast.makeText(this, "No password to copy", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("New Password", newPassword)
        clipboard.setPrimaryClip(clip)
        Log.d(TAG, "Password copied to clipboard")
        Toast.makeText(this, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun refreshFields() {
        editTextNewPassword.text?.clear()
        editTextConfirmPassword.text?.clear()
        editTextNewPassword.error = null
        editTextConfirmPassword.error = null
        buttonSave.isEnabled = true
        Log.d(TAG, "Fields refreshed")
        Toast.makeText(this, "Fields cleared", Toast.LENGTH_SHORT).show()
    }

    private fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(password.toByteArray(Charsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Hashing error: ${e.message}", e)
            ""
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        buttonSave.isEnabled = true
        buttonSave.clearAnimation()
    }

    private fun handleCriticalError(e: Exception) {
        Log.e(TAG, "Error in onCreate: ${e.message}", e)
        Toast.makeText(this, "Error initializing activity: ${e.message}", Toast.LENGTH_LONG).show()
        finish()
    }
}