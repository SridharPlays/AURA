package com.sridharplays.aura

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

class SettingsActivity : AppCompatActivity() {

    private var musicService: MusicPlaybackService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            musicService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar_settings)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Find views
        val usernameEditText: EditText = findViewById(R.id.username_edittext)
        val saveButton: Button = findViewById(R.id.save_settings_button)
        val clearJournalTextView: TextView = findViewById(R.id.clearJournal)
        val bassBoostSeekBar: SeekBar = findViewById(R.id.bass_boost_seekbar) // ADDED
        val bassBoostValueText: TextView = findViewById(R.id.bass_boost_value_text) // ADDED
        val sharedPrefs = getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)

        // Load existing settings
        usernameEditText.setText(sharedPrefs.getString("USERNAME", ""))

        // Load and set initial Bass Boost value
        val savedStrength = sharedPrefs.getInt("BASS_BOOST_STRENGTH", 0)
        bassBoostSeekBar.progress = savedStrength
        bassBoostValueText.text = "${savedStrength / 10}%"

        // Listeners
        saveButton.setOnClickListener {
            val newUsername = usernameEditText.text.toString()
            sharedPrefs.edit().putString("USERNAME", newUsername).apply()

            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        }

        clearJournalTextView.setOnClickListener {
            showClearJournalConfirmationDialog()
        }

        // SeekBar listener to update in real-time and save the value
        bassBoostSeekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Update the text view as the user drags
                bassBoostValueText.text = "${progress / 10}%"
                // Update the service in real-time if it's bound
                if (fromUser) {
                    musicService?.setBassBoostStrength(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Save the final value when the user lets go
                seekBar?.let {
                    sharedPrefs.edit().putInt("BASS_BOOST_STRENGTH", it.progress).apply()
                }
            }
        })
    }

    // Bind to the service when the activity starts
    override fun onStart() {
        super.onStart()
        Intent(this, MusicPlaybackService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    // ADDED: Unbind from the service when the activity stops
    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_invite -> {
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