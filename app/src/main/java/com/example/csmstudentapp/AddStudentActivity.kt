package com.example.csmstudentapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.csmstudentapp.databinding.ActivityAddStudentBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.opencsv.CSVWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.util.Locale
import java.util.regex.Pattern

data class Student(
    val rollNumber: String,
    val name: String,
    val backlogs: String,
    val cgpa: Double,
    val percentage: Double,
    val imageBase64: String? = null
)

@Suppress("unused", "UnusedVariable", "RedundantSuppression")
class AddStudentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddStudentBinding
    private lateinit var db: FirebaseFirestore
    private var selectedImageUri: Uri? = null
    private var csvFile: File? = null
    private val students = mutableListOf<Student>()
    private val tag = "AddStudentActivity"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchFilePicker()
            } else {
                Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val pickFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri: Uri ->
                    processPdfFile(uri)
                }
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it
                previewImage(it)
            }
        }

    private val saveCsvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    csvFile?.inputStream()?.use { inputStream ->
                        inputStream.copyTo(outputStream)
                        Toast.makeText(this, getString(R.string.csv_saved, csvFile!!.name), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            PDFBoxResourceLoader.init(applicationContext)
            Log.d(tag, "PDFBoxResourceLoader initialized")

            Log.d(tag, "Layout set successfully")
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            binding.toolbar.setNavigationOnClickListener {
                finish()
            }
            Log.d(tag, "Toolbar initialized")

            db = FirebaseFirestore.getInstance()
            Log.d(tag, "Firestore initialized")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                selectedImageUri = savedInstanceState?.getParcelable("selectedImageUri", Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                selectedImageUri = savedInstanceState?.getParcelable("selectedImageUri")
            }
            selectedImageUri?.let { previewImage(it) }

            setupAnimations()

            binding.fabSave.setOnClickListener {
                try {
                    val bounceAnimation = AnimationUtils.loadAnimation(this, R.anim.bounce_scale_animation)
                    binding.fabSave.startAnimation(bounceAnimation)
                } catch (e: Exception) {
                    Log.w(tag, "Bounce animation failed: ${e.message}")
                }
                saveStudent()
            }

            binding.buttonAddImage.setOnClickListener {
                pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }

            binding.chipUploadPdf.setOnClickListener {
                if (checkStoragePermission()) {
                    launchFilePicker()
                } else {
                    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    requestPermissionLauncher.launch(permission)
                }
            }

            binding.chipUpdateStudents.setOnClickListener {
                if (students.isNotEmpty()) {
                    updateStudentsInFirebase()
                } else {
                    Toast.makeText(this, R.string.no_student_data, Toast.LENGTH_SHORT).show()
                }
            }

            binding.chipDownloadCsv.setOnClickListener {
                if (csvFile != null && csvFile!!.exists()) {
                    saveCsvToStorage()
                } else {
                    Toast.makeText(this, R.string.no_csv_file, Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, R.string.error_initializing_activity, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("selectedImageUri", selectedImageUri)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun previewImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap == null) {
                    Log.w(tag, "Failed to decode bitmap from URI: $uri")
                    Toast.makeText(this, R.string.error_previewing_image, Toast.LENGTH_SHORT).show()
                    return
                }
                binding.imageViewStudent.setImageBitmap(bitmap)
                binding.imageViewStudent.visibility = View.VISIBLE
                binding.imageViewStudent.startAnimation(
                    AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
                )
                Log.d(tag, "Image preview set successfully")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error previewing image: ${e.message}", e)
            Toast.makeText(this, R.string.error_previewing_image, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupAnimations() {
        try {
            val slideUpAnimation = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
            listOf(
                binding.editTextRollNumber,
                binding.editTextName,
                binding.editTextCgpa,
                binding.editTextBacklogs,
                binding.editTextPercentage,
                binding.fabSave,
                binding.imageViewStudent,
                binding.buttonAddImage
            ).forEach { view ->
                view.startAnimation(slideUpAnimation)
            }

            val scaleFadeAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_fade_in)
            binding.formTitle.startAnimation(scaleFadeAnimation)
            Log.d(tag, "Animations set up successfully")
        } catch (e: Exception) {
            Log.w(tag, "Failed to set up animations: ${e.message}", e)
        }
    }

    private fun saveStudent() {
        binding.inputRollNumber.error = null
        binding.inputName.error = null
        binding.inputCgpa.error = null
        binding.inputBacklogs.error = null
        binding.inputPercentage.error = null

        val rollNumber: String = binding.editTextRollNumber.text?.toString()?.trim()?.uppercase(Locale.getDefault()) ?: ""
        val name: String = binding.editTextName.text?.toString()?.trim() ?: ""
        val cgpaStr: String = binding.editTextCgpa.text?.toString()?.trim() ?: ""
        val backlogs: String = binding.editTextBacklogs.text?.toString()?.trim() ?: ""
        val percentageStr: String = binding.editTextPercentage.text?.toString()?.trim() ?: ""
        val imageBase64: String? = selectedImageUri?.let { uri -> uriToBase64(uri) }

        if (rollNumber.isEmpty() || name.isEmpty() || cgpaStr.isEmpty() || backlogs.isEmpty() || percentageStr.isEmpty()) {
            Toast.makeText(this, R.string.toast_fill_fields, Toast.LENGTH_SHORT).show()
            showErrorAnimation()
            return
        }

        val cgpa: Double = cgpaStr.toDoubleOrNull() ?: return run {
            Toast.makeText(this, R.string.toast_invalid_numbers, Toast.LENGTH_SHORT).show()
            showErrorAnimation()
        }
        val percentage: Double = percentageStr.toDoubleOrNull() ?: return run {
            Toast.makeText(this, R.string.toast_invalid_numbers, Toast.LENGTH_SHORT).show()
            showErrorAnimation()
        }

        if (cgpa < 0.0 || cgpa > 10.0) {
            binding.inputCgpa.error = getString(R.string.invalid_cgpa)
            binding.editTextCgpa.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_error))
            return
        }
        if (percentage < 0.0 || percentage > 100.0) {
            binding.inputPercentage.error = getString(R.string.invalid_percentage)
            binding.editTextPercentage.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake_error))
            return
        }

        binding.fabSave.isEnabled = false
        try {
            binding.fabSave.startAnimation(AnimationUtils.loadAnimation(this, R.anim.loading_spin))
        } catch (e: Exception) {
            Log.w(tag, "Loading animation failed: ${e.message}")
        }

        db.collection("STUDENTS").document(rollNumber).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Toast.makeText(this, R.string.toast_student_exists, Toast.LENGTH_SHORT).show()
                    binding.fabSave.isEnabled = true
                    binding.fabSave.clearAnimation()
                } else {
                    val student = hashMapOf<String, Any>(
                        "ROLL_NUMBER" to rollNumber,
                        "NAME" to name,
                        "CGPA" to cgpa,
                        "BACKLOGS" to backlogs,
                        "PERCENTAGE" to percentage,
                        "TIMESTAMP" to System.currentTimeMillis()
                    )
                    imageBase64?.let { studentData -> student["imageBase64"] = studentData }

                    db.collection("STUDENTS").document(rollNumber).set(student)
                        .addOnSuccessListener {
                            Log.d(tag, "Student added: $rollNumber")
                            Toast.makeText(this, R.string.toast_student_added, Toast.LENGTH_SHORT).show()
                            showSuccessAnimation()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.e(tag, "Error adding student: ${e.message}", e)
                            Toast.makeText(this, R.string.toast_error_adding_student, Toast.LENGTH_SHORT).show()
                            binding.fabSave.isEnabled = true
                            binding.fabSave.clearAnimation()
                            showErrorAnimation()
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(tag, "Error checking existence: ${e.message}", e)
                Toast.makeText(this, R.string.toast_error_checking_existence, Toast.LENGTH_SHORT).show()
                binding.fabSave.isEnabled = true
                binding.fabSave.clearAnimation()
                showErrorAnimation()
            }
    }

    private fun uriToBase64(uri: Uri): String {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap == null) {
                    Log.w(tag, "Failed to decode bitmap from URI: $uri")
                    return ""
                }
                ByteArrayOutputStream().use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    val byteArray: ByteArray = outputStream.toByteArray()
                    bitmap.recycle()
                    return Base64.encodeToString(byteArray, Base64.DEFAULT)
                }
            }
            return ""
        } catch (e: Exception) {
            Log.e(tag, "Error converting URI to Base64: ${e.message}", e)
            return ""
        }
    }

    private fun showErrorAnimation() {
        try {
            val shakeAnimation = AnimationUtils.loadAnimation(this, R.anim.shake_error)
            listOf(
                binding.editTextRollNumber,
                binding.editTextName,
                binding.editTextCgpa,
                binding.editTextBacklogs,
                binding.editTextPercentage
            ).forEach { editText ->
                if (editText.text.isNullOrEmpty()) {
                    editText.startAnimation(shakeAnimation)
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error animation failed: ${e.message}")
        }
    }

    private fun showSuccessAnimation() {
        try {
            val successAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_fade_out)
            binding.fabSave.startAnimation(successAnimation)
        } catch (e: Exception) {
            Log.w(tag, "Success animation failed: ${e.message}")
        }
    }

    @SuppressLint("InlinedApi")
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun launchFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickFileLauncher.launch(intent)
    }

    private fun processPdfFile(uri: Uri) {
        binding.progressBar.visibility = View.VISIBLE
        students.clear()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val extractedStudents = extractStudentData(uri)
                students.addAll(extractedStudents)
                if (students.isNotEmpty()) {
                    generateCsvFile()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddStudentActivity,
                            getString(R.string.extracted_students, students.size),
                            Toast.LENGTH_SHORT
                        ).show()
                        displayResults(students)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddStudentActivity,
                            R.string.no_student_data_in_pdf,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: ExceptionInInitializerError) {
                Log.e(tag, "PDFBox initialization failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddStudentActivity,
                        getString(R.string.error_processing_pdf, "PDF library initialization failed"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error processing PDF: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddStudentActivity,
                        getString(R.string.error_processing_pdf, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun extractStudentData(uri: Uri): List<Student> {
        val students = mutableListOf<Student>()

        contentResolver.openInputStream(uri)?.use { inputStream ->
            PDDocument.load(inputStream).use { document ->
                try {
                    val totalPages = document.numberOfPages

                    for (i in 0 until totalPages step 2) {
                        if (i + 1 >= totalPages) break

                        // Extract text from both pages
                        val stripper = PDFTextStripper().apply {
                            sortByPosition = true
                            setShouldSeparateByBeads(false)
                        }
                        stripper.startPage = i + 1
                        stripper.endPage = i + 1
                        val text1 = stripper.getText(document) ?: continue

                        stripper.startPage = i + 2
                        stripper.endPage = i + 2
                        val text2 = stripper.getText(document) ?: continue

                        // Extract Roll Number
                        val rollPattern = Pattern.compile("\\d{2}U\\d{2}A\\d{4}")
                        val rollMatcher = rollPattern.matcher(text1)
                        val rollNumber = if (rollMatcher.find()) rollMatcher.group(0) ?: "" else ""
                        if (rollNumber.isEmpty()) continue

                        // Extract Name
                        val namePattern = Pattern.compile("Name:\\s*(.*?)\\s*Report Date:", Pattern.DOTALL)
                        val nameMatcher = namePattern.matcher(text1)
                        val name = if (nameMatcher.find()) nameMatcher.group(1)?.trim() ?: "" else ""

                        // Extract CGPA
                        val cgpaPattern = Pattern.compile("Secured CGPA:\\s*([\\d.]+)")
                        val cgpaMatcher = cgpaPattern.matcher(text2)
                        val cgpa = if (cgpaMatcher.find()) cgpaMatcher.group(1)?.toDoubleOrNull() ?: 0.0 else 0.0

                        // Extract Percentage
                        val percentagePattern = Pattern.compile("Equivalent Percentage:\\s*([\\d.]+)")
                        val percentageMatcher = percentagePattern.matcher(text2)
                        val percentage = if (percentageMatcher.find()) {
                            percentageMatcher.group(1)?.toDoubleOrNull() ?: (cgpa * 10 - 7.5)
                        } else {
                            (cgpa * 10 - 7.5)
                        }

                        // Extract Backlogs
                        val fullText = "$text1\n$text2"
                        val backlogInfo = extractBacklogsFromText(fullText)

                        students.add(
                            Student(
                                rollNumber = rollNumber,
                                name = name,
                                backlogs = backlogInfo,
                                cgpa = cgpa,
                                percentage = String.format(Locale.US, "%.2f", percentage).toDouble(),
                                imageBase64 = null
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("PDF_PROCESS", "Error parsing PDF: ${e.message}", e)
                }
            }
        } ?: run {
            Log.w("PDF_PROCESS", "Failed to open input stream for URI: $uri")
            return emptyList()
        }

        return students
    }

    private fun extractBacklogsFromText(text: String): String {
        val backlogCourses = mutableSetOf<String>()
        val lines = text.replace("\r", "").split("\n")

        // Regex to match table-like rows with course code, name, and grade 'F'
        val pattern = Regex("""([A-Z]{2,}\d{2,})\s+(.+?)\s+F\b""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val match = pattern.find(line)
            if (match != null) {
                val courseName = match.groupValues[2].trim().replace(Regex("\\s+"), " ")
                if (courseName.length > 4) {
                    backlogCourses.add(courseName)
                }
            }
        }

        // Attempt table-like parsing for more accuracy
        val tablePattern = Regex("""^\s*(\d+)\s+([A-Z]{2,}\d{2,})\s+(.+?)\s+([A-F])\s*$""", RegexOption.MULTILINE)
        val tableMatches = tablePattern.findAll(text)
        for (match in tableMatches) {
            val serialNumber = match.groupValues[1]
            val courseCode = match.groupValues[2]
            val courseName = match.groupValues[3].trim().replace(Regex("\\s+"), " ")
            val grade = match.groupValues[4]
            if (grade == "F" && courseName.length > 4) {
                backlogCourses.add(courseName)
            }
        }

        return if (backlogCourses.isNotEmpty())
            "${backlogCourses.size} - [${backlogCourses.joinToString(", ")}]"
        else
            "0 - []"
    }

    private suspend fun generateCsvFile() {
        try {
            val fileName = "student_backlog_data_${System.currentTimeMillis()}.csv"
            csvFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)

            withContext(Dispatchers.IO) {
                CSVWriter(FileWriter(csvFile)).use { writer: CSVWriter ->
                    writer.writeNext(arrayOf("ROLL_NUMBER", "NAME", "BACKLOGS", "CGPA", "PERCENTAGE"))
                    students.forEach { student: Student ->
                        writer.writeNext(
                            arrayOf(
                                student.rollNumber,
                                student.name,
                                student.backlogs,
                                student.cgpa.toString(),
                                student.percentage.toString()
                            )
                        )
                    }
                }
            }
            Log.d(tag, "CSV file generated: ${csvFile?.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "Error generating CSV: ${e.message}", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AddStudentActivity, R.string.error_generating_csv, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCsvToStorage() {
        try {
            if (csvFile != null && csvFile!!.exists()) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    saveCsvLauncher.launch(csvFile!!.name)
                }
            } else {
                Toast.makeText(this, R.string.no_csv_file, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error saving CSV: ${e.message}", e)
            Toast.makeText(this, R.string.error_saving_csv, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStudentsInFirebase() {
        binding.progressBar.visibility = View.VISIBLE
        var successCount = 0
        var failureCount = 0

        students.forEach { student: Student ->
            try {
                val studentData = hashMapOf<String, Any>(
                    "ROLL_NUMBER" to student.rollNumber,
                    "NAME" to student.name,
                    "BACKLOGS" to student.backlogs,
                    "CGPA" to student.cgpa,
                    "PERCENTAGE" to student.percentage,
                    "TIMESTAMP" to System.currentTimeMillis()
                )
                student.imageBase64?.let { image -> studentData["imageBase64"] = image }

                db.collection("STUDENTS").document(student.rollNumber)
                    .set(studentData)
                    .addOnSuccessListener {
                        successCount++
                        if (successCount + failureCount == students.size) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(
                                this,
                                getString(R.string.updated_students, successCount, failureCount),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        failureCount++
                        Log.e(tag, "Error updating student ${student.rollNumber}: ${e.message}", e)
                        if (successCount + failureCount == students.size) {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(
                                this,
                                getString(R.string.updated_students, successCount, failureCount),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
            } catch (e: Exception) {
                Log.e(tag, "Error preparing student data for ${student.rollNumber}: ${e.message}", e)
                failureCount++
            }
        }
    }

    private fun displayResults(students: List<Student>) {
        val backlogCount = students.count { it.backlogs != "0 - []" }
        val preview = students.take(5).joinToString("\n") { student ->
            getString(
                R.string.student_preview,
                student.rollNumber,
                student.name,
                student.cgpa,
                student.backlogs
            )
        }
        binding.resultTextView.text = getString(
            R.string.extraction_results,
            students.size,
            backlogCount,
            preview
        )
    }
}