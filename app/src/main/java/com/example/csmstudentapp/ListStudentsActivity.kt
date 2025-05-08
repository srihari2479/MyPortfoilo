@file:Suppress("DEPRECATION", "unused")

package com.example.csmstudentapp

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Serializable

@Suppress("DEPRECATION")
class ListStudentsActivity : AppCompatActivity() {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var recyclerViewDepartments: RecyclerView
    private lateinit var editTextSearch: TextInputEditText
    private lateinit var searchInputLayout: TextInputLayout
    private lateinit var textViewTotalCount: MaterialTextView
    private lateinit var textViewSelectedCount: MaterialTextView
    private lateinit var buttonCategorize: MaterialButton
    private lateinit var buttonUnselectAll: MaterialButton
    private lateinit var loadingProgressBar: android.widget.ProgressBar
    private lateinit var departmentAdapter: DepartmentAdapter
    private val allRollNumbers = mutableListOf<String>()
    private var allDepartments = emptyList<Department>()
    internal val selectedRollNumbers = mutableSetOf<String>()
    private val departments = mutableListOf<Department>()
    private lateinit var fullRollNumberSnapshot: Department
    private var loadDataJob: Job? = null
    private var searchJob: Job? = null
    private val tag = "ListStudentsActivity"
    internal val maxSelectionLimit = 120
    private var isInitialDataLoaded = false
    private var lastFilteredDepartments: List<Department> = emptyList()
    private var lastBackPressTime = 0L

    private val categorizeResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val departmentName = data.getStringExtra("departmentName") ?: return@let
                val isNewDepartment = data.getBooleanExtra("isNewDepartment", false)
                if (isNewDepartment) {
                    categorizeRollNumbers(departmentName)
                } else {
                    moveToExistingDepartment(departmentName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list_students)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finishWithTransition()
        }

        recyclerViewDepartments = findViewById(R.id.recyclerViewDepartments)
        editTextSearch = findViewById(R.id.editTextSearch)
        searchInputLayout = findViewById(R.id.searchInputLayout)
        textViewTotalCount = findViewById(R.id.textViewTotalCount)
        textViewSelectedCount = findViewById(R.id.textViewSelectedCount)
        buttonCategorize = findViewById(R.id.buttonCategorize)
        buttonUnselectAll = findViewById(R.id.buttonUnselectAll)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)

        setupRecyclerView()
        setupListeners()

        val showLoading = intent.getBooleanExtra("SHOW_LOADING", false)
        if (showLoading) {
            loadingProgressBar.visibility = View.VISIBLE
            loadInitialDataWithAnimation()
        } else {
            loadInitialData()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!isInitialDataLoaded) {
                loadDataJob?.cancel()
                loadingProgressBar.visibility = View.GONE
                finishWithTransition()
            } else {
                handleBackNavigation()
            }
        }
    }

    private fun setupRecyclerView() {
        recyclerViewDepartments.layoutManager = LinearLayoutManager(this).apply { initialPrefetchItemCount = 15 }
        recyclerViewDepartments.setHasFixedSize(true)
        recyclerViewDepartments.itemAnimator = null
        recyclerViewDepartments.setRecycledViewPool(RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 50) })
        fullRollNumberSnapshot = Department("All Roll Numbers", mutableListOf())
        departments.add(fullRollNumberSnapshot)
        departmentAdapter = DepartmentAdapter()
        recyclerViewDepartments.adapter = departmentAdapter
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

    private fun loadInitialData() {
        loadDataJob?.cancel()
        loadDataJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                if (!isNetworkAvailable()) {
                    handleLoadingError("No internet connection. Please check your network.")
                    return@launch
                }
                val studentSnapshot = withContext(Dispatchers.IO) {
                    db.collection("STUDENTS").get().await()
                }
                val deptSnapshot = withContext(Dispatchers.IO) {
                    db.collection("DEPARTMENTS").get().await()
                }
                withContext(Dispatchers.Default) {
                    updateStudents(studentSnapshot)
                    updateDepartments(deptSnapshot)
                    isInitialDataLoaded = true
                }
                showUiElements()
            } catch (e: CancellationException) {
                Log.d(tag, "Loading cancelled")
                finishWithTransition()
            } catch (e: Exception) {
                Log.e(tag, "Error fetching initial data: ${e.message}", e)
                handleLoadingError("Failed to load data: ${e.message}")
            }
        }
    }

    private fun loadInitialDataWithAnimation() {
        loadDataJob?.cancel()
        loadDataJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                if (!isNetworkAvailable()) {
                    handleLoadingError("No internet connection. Please check your network.")
                    return@launch
                }
                val progressJob = launch { animateLoadingProgress() }
                val studentSnapshot = withContext(Dispatchers.IO) {
                    db.collection("STUDENTS").get().await()
                }
                val deptSnapshot = withContext(Dispatchers.IO) {
                    db.collection("DEPARTMENTS").get().await()
                }
                withContext(Dispatchers.Default) {
                    updateProgress(50)
                    updateStudents(studentSnapshot)
                    updateProgress(90)
                    updateDepartments(deptSnapshot)
                    isInitialDataLoaded = true
                }
                progressJob.join()
                showUiElements()
                loadingProgressBar.visibility = View.GONE
            } catch (e: CancellationException) {
                Log.d(tag, "Loading cancelled")
                loadingProgressBar.visibility = View.GONE
                finishWithTransition()
            } catch (e: Exception) {
                Log.e(tag, "Error fetching initial data: ${e.message}", e)
                handleLoadingError("Failed to load data: ${e.message}")
            }
        }
    }

    private suspend fun animateLoadingProgress() {
        withContext(Dispatchers.Main) {
            loadingProgressBar.visibility = View.VISIBLE
            loadingProgressBar.progress = 0
            var progress = 0
            while (progress < 100 && isActive) {
                progress += 2
                loadingProgressBar.progress = progress
                delay(50)
            }
            if (isActive) loadingProgressBar.progress = 100
        }
    }

    private fun updateProgress(progress: Int) {
        if (loadingProgressBar.progress < progress) {
            loadingProgressBar.progress = progress
        }
    }

    private suspend fun showUiElements() {
        withContext(Dispatchers.Main) {
            recyclerViewDepartments.visibility = View.VISIBLE
            searchInputLayout.visibility = View.VISIBLE
            textViewTotalCount.visibility = View.VISIBLE
            textViewSelectedCount.visibility = View.VISIBLE
            buttonCategorize.visibility = View.VISIBLE
            buttonUnselectAll.visibility = View.VISIBLE
            recyclerViewDepartments.startAnimation(
                AnimationUtils.loadAnimation(this@ListStudentsActivity, android.R.anim.fade_in)
            )
            searchInputLayout.startAnimation(
                AnimationUtils.loadAnimation(this@ListStudentsActivity, android.R.anim.fade_in)
            )
        }
    }

    private suspend fun handleLoadingError(message: String) {
        withContext(Dispatchers.Main) {
            loadingProgressBar.visibility = View.GONE
            showSnackbar(message)
            finishWithTransition()
        }
    }

    private fun setupListeners() {
        buttonCategorize.setOnClickListener {
            if (selectedRollNumbers.isNotEmpty()) {
                val intent = Intent(this, CategorizeRollNumbersActivity::class.java).apply {
                    putExtra("selectedRollNumbers", HashSet(selectedRollNumbers))
                    putExtra("allDepartments", ArrayList(allDepartments))
                }
                categorizeResultLauncher.launch(intent)
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                showSnackbar("No roll numbers selected.")
            }
        }

        buttonUnselectAll.setOnClickListener {
            if (selectedRollNumbers.isNotEmpty()) {
                val previousCount = selectedRollNumbers.size
                selectedRollNumbers.clear()
                departmentAdapter.notifyItemRangeChanged(0, departmentAdapter.itemCount, "unselect")
                updateSelectedCount()
                showSnackbar("$previousCount roll numbers unselected.")
            } else {
                showSnackbar("No roll numbers selected.")
            }
        }

        editTextSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = editTextSearch.text.toString().trim()
                debounceSearch(query)
                true
            } else {
                false
            }
        }
    }

    private fun debounceSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch(Dispatchers.Main) {
            try {
                loadingProgressBar.visibility = View.VISIBLE
                withContext(Dispatchers.Default) {
                    filterData(query)
                }
                loadingProgressBar.visibility = View.GONE
            } catch (e: Exception) {
                loadingProgressBar.visibility = View.GONE
                showSnackbar("Search failed: ${e.message}")
            }
        }
    }

    private fun filterData(query: String) {
        departments.clear()
        if (query.isEmpty()) {
            departments.add(fullRollNumberSnapshot)
            departments.addAll(allDepartments)
        } else {
            val rollNumberMatch = allRollNumbers.find { it.equals(query, ignoreCase = true) }
            if (rollNumberMatch != null) {
                departments.add(Department("Matching Roll Number", mutableListOf(rollNumberMatch)))
                showSnackbar("Roll number '$rollNumberMatch' found.")
            } else {
                val filteredRollNumbers = allRollNumbers.filter { it.contains(query, ignoreCase = true) }
                if (filteredRollNumbers.isNotEmpty()) {
                    departments.add(Department("Matching Roll Numbers (${filteredRollNumbers.size})", filteredRollNumbers.toMutableList()))
                    showSnackbar("Found ${filteredRollNumbers.size} roll numbers matching '$query'.")
                }
            }

            val filteredDepartments = allDepartments.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.rollNumbers.any { roll -> roll.contains(query, ignoreCase = true) }
            }
            if (filteredDepartments.isNotEmpty()) {
                departments.addAll(filteredDepartments.map { Department(it.name, it.rollNumbers.toMutableList()) })
                showSnackbar("Found ${filteredDepartments.size} departments matching '$query'.")
            }

            if (departments.isEmpty()) {
                showSnackbar("No matches found for '$query'.")
            }
        }
        lastFilteredDepartments = departments.toList()
        departmentAdapter.submitList(lastFilteredDepartments)
        updateSelectedCount()
    }

    private fun updateStudents(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        val newRollNumbers = snapshot.documents.map { it.id }.sorted()
        allRollNumbers.clear()
        allRollNumbers.addAll(newRollNumbers)
        fullRollNumberSnapshot.rollNumbers.apply {
            clear()
            addAll(newRollNumbers)
        }
        departments.clear()
        departments.add(fullRollNumberSnapshot)
        if (allDepartments.isNotEmpty()) {
            departments.addAll(allDepartments)
        }
        lastFilteredDepartments = departments.toList()
        departmentAdapter.submitList(lastFilteredDepartments)
        updateTotalCount()
        updateSelectedCount()
    }

    private fun updateDepartments(snapshot: com.google.firebase.firestore.QuerySnapshot) {
        val newDepartments = if (snapshot.isEmpty) {
            listOf(Department("Uncategorized", allRollNumbers.toMutableList()))
        } else {
            snapshot.map { doc ->
                val name = doc.id
                val rollNumbers = (doc.get("rollNumbers") as? List<*>)?.filterIsInstance<String>()
                    ?.filter { it in allRollNumbers }?.toMutableList() ?: mutableListOf()
                Department(name, rollNumbers)
            }
        }
        allDepartments = newDepartments
        departments.clear()
        departments.add(fullRollNumberSnapshot)
        departments.addAll(allDepartments)
        lastFilteredDepartments = departments.toList()
        departmentAdapter.submitList(lastFilteredDepartments)
        updateTotalCount()
        updateSelectedCount()
    }

    private fun updateTotalCount() {
        textViewTotalCount.text = getString(R.string.total_roll_numbers, allRollNumbers.size)
    }

    internal fun updateSelectedCount() {
        textViewSelectedCount.text = getString(R.string.selected_roll_numbers, selectedRollNumbers.size)
    }

    private fun categorizeRollNumbers(departmentName: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                loadingProgressBar.visibility = View.VISIBLE
                val newDepartment: Department
                withContext(Dispatchers.Default) {
                    departments.forEach { it.rollNumbers.removeAll(selectedRollNumbers) }
                    departments.removeAll { it.rollNumbers.isEmpty() && it.name != "All Roll Numbers" }
                    newDepartment = Department(departmentName, selectedRollNumbers.toMutableList())
                    departments.add(newDepartment)
                    allDepartments = departments.filter { it.name != "All Roll Numbers" }.toList()
                    fullRollNumberSnapshot.rollNumbers.clear()
                    fullRollNumberSnapshot.rollNumbers.addAll(allRollNumbers)
                    departments.clear()
                    departments.add(fullRollNumberSnapshot)
                    departments.addAll(allDepartments)
                    lastFilteredDepartments = departments.toList()
                    departmentAdapter.submitList(lastFilteredDepartments)
                    selectedRollNumbers.clear()
                }
                updateSelectedCount()
                loadingProgressBar.visibility = View.GONE
                showSnackbar("${newDepartment.rollNumbers.size} roll numbers categorized into '$departmentName'.")
                launch(Dispatchers.IO) { saveDepartmentToFirestore(departmentName, newDepartment.rollNumbers) }
            } catch (e: Exception) {
                loadingProgressBar.visibility = View.GONE
                showSnackbar("Categorization failed: ${e.message}")
            }
        }
    }

    private fun moveToExistingDepartment(departmentName: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                loadingProgressBar.visibility = View.VISIBLE
                withContext(Dispatchers.Default) {
                    val targetDepartment = allDepartments.find { it.name == departmentName }
                    if (targetDepartment != null) {
                        departments.forEach { if (it.name != departmentName) it.rollNumbers.removeAll(selectedRollNumbers) }
                        val newRollNumbers = selectedRollNumbers.filter { it !in targetDepartment.rollNumbers }
                        targetDepartment.rollNumbers.addAll(newRollNumbers)
                        departments.removeAll { it.rollNumbers.isEmpty() && it.name != "All Roll Numbers" }
                        if (departments.none { it.name == departmentName }) departments.add(targetDepartment)
                        allDepartments = departments.filter { it.name != "All Roll Numbers" }.toList()
                        fullRollNumberSnapshot.rollNumbers.clear()
                        fullRollNumberSnapshot.rollNumbers.addAll(allRollNumbers)
                        departments.clear()
                        departments.add(fullRollNumberSnapshot)
                        departments.addAll(allDepartments)
                        lastFilteredDepartments = departments.toList()
                        departmentAdapter.submitList(lastFilteredDepartments)
                        selectedRollNumbers.clear()
                        withContext(Dispatchers.Main) {
                            updateSelectedCount()
                            loadingProgressBar.visibility = View.GONE
                            showSnackbar("${newRollNumbers.size} roll numbers moved to '$departmentName'.")
                        }
                        launch(Dispatchers.IO) { saveDepartmentToFirestore(departmentName, targetDepartment.rollNumbers) }
                    } else {
                        withContext(Dispatchers.Main) {
                            loadingProgressBar.visibility = View.GONE
                            showSnackbar("Department '$departmentName' not found.")
                        }
                    }
                }
            } catch (e: Exception) {
                loadingProgressBar.visibility = View.GONE
                showSnackbar("Move failed: ${e.message}")
            }
        }
    }

    private fun saveDepartmentToFirestore(departmentName: String, rollNumbers: List<String>) {
        db.collection("DEPARTMENTS").document(departmentName).set(hashMapOf("rollNumbers" to rollNumbers))
            .addOnSuccessListener {
                Log.d(tag, "Successfully saved '$departmentName' with ${rollNumbers.size} roll numbers.")
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    Log.e(tag, "Error saving department '$departmentName': ${e.message}", e)
                    showSnackbar("Failed to save '$departmentName': ${e.message}")
                }
            }
    }

    internal fun deleteRollNumber(rollNumber: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                loadingProgressBar.visibility = View.VISIBLE
                withContext(Dispatchers.IO) {
                    db.collection("STUDENTS").document(rollNumber).delete().await()
                }
                withContext(Dispatchers.Default) {
                    allRollNumbers.remove(rollNumber)
                    departments.forEach { it.rollNumbers.remove(rollNumber) }
                    departments.removeAll { it.rollNumbers.isEmpty() && it.name != "All Roll Numbers" }
                    fullRollNumberSnapshot.rollNumbers.remove(rollNumber)
                    allDepartments = departments.filter { it.name != "All Roll Numbers" }.toList()
                    lastFilteredDepartments = departments.toList()
                    departmentAdapter.submitList(lastFilteredDepartments)
                }
                updateTotalCount()
                updateSelectedCount()
                loadingProgressBar.visibility = View.GONE
                showSnackbar("Student '$rollNumber' deleted.")
            } catch (e: Exception) {
                Log.e(tag, "Error deleting roll number '$rollNumber': ${e.message}", e)
                loadingProgressBar.visibility = View.GONE
                showSnackbar("Failed to delete '$rollNumber': ${e.message}")
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(recyclerViewDepartments, message, Snackbar.LENGTH_LONG).show()
    }

    private fun handleBackNavigation() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            loadDataJob?.cancel()
            searchJob?.cancel()
            finishWithTransition()
        } else {
            lastBackPressTime = currentTime
            showSnackbar("Press again to go back")
        }
    }

    @Suppress("DEPRECATION")
    private fun finishWithTransition() {
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        loadDataJob?.cancel()
        searchJob?.cancel()
    }
}

data class Department(val name: String, val rollNumbers: MutableList<String>) : Serializable

class DepartmentAdapter : RecyclerView.Adapter<DepartmentAdapter.DepartmentViewHolder>() {
    private val differ = AsyncListDiffer(this, DepartmentDiffCallback())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DepartmentViewHolder =
        DepartmentViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_department, parent, false))

    override fun onBindViewHolder(holder: DepartmentViewHolder, position: Int) = holder.bind(differ.currentList[position])
    override fun onBindViewHolder(holder: DepartmentViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            holder.bind(differ.currentList[position])
        } else {
            holder.updateCheckboxes()
        }
    }
    override fun getItemCount(): Int = differ.currentList.size
    override fun getItemId(position: Int): Long = differ.currentList[position].name.hashCode().toLong()

    init { setHasStableIds(true) }

    fun submitList(newDepartments: List<Department>) {
        differ.submitList(newDepartments.toList())
    }

    class DepartmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewDepartmentName: MaterialTextView = itemView.findViewById(R.id.textViewDepartmentName)
        private val textViewRollNumberCount: MaterialTextView = itemView.findViewById(R.id.textViewRollNumberCount)
        private val recyclerViewRollNumbers: RecyclerView = itemView.findViewById(R.id.recyclerViewRollNumbers)
        private val rollNumberAdapter: RollNumberAdapter

        init {
            recyclerViewRollNumbers.layoutManager = LinearLayoutManager(itemView.context).apply { initialPrefetchItemCount = 10 }
            recyclerViewRollNumbers.setHasFixedSize(true)
            recyclerViewRollNumbers.itemAnimator = null
            val parentActivity = itemView.context as ListStudentsActivity
            rollNumberAdapter = RollNumberAdapter(parentActivity.selectedRollNumbers, parentActivity.maxSelectionLimit) { rollNumber ->
                parentActivity.deleteRollNumber(rollNumber)
            }
            recyclerViewRollNumbers.adapter = rollNumberAdapter
        }

        fun bind(department: Department) {
            textViewDepartmentName.text = department.name
            textViewRollNumberCount.text = itemView.context.getString(R.string.roll_number_count, department.rollNumbers.size)
            rollNumberAdapter.submitList(department.rollNumbers.toList())
        }

        fun updateCheckboxes() {
            rollNumberAdapter.notifyItemRangeChanged(0, rollNumberAdapter.itemCount)
        }
    }
}

class RollNumberAdapter(
    private val selectedRollNumbers: MutableSet<String>,
    private val maxSelectionLimit: Int,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<RollNumberAdapter.RollNumberViewHolder>() {
    private val differ = AsyncListDiffer(this, RollNumberDiffCallback())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RollNumberViewHolder =
        RollNumberViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_student_roll_number, parent, false), selectedRollNumbers, maxSelectionLimit, onDelete)

    override fun onBindViewHolder(holder: RollNumberViewHolder, position: Int) = holder.bind(differ.currentList[position])
    override fun getItemCount(): Int = differ.currentList.size
    override fun getItemId(position: Int): Long = differ.currentList[position].hashCode().toLong()

    init { setHasStableIds(true) }

    fun submitList(newList: List<String>) {
        differ.submitList(newList.toList())
    }

    class RollNumberViewHolder(
        itemView: View,
        private val selectedRollNumbers: MutableSet<String>,
        private val maxSelectionLimit: Int,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val textViewRollNumber: MaterialTextView = itemView.findViewById(R.id.textViewRollNumber)
        private val buttonCopy: MaterialButton = itemView.findViewById(R.id.buttonCopy)
        private val buttonDelete: MaterialButton = itemView.findViewById(R.id.buttonDelete)
        private val checkBoxSelect: MaterialCheckBox = itemView.findViewById(R.id.checkBoxSelect)

        @SuppressLint("InflateParams")
        fun bind(rollNumber: String) {
            textViewRollNumber.text = rollNumber
            checkBoxSelect.isChecked = selectedRollNumbers.contains(rollNumber)
            itemView.isActivated = checkBoxSelect.isChecked

            checkBoxSelect.setOnCheckedChangeListener(null)
            checkBoxSelect.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    if (selectedRollNumbers.size >= maxSelectionLimit) {
                        checkBoxSelect.isChecked = false
                        Snackbar.make(itemView, "Max selection limit ($maxSelectionLimit) reached", Snackbar.LENGTH_LONG).show()
                    } else {
                        selectedRollNumbers.add(rollNumber)
                        itemView.isActivated = true
                        (itemView.context as? ListStudentsActivity)?.updateSelectedCount()
                    }
                } else {
                    selectedRollNumbers.remove(rollNumber)
                    itemView.isActivated = false
                    (itemView.context as? ListStudentsActivity)?.updateSelectedCount()
                }
            }

            itemView.setOnClickListener { checkBoxSelect.toggle() }

            buttonCopy.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.bounce_scale_animation))
                val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Roll Number", rollNumber))
                Snackbar.make(itemView, "Copied '$rollNumber'", Snackbar.LENGTH_SHORT).show()
            }

            buttonDelete.setOnClickListener {
                it.startAnimation(AnimationUtils.loadAnimation(itemView.context, R.anim.bounce_scale_animation))
                val parentActivity = itemView.context as ListStudentsActivity
                val dialogView = LayoutInflater.from(parentActivity).inflate(R.layout.dialog_delete_confirmation, null)
                val dialog = AlertDialog.Builder(parentActivity).setView(dialogView).create()

                dialogView.findViewById<MaterialButton>(R.id.buttonCancel).setOnClickListener { dialog.dismiss() }
                dialogView.findViewById<MaterialButton>(R.id.buttonConfirm).setOnClickListener {
                    onDelete(rollNumber)
                    dialog.dismiss()
                }
                dialog.show()
            }
        }
    }
}

class DepartmentSelectionAdapter(
    private var departmentNames: List<String>
) : RecyclerView.Adapter<DepartmentSelectionAdapter.ViewHolder>() {
    var selectedDepartment: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false))

    @SuppressLint("NotifyDataSetChanged")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val deptName = departmentNames[position]
        holder.textView.text = deptName
        holder.itemView.setOnClickListener {
            selectedDepartment = if (selectedDepartment == deptName) "" else deptName
            notifyDataSetChanged()
        }
        holder.textView.setTextColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (deptName == selectedDepartment) android.R.color.holo_blue_light else android.R.color.white
            )
        )
        holder.textView.setBackgroundColor(
            ContextCompat.getColor(
                holder.itemView.context,
                if (deptName == selectedDepartment) android.R.color.black else android.R.color.transparent
            )
        )
    }

    override fun getItemCount(): Int = departmentNames.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateDepartments(newDepartments: List<String>) {
        departmentNames = newDepartments
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: android.widget.TextView = itemView.findViewById(android.R.id.text1)
    }
}

class DepartmentDiffCallback : DiffUtil.ItemCallback<Department>() {
    override fun areItemsTheSame(oldItem: Department, newItem: Department): Boolean = oldItem.name == newItem.name
    override fun areContentsTheSame(oldItem: Department, newItem: Department): Boolean =
        oldItem.rollNumbers.toTypedArray().contentEquals(newItem.rollNumbers.toTypedArray())
}

class RollNumberDiffCallback : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}