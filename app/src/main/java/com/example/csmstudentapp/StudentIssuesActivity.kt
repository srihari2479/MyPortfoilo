package com.example.csmstudentapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.firestore.FirebaseFirestore

class StudentIssuesActivity : AppCompatActivity() {
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerViewIssues: RecyclerView
    private lateinit var adapter: IssuesAdapter
    @Suppress("PrivatePropertyName")
    private val TAG = "StudentIssuesActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_issues)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        db = FirebaseFirestore.getInstance()
        recyclerViewIssues = findViewById(R.id.recyclerViewIssues)
        recyclerViewIssues.layoutManager = LinearLayoutManager(this)

        fetchIssues()
    }

    private fun fetchIssues() {
        db.collection("STUDENT_ISSUES").get()
            .addOnSuccessListener { documents ->
                val issues = documents.map { doc ->
                    Issue(
                        docId = doc.id, // Store the Firestore document ID
                        rollNumber = doc.getString("ROLL_NUMBER") ?: "",
                        description = doc.getString("DESCRIPTION") ?: "",
                        timestamp = doc.getLong("TIMESTAMP") ?: 0L,
                        status = doc.getString("STATUS") ?: "PENDING"
                    )
                }.sortedByDescending { it.timestamp }
                adapter = IssuesAdapter(issues.toMutableList())
                recyclerViewIssues.adapter = adapter
                Log.d(TAG, "Fetched ${issues.size} issues")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching issues: ${e.message}", e)
                Snackbar.make(recyclerViewIssues, "Error fetching issues: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    data class Issue(
        val docId: String, // Added to store the document ID
        var rollNumber: String,
        var description: String,
        var timestamp: Long,
        var status: String
    )

    inner class IssuesAdapter(private val issues: MutableList<Issue>) :
        RecyclerView.Adapter<IssuesAdapter.IssueViewHolder>() {

        @Suppress("MemberVisibilityCanBePrivate")
        inner class IssueViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            val textViewRollNumber: MaterialTextView = itemView.findViewById(R.id.textViewRollNumber)
            val textViewDescription: MaterialTextView = itemView.findViewById(R.id.textViewDescription)
            val textViewStatus: MaterialTextView = itemView.findViewById(R.id.textViewStatus)
            val buttonCompleted: MaterialButton = itemView.findViewById(R.id.buttonCompleted)

            @SuppressLint("SetTextI18n")
            fun bind(issue: Issue, position: Int) {
                textViewRollNumber.text = "Roll Number: ${issue.rollNumber}"
                textViewDescription.text = "Issue: ${issue.description}"
                textViewStatus.text = "Status: ${issue.status}"

                buttonCompleted.setOnClickListener {
                    // Use the stored docId instead of reconstructing it
                    db.collection("STUDENT_ISSUES").document(issue.docId)
                        .delete()
                        .addOnSuccessListener {
                            issues.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, issues.size)
                            Snackbar.make(itemView, "Issue marked as completed and deleted", Snackbar.LENGTH_SHORT).show()
                            Log.d(TAG, "Deleted issue with docId: ${issue.docId}")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error deleting issue with docId: ${issue.docId}, message: ${e.message}", e)
                            Snackbar.make(itemView, "Error deleting issue: ${e.message}", Snackbar.LENGTH_LONG).show()
                        }
                }
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): IssueViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_student_issue, parent, false)
            return IssueViewHolder(view)
        }

        override fun onBindViewHolder(holder: IssueViewHolder, position: Int) {
            holder.bind(issues[position], position)
        }

        override fun getItemCount(): Int = issues.size
    }
}