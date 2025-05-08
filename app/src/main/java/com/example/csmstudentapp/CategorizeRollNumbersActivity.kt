package com.example.csmstudentapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
class CategorizeRollNumbersActivity : AppCompatActivity() {
    private lateinit var radioGroupOption: android.widget.RadioGroup
    private lateinit var editTextDepartmentName: TextInputEditText
    private lateinit var editTextSearchDepartment: TextInputEditText
    private lateinit var buttonSubmit: MaterialButton
    private lateinit var recyclerViewExistingDepartments: androidx.recyclerview.widget.RecyclerView
    private lateinit var newDepartmentLayout: TextInputLayout
    private lateinit var searchDepartmentLayout: TextInputLayout
    private lateinit var deptSelectionAdapter: DepartmentSelectionAdapter
    private var allDepartments: List<Department> = emptyList()
    private lateinit var selectedRollNumbers: MutableSet<String>
    private var searchJob: Job? = null

    @Suppress("UNCHECKED_CAST")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categorize_roll_numbers)

        // Setup Toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finishWithTransition()
        }

        // Initialize Views
        radioGroupOption = findViewById(R.id.radioGroupOption)
        editTextDepartmentName = findViewById(R.id.editTextDepartmentName)
        editTextSearchDepartment = findViewById(R.id.editTextSearchDepartment)
        buttonSubmit = findViewById(R.id.buttonSubmit)
        recyclerViewExistingDepartments = findViewById(R.id.recyclerViewExistingDepartments)
        newDepartmentLayout = findViewById(R.id.newDepartmentLayout)
        searchDepartmentLayout = findViewById(R.id.searchDepartmentLayout)

        // Get Data from Intent
        selectedRollNumbers = intent.getSerializableExtra("selectedRollNumbers") as? MutableSet<String> ?: mutableSetOf()
        allDepartments = intent.getSerializableExtra("allDepartments") as? List<Department> ?: emptyList()

        // Setup RecyclerView
        recyclerViewExistingDepartments.layoutManager = LinearLayoutManager(this)
        deptSelectionAdapter = DepartmentSelectionAdapter(allDepartments.map { it.name })
        recyclerViewExistingDepartments.adapter = deptSelectionAdapter

        // Setup Listeners
        setupListeners()

        // Initial UI setup
        updateUIForRadioSelection(radioGroupOption.checkedRadioButtonId)
    }

    private fun setupListeners() {
        radioGroupOption.setOnCheckedChangeListener { _, checkedId ->
            updateUIForRadioSelection(checkedId)
        }

        editTextSearchDepartment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                debounceSearch(query)
            }
        })

        buttonSubmit.setOnClickListener {
            submitAction()
        }
    }

    private fun updateUIForRadioSelection(checkedId: Int) {
        when (checkedId) {
            R.id.radioNewDepartment -> {
                newDepartmentLayout.visibility = View.VISIBLE
                searchDepartmentLayout.visibility = View.GONE
                recyclerViewExistingDepartments.visibility = View.GONE
            }
            R.id.radioExistingDepartment -> {
                newDepartmentLayout.visibility = View.GONE
                searchDepartmentLayout.visibility = View.VISIBLE
                recyclerViewExistingDepartments.visibility = View.VISIBLE
                lifecycleScope.launch(Dispatchers.Main) {
                    try {
                        val departmentNames = withContext(Dispatchers.Default) {
                            allDepartments.map { it.name }
                        }
                        deptSelectionAdapter.updateDepartments(departmentNames)
                    } catch (e: Exception) {
                        showSnackbar("Failed to load departments: ${e.message}")
                    }
                }
            }
        }
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                val filteredDepartments = withContext(Dispatchers.Default) {
                    if (query.isEmpty()) {
                        allDepartments.map { it.name }
                    } else {
                        allDepartments.filter {
                            it.name.contains(query, ignoreCase = true) ||
                                    it.rollNumbers.any { roll -> roll.contains(query, ignoreCase = true) }
                        }.map { it.name }
                    }
                }
                deptSelectionAdapter.updateDepartments(filteredDepartments)
            } catch (e: Exception) {
                showSnackbar("Search failed: ${e.message}")
            }
        }
    }

    private fun submitAction() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                buttonSubmit.isEnabled = false
                when (radioGroupOption.checkedRadioButtonId) {
                    R.id.radioNewDepartment -> {
                        val departmentName = editTextDepartmentName.text.toString().trim()
                        if (departmentName.isNotEmpty()) {
                            if (allDepartments.any { it.name.equals(departmentName, ignoreCase = true) }) {
                                showSnackbar("Department '$departmentName' already exists.")
                            } else {
                                val resultIntent = Intent().apply {
                                    putExtra("departmentName", departmentName)
                                    putExtra("isNewDepartment", true)
                                }
                                setResult(RESULT_OK, resultIntent)
                                finishWithTransition()
                            }
                        } else {
                            editTextDepartmentName.error = "Enter a department name"
                        }
                    }
                    R.id.radioExistingDepartment -> {
                        val selectedDept = deptSelectionAdapter.selectedDepartment
                        if (selectedDept.isNotEmpty()) {
                            val resultIntent = Intent().apply {
                                putExtra("departmentName", selectedDept)
                                putExtra("isNewDepartment", false)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finishWithTransition()
                        } else {
                            showSnackbar("Please select a department.")
                        }
                    }
                }
            } catch (e: Exception) {
                showSnackbar("Submission failed: ${e.message}")
            } finally {
                buttonSubmit.isEnabled = true
            }
        }
    }

    private fun finishWithTransition() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        searchJob?.cancel()
        finishWithTransition()
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(R.id.cardViewContent), message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}