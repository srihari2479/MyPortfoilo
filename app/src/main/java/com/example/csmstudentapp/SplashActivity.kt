@file:Suppress("DEPRECATION")

package com.example.csmstudentapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.csmstudentapp.databinding.ActivitySplashBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("PrivatePropertyName")
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    private val TAG = "SplashActivity"
    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enable edge-to-edge display
        initializeBinding()
        setupWindowInsets()
        startAnimations()
        launchMainActivity()
    }

    private fun initializeBinding() {
        try {
            binding = ActivitySplashBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "SplashActivity started, layout set")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize binding: ${e.message}", e)
            showErrorSnackbar("Splash initialization failed: ${e.message}")
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun startAnimations() {
        // Fade in loading indicator
        with(binding.loadingIndicator) {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(500)
                .withEndAction { Log.d(TAG, "Lottie animation fade-in complete") }
                .start()
        }

        // Start glow animations for text views
        startBulbGlowAnimation(binding.textViewCsmCsd)
        startBulbGlowAnimation(binding.textViewDepartments)
    }

    private fun startBulbGlowAnimation(textView: android.widget.TextView) {
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in).apply {
            duration = 1000
        }
        val fadeOut = AnimationUtils.loadAnimation(this, android.R.anim.fade_out).apply {
            duration = 1000
        }

        fadeIn.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            @SuppressLint("UseKtx")
            override fun onAnimationEnd(animation: Animation?) {
                if (textView.visibility == View.VISIBLE) textView.startAnimation(fadeOut)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        fadeOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {}
            @SuppressLint("UseKtx")
            override fun onAnimationEnd(animation: Animation?) {
                if (textView.visibility == View.VISIBLE) textView.startAnimation(fadeIn)
            }
            override fun onAnimationRepeat(animation: Animation?) {}
        })

        textView.startAnimation(fadeIn)
    }

    private fun launchMainActivity() {
        lifecycleScope.launch {
            try {
                delay(3000) // 3-second delay
                Log.d(TAG, "Attempting to start MainActivity")
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                Log.d(TAG, "MainActivity started")
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch MainActivity: ${e.message}", e)
                showErrorSnackbar("Failed to load app: ${e.message}")
            }
        }
    }

    @SuppressLint("NewApi")
    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction("Retry") { restartApp() }
            .setBackgroundTint(resources.getColor(R.color.accent_teal, theme)) // Optional: Customize color
            .show()
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }
}