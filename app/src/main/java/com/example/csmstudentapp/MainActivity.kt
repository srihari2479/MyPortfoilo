package com.example.csmstudentapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import de.hdodenhof.circleimageview.CircleImageView

@Suppress("PrivatePropertyName", "DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private var masterPasswordHash: String? = null
    private var isPasswordLoaded = false

    private lateinit var editTextRollNumber: TextInputEditText
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var cardViewStudentDetails: View
    private lateinit var textViewRollNumber: android.widget.TextView
    private lateinit var textViewName: android.widget.TextView
    private lateinit var textViewCgpa: android.widget.TextView
    private lateinit var textViewBacklogs: android.widget.TextView
    private lateinit var textViewPercentage: android.widget.TextView
    private lateinit var imageViewStudent: CircleImageView
    private lateinit var textViewDiet: android.widget.TextView

    private val TAG = "MainActivity"

    @SuppressLint("SetTextI18n", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        try {
            setContentView(R.layout.activity_main)
            setupToolbar()
            initializeFirebase()
            initializeUI()
            setupListeners()
            fetchMasterPasswordHash { /* Initial fetch */ }

            if (savedInstanceState != null) {
                editTextRollNumber.setText(savedInstanceState.getString("ROLL_NUMBER", ""))
                val wasCardVisible = savedInstanceState.getBoolean("CARD_VISIBLE", false)
                if (wasCardVisible) {
                    val rollNumber = savedInstanceState.getString("ROLL_NUMBER", "")
                    if (rollNumber.isNotEmpty()) {
                        searchStudent(rollNumber)
                    }
                }
            }
        } catch (e: Exception) {
            handleCriticalError(e)
        }
    }

    @SuppressLint("UseKtx")
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("ROLL_NUMBER", editTextRollNumber.text.toString())
        outState.putBoolean("CARD_VISIBLE", cardViewStudentDetails.visibility == View.VISIBLE)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.setNavigationOnClickListener {
            Log.d(TAG, "Toolbar navigation icon clicked (decorative)")
        }
    }

    private fun initializeFirebase() {
        db = FirebaseFirestore.getInstance()
        Log.d(TAG, "Firestore initialized")
    }

    private fun initializeUI() {
        editTextRollNumber = findViewById(R.id.editTextRollNumber)
            ?: throw IllegalStateException("editTextRollNumber not found")
        fabRefresh = findViewById(R.id.fabRefresh)
            ?: throw IllegalStateException("fabRefresh not found")
        cardViewStudentDetails = findViewById(R.id.cardViewStudentDetails)
            ?: throw IllegalStateException("cardViewStudentDetails not found")
        textViewRollNumber = findViewById(R.id.textViewRollNumber)
            ?: throw IllegalStateException("textViewRollNumber not found")
        textViewName = findViewById(R.id.textViewName)
            ?: throw IllegalStateException("textViewName not found")
        textViewCgpa = findViewById(R.id.textViewCgpa)
            ?: throw IllegalStateException("textViewCgpa not found")
        textViewBacklogs = findViewById(R.id.textViewBacklogs)
            ?: throw IllegalStateException("textViewBacklogs not found")
        textViewPercentage = findViewById(R.id.textViewPercentage)
            ?: throw IllegalStateException("textViewPercentage not found")
        imageViewStudent = findViewById(R.id.imageViewStudent)
            ?: throw IllegalStateException("imageViewStudent not found")
        textViewDiet = findViewById(R.id.textViewDiet)
            ?: throw IllegalStateException("textViewDiet not found")

        textViewDiet.visibility = View.VISIBLE
        fabRefresh.visibility = View.VISIBLE
        startBulbGlowAnimation()
    }

    private fun setupListeners() {
        fabRefresh.setOnClickListener {
            resetUI()
        }

        editTextRollNumber.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val rollNumber = editTextRollNumber.text.toString().trim().uppercase()
                searchStudent(rollNumber)
                return@setOnKeyListener true
            }
            false
        }
    }

    private fun resetUI() {
        fabRefresh.clearAnimation()
        fabRefresh.animate().rotationBy(360f).setDuration(500).withEndAction {
            fabRefresh.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_pulse))
        }.start()
        editTextRollNumber.text?.clear()
        editTextRollNumber.error = null
        cardViewStudentDetails.visibility = View.GONE
        imageViewStudent.visibility = View.GONE
        imageViewStudent.setImageBitmap(null) // Clear previous image
        textViewDiet.visibility = View.VISIBLE
        fabRefresh.visibility = View.VISIBLE
        startBulbGlowAnimation()
        ImageCache.clearAll() // Clear image cache on reset
        Snackbar.make(findViewById(android.R.id.content), "Reset complete.", Snackbar.LENGTH_SHORT).show()
    }

    private fun startBulbGlowAnimation() {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out)

        fadeIn.duration = 1000
        fadeOut.duration = 1000

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            @SuppressLint("UseKtx")
            override fun onAnimationEnd(animation: Animation?) {
                if (textViewDiet.visibility == View.VISIBLE) {
                    textViewDiet.startAnimation(fadeOut)
                }
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            @SuppressLint("UseKtx")
            override fun onAnimationEnd(animation: Animation?) {
                if (textViewDiet.visibility == View.VISIBLE) {
                    textViewDiet.startAnimation(fadeIn)
                }
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        textViewDiet.startAnimation(fadeIn)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.nav_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_admin_login -> {
                Log.d(TAG, "Admin Login menu item clicked")
                val intent = Intent(this, AdminLoginActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                true
            }
            R.id.action_report -> {
                Log.d(TAG, "Report menu item clicked")
                startActivity(Intent(this, ReportActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun searchStudent(rollNumber: String) {
        editTextRollNumber.error = null
        if (rollNumber.isEmpty()) {
            editTextRollNumber.error = "Please enter a roll number"
            editTextRollNumber.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
            return
        }

        db.collection("STUDENTS").document(rollNumber).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    displayStudentDetails(document)
                } else {
                    showStudentNotFound()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching student: ${e.message}", e)
                showStudentFetchError()
            }
    }

    @SuppressLint("SetTextI18n")
    private fun displayStudentDetails(document: com.google.firebase.firestore.DocumentSnapshot) {
        cardViewStudentDetails.visibility = View.VISIBLE
        cardViewStudentDetails.startAnimation(AnimationUtils.loadAnimation(this, R.anim.card_enter))
        textViewRollNumber.text = "${getString(R.string.label_roll_number)}${document.getString("ROLL_NUMBER") ?: document.id}"
        textViewName.text = "${getString(R.string.label_name)}${document.getString("NAME") ?: "N/A"}"
        textViewCgpa.text = "${getString(R.string.label_cgpa)}${document.getDouble("CGPA") ?: 0.0}"
        textViewBacklogs.text = "${getString(R.string.label_backlogs)}${document.getString("BACKLOGS") ?: "0"}"
        textViewPercentage.text = "${getString(R.string.label_percentage)}${document.getDouble("PERCENTAGE") ?: 0.0}"

        textViewDiet.visibility = View.GONE
        fabRefresh.visibility = View.VISIBLE

        val imageBase64 = document.getString("imageBase64")
        Log.d(TAG, "ImageBase64 fetched: ${imageBase64?.take(50)}...")
        if (!imageBase64.isNullOrEmpty()) {
            loadStudentImage(imageBase64)
            imageViewStudent.isClickable = true
            imageViewStudent.isFocusable = true
            imageViewStudent.setOnClickListener {
                val rollNumber = document.getString("ROLL_NUMBER") ?: document.id
                ImageCache.storeImage(rollNumber, imageBase64)
                val intent = Intent(this, FullScreenImageActivity::class.java)
                intent.putExtra("ROLL_NUMBER", rollNumber)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        } else {
            imageViewStudent.visibility = View.GONE
            Log.d(TAG, "No image data found for student")
        }
    }

    private fun loadStudentImage(imageBase64: String) {
        try {
            val cleanBase64 = if (imageBase64.startsWith("data:image")) {
                imageBase64.substringAfter(",")
            } else {
                imageBase64
            }
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            if (bitmap != null) {
                imageViewStudent.setImageBitmap(bitmap)
                imageViewStudent.visibility = View.VISIBLE
                imageViewStudent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))
                Log.d(TAG, "Image loaded successfully")
            } else {
                Log.e(TAG, "Bitmap is null - decoding failed")
                imageViewStudent.visibility = View.GONE
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid Base64 string: ${e.message}", e)
            imageViewStudent.visibility = View.GONE
            Snackbar.make(findViewById(android.R.id.content), "Invalid image data", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding image: ${e.message}", e)
            imageViewStudent.visibility = View.GONE
            Snackbar.make(findViewById(android.R.id.content), "Error loading image", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showStudentNotFound() {
        cardViewStudentDetails.visibility = View.GONE
        imageViewStudent.visibility = View.GONE
        textViewDiet.visibility = View.VISIBLE
        fabRefresh.visibility = View.VISIBLE
        startBulbGlowAnimation()
        Snackbar.make(findViewById(android.R.id.content), R.string.toast_student_not_found, Snackbar.LENGTH_LONG).show()
    }

    private fun showStudentFetchError() {
        cardViewStudentDetails.visibility = View.GONE
        imageViewStudent.visibility = View.GONE
        textViewDiet.visibility = View.VISIBLE
        fabRefresh.visibility = View.VISIBLE
        startBulbGlowAnimation()
        Snackbar.make(findViewById(android.R.id.content), R.string.toast_error_fetching_data, Snackbar.LENGTH_LONG).show()
    }

    private fun fetchMasterPasswordHash(callback: (String?) -> Unit) {
        db.collection("config").document("admin").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    masterPasswordHash = document.getString("master_password_hash")
                    isPasswordLoaded = true
                    Log.d(TAG, "Master password hash fetched: $masterPasswordHash")
                    callback(masterPasswordHash)
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

    private fun handleCriticalError(e: Exception) {
        Log.e(TAG, "Critical error in onCreate: ${e.message}", e)
        Snackbar.make(findViewById(android.R.id.content), "An error occurred: ${e.message}", Snackbar.LENGTH_LONG)
            .setAction("Restart") { restartApp() }.show()
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}