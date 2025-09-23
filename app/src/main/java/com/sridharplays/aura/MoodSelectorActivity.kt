package com.sridharplays.aura

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodSelectorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_selector)
        setupCardClickListeners()

        val myPlaylistButtom = findViewById<CardView>(R.id.cardMyPlaylists)
        myPlaylistButtom.setOnClickListener {
            Toast.makeText(this, "Coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCardClickListeners() {
        val moodCardMap = mapOf(
            R.id.cardHappy to "Happy",
            R.id.cardSad to "Sad",
            R.id.cardSleepy to "Sleepy",
            R.id.cardMotivated to "Motivated",
            R.id.cardExcited to "Excited",
            R.id.cardRomantic to "Romantic",
            R.id.cardChill to "Chill",
            R.id.cardEnergetic to "Energetic"
        )

        for ((cardId, mood) in moodCardMap) {
            findViewById<CardView>(cardId).setOnClickListener {
                addJournalEntry(mood) // Call our new function
                Toast.makeText(this, "Mood logged: $mood!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        findViewById<CardView>(R.id.cardSurpriseMe).setOnClickListener {
            val randomMood = moodCardMap.values.random()
            addJournalEntry(randomMood) // Call our new function
            Toast.makeText(this, "🎲 Surprise! Mood logged: $randomMood.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun addJournalEntry(mood: String) {
        val sharedPrefs = getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)

        val sdf = SimpleDateFormat("dd MMM yyyy | hh:mm a", Locale.getDefault())
        val formattedDateTime = sdf.format(Date())

        val newEntry = "$formattedDateTime - $mood"

        val existingEntries = sharedPrefs.getStringSet("JOURNAL_ENTRIES", mutableSetOf()) ?: mutableSetOf()

        val updatedEntries = mutableSetOf<String>()
        updatedEntries.addAll(existingEntries)
        updatedEntries.add(newEntry)

        sharedPrefs.edit().putStringSet("JOURNAL_ENTRIES", updatedEntries).apply()

        sharedPrefs.edit().putString("CURRENT_MOOD", mood).apply()
    }
}