package com.sridharplays.aura

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.sridharplays.aura.network.MoodPostData
import com.sridharplays.aura.network.RetrofitInstance
import kotlinx.coroutines.launch

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
                addJournalEntry(mood)
            }
        }

        findViewById<CardView>(R.id.cardSurpriseMe).setOnClickListener {
            val randomMood = moodCardMap.values.random()
            addJournalEntry(randomMood)
        }
    }

    private fun addJournalEntry(mood: String) {
        val sharedPrefs = getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("CURRENT_MOOD", mood).apply()

        Toast.makeText(this@MoodSelectorActivity, "Logging mood...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.postMood(MoodPostData(mood = mood))
                if (response.isSuccessful) {
                    Toast.makeText(this@MoodSelectorActivity, "Mood logged: $mood!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MoodSelectorActivity, "Failed to log mood.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e("MoodSelectorActivity", "Network error", e)
                    Toast.makeText(this@MoodSelectorActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                finish()
            }
        }
    }
}