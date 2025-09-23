package com.sridharplays.aura

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val usernameEditText: EditText = findViewById(R.id.username_edittext)
        val saveButton: Button = findViewById(R.id.save_settings_button)
        val clearJournalTextView: TextView = findViewById(R.id.clearJournal)
        val sharedPrefs = getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)

        // Load existing username
        usernameEditText.setText(sharedPrefs.getString("USERNAME", ""))

        // Save button listener
        saveButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()
            sharedPrefs.edit().putString("USERNAME", newUsername).apply()

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }

        // Clear journal listener
        clearJournalTextView.setOnClickListener {
            showClearJournalConfirmationDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_invite -> {
                // Create an implicit intent to share the app
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "Check out AURA, my new favorite music app!")
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(shareIntent, "Share AURA with a friend"))
                true
            }
            R.id.menu_help -> {
                Toast.makeText(this, "Opening Help & Support...", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_exit -> {
                // Finish all activities and exit the app
                finishAffinity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearJournalConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Journal")
            .setMessage("Are you sure you want to delete all your mood history? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                val sharedPrefs = getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().remove("JOURNAL_ENTRIES").apply()
                Toast.makeText(this, "Mood journal cleared.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}