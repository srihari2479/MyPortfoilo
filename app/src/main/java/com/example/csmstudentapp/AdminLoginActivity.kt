@file:Suppress("DEPRECATION")

package com.example.csmstudentapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest

@Suppress("PrivatePropertyName")
class AdminLoginActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private val TAG = "AdminLoginActivity"

    private lateinit var textInputLayoutAdminName: TextInputLayout
    private lateinit var editTextAdminName: TextInputEditText
    private lateinit var textInputLayoutPassword: TextInputLayout
    private lateinit var editTextPassword: TextInputEditText
    private lateinit var buttonSubmit: MaterialButton
    private lateinit var buttonBack: MaterialButton

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        // Initialize Firebase
        db = FirebaseFirestore.getInstance()

        // Setup toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Initialize UI
        textInputLayoutAdminName = findViewById(R.id.textInputLayoutAdminName)
        editTextAdminName = findViewById(R.id.editTextAdminName)
        textInputLayoutPassword = findViewById(R.id.textInputLayoutPassword)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonSubmit = findViewById(R.id.buttonSubmit)
        buttonBack = findViewById(R.id.buttonBack)

        // Apply animations
        applyAnimations()

        // Setup listeners
        buttonSubmit.setOnClickListener {
            val adminNameInput = editTextAdminName.text.toString().trim()
            val enteredPassword = editTextPassword.text.toString().trim()

            if (validateInputs(adminNameInput, enteredPassword)) {
                authenticateAdmin(adminNameInput, enteredPassword)
            }
        }

        buttonBack.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun applyAnimations() {
        val fadeInAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        textInputLayoutAdminName.startAnimation(fadeInAnimation)
        textInputLayoutPassword.startAnimation(fadeInAnimation)
        buttonSubmit.startAnimation(fadeInAnimation)
        buttonBack.startAnimation(fadeInAnimation)
    }

    private fun validateInputs(adminName: String, password: String): Boolean {
        var isValid = true
        if (adminName.isEmpty()) {
            textInputLayoutAdminName.error = "Admin name cannot be empty"
            textInputLayoutAdminName.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
            isValid = false
        } else {
            textInputLayoutAdminName.error = null
        }
        if (password.isEmpty()) {
            textInputLayoutPassword.error = "Password cannot be empty"
            textInputLayoutPassword.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
            isValid = false
        } else {
            textInputLayoutPassword.error = null
        }
        return isValid
    }

    private fun authenticateAdmin(adminNameInput: String, enteredPassword: String) {
        val enteredHash = hashPassword(enteredPassword)

        db.collection("admins").document(adminNameInput).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val storedHash = document.getString("master_password_hash")
                    if (storedHash == enteredHash) {
                        Log.d(TAG, "Admin $adminNameInput logged in successfully")
                        launchAdminActivity(adminNameInput)
                    } else {
                        textInputLayoutPassword.error = getString(R.string.toast_incorrect_password)
                        textInputLayoutPassword.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
                    }
                } else {
                    fetchMasterPasswordHash { masterHash ->
                        if (masterHash == null) {
                            Snackbar.make(findViewById(android.R.id.content), "Master password not configured in Firebase", Snackbar.LENGTH_LONG).show()
                            return@fetchMasterPasswordHash
                        }
                        if (enteredHash == masterHash) {
                            val adminData = hashMapOf(
                                "master_password_hash" to enteredHash,
                                "created_at" to System.currentTimeMillis(),
                                "created_by" to "@srima12",
                                "is_master" to (adminNameInput == "@srima12")
                            )
                            db.collection("admins").document(adminNameInput)
                                .set(adminData)
                                .addOnSuccessListener {
                                    Log.d(TAG, "New admin $adminNameInput registered successfully")
                                    launchAdminActivity(adminNameInput)
                                }
                                .addOnFailureListener { e ->
                                    showError(e)
                                }
                        } else {
                            textInputLayoutPassword.error = "Invalid credentials. Use master password for login"
                            textInputLayoutPassword.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                showError(e)
            }
    }

    private fun fetchMasterPasswordHash(callback: (String?) -> Unit) {
        db.collection("config").document("admin").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val masterHash = document.getString("master_password_hash")
                    Log.d(TAG, "Master password hash fetched: $masterHash")
                    callback(masterHash)
                } else {
                    Log.w(TAG, "Master password config not found")
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching config: ${e.message}", e)
                callback(null)
            }
    }

    private fun launchAdminActivity(adminName: String) {
        val intent = Intent(this, AdminActivity::class.java)
        intent.putExtra("ADMIN_NAME", adminName)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showError(e: Exception) {
        Log.e(TAG, "Error: ${e.message}", e)
        Snackbar.make(findViewById(android.R.id.content), "Login failed: ${e.message}", Snackbar.LENGTH_LONG).show()
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
}