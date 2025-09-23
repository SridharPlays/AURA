package com.sridharplays.aura

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.imageview.ShapeableImageView

class VibeRecapActivity : AppCompatActivity() {

    private lateinit var recapText: TextView
    private lateinit var recapImage: ShapeableImageView
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var doneButton: Button
    private lateinit var slideCounterText: TextView
    private lateinit var navigationControls: LinearLayout // The new control group

    private val recapSlides = listOf(
        RecapSlide("🎵 Your Top Song", "Timeless - Weeknd", R.drawable.album1),
        RecapSlide("🕒 Total Listening Time", "5 hrs 12 mins this week!", R.drawable.album2),
        RecapSlide("👩‍🎤 Favorite Artist", "Ramin Djawadi", R.drawable.album3),
        RecapSlide("🎧 Most Played Genre", "Fantasy", R.drawable.album2),
        RecapSlide("🚀 Thanks for Vibing!", "Your music taste is 🔥", R.drawable.album1)
    )

    private var currentSlide = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vibe_recap)

        // Initialize Views
        recapText = findViewById(R.id.recapText)
        recapImage = findViewById(R.id.recapImage)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        doneButton = findViewById(R.id.doneButton)
        slideCounterText = findViewById(R.id.slideCounterText)
        navigationControls = findViewById(R.id.navigationControls) // Initialize the control group

        // Set initial slide
        showSlide(currentSlide)

        // Click Listeners
        nextButton.setOnClickListener {
            if (currentSlide < recapSlides.size - 1) {
                currentSlide++
                showSlide(currentSlide)
            }
        }

        prevButton.setOnClickListener {
            if (currentSlide > 0) {
                currentSlide--
                showSlide(currentSlide)
            }
        }

        doneButton.setOnClickListener {
            finish() // Close the recap activity
        }
    }

    private fun showSlide(index: Int) {
        val slide = recapSlides[index]
        recapText.text = "${slide.title}\n\n${slide.message}"
        recapImage.setImageResource(slide.imageRes)

        // Update the slide counter text
        slideCounterText.text = "${index + 1} / ${recapSlides.size}"

        // Manage individual button visibility within the control group
        prevButton.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        nextButton.visibility = if (index == recapSlides.size - 1) View.INVISIBLE else View.VISIBLE

        // Show the done button and hide the entire navigation group on the last slide
        if (index == recapSlides.size - 1) {
            doneButton.visibility = View.VISIBLE
            navigationControls.visibility = View.GONE
        } else {
            doneButton.visibility = View.GONE
            navigationControls.visibility = View.VISIBLE
        }
    }
}

data class RecapSlide(val title: String, val message: String, val imageRes: Int)