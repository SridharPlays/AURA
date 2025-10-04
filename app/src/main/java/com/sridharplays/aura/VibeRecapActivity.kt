package com.sridharplays.aura

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.imageview.ShapeableImageView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Updated data class to handle Uris for album art
data class RecapSlide(val title: String, val message: String, val albumArtUri: Uri?)

class VibeRecapActivity : AppCompatActivity() {

    private lateinit var recapText: TextView
    private lateinit var recapImage: ShapeableImageView
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var doneButton: Button
    private lateinit var slideCounterText: TextView
    private lateinit var navigationControls: LinearLayout
    private lateinit var loadingText: TextView

    // The list of slides will now be populated dynamically
    private var recapSlides = mutableListOf<RecapSlide>()
    private var currentSlide = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vibe_recap)

        initializeViews()
        fetchRecapData()

        prevButton.setOnClickListener {
            if (currentSlide > 0) {
                currentSlide--
                showSlide(currentSlide)
            }
        }
        nextButton.setOnClickListener {
            if (currentSlide < recapSlides.size - 1) {
                currentSlide++
                showSlide(currentSlide)
            }
        }
        doneButton.setOnClickListener {
            finish()
        }
    }

    private fun initializeViews() {
        recapText = findViewById(R.id.recapText)
        recapImage = findViewById(R.id.recapImage)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        doneButton = findViewById(R.id.doneButton)
        slideCounterText = findViewById(R.id.slideCounterText)
        navigationControls = findViewById(R.id.navigationControls)
        loadingText = findViewById(R.id.loadingText) // Assuming you add a loading TextView
    }

    private fun fetchRecapData() {
        // Show loading state
        loadingText.visibility = View.VISIBLE
        recapImage.visibility = View.INVISIBLE

        val playCountDao = AppDatabase.getDatabase(this).playCountDao()

        lifecycleScope.launch(Dispatchers.IO) {
            val topSongsFromDb = playCountDao.getTopSongs()

            if (topSongsFromDb.isEmpty()) {
                // Handle case with no listening history
                recapSlides.add(RecapSlide("No Recap Available", "Listen to more music to generate your Vibe Recap!", null))
            } else {
                // Fetch full song details for the top songs
                val songDetailsMap = fetchSongDetails(topSongsFromDb.map { it.songId })

                // Create a slide for each top song
                topSongsFromDb.forEachIndexed { index, songCount ->
                    val song = songDetailsMap[songCount.songId]
                    if (song != null) {
                        val albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), song.albumId)
                        val title = "🏆 Your Top Song #${index + 1}"
                        val message = "'${song.title}' by ${song.artist}\n(Played ${songCount.playCount} times)"
                        recapSlides.add(RecapSlide(title, message, albumArtUri))
                    }
                }
            }

            // Add a final slide
            recapSlides.add(RecapSlide("🚀 Thanks for Vibing!", "Your music taste is 🔥", null))


            // Switch back to the Main thread to update the UI
            withContext(Dispatchers.Main) {
                loadingText.visibility = View.GONE
                recapImage.visibility = View.VISIBLE
                if (recapSlides.isNotEmpty()) {
                    showSlide(0)
                } else {
                    Toast.makeText(this@VibeRecapActivity, "Could not load recap data.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun fetchSongDetails(songIds: List<Long>): Map<Long, Song> {
        val songDetailsMap = mutableMapOf<Long, Song>()
        if (songIds.isEmpty()) return songDetailsMap

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        // Create a selection string like "_id IN (?,?,?,?,?)"
        val selection = MediaStore.Audio.Media._ID + " IN (" + songIds.joinToString(",") + ")"

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )

        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val albumId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                val duration = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                songDetailsMap[id] = Song(id, title, artist, albumId, duration, contentUri)
            }
        }
        return songDetailsMap
    }

    private fun showSlide(index: Int) {
        if (recapSlides.isEmpty() || index !in recapSlides.indices) return

        val slide = recapSlides[index]
        recapText.text = "${slide.title}\n\n${slide.message}"

        // Use Picasso to load the album art from a Uri
        Picasso.get()
            .load(slide.albumArtUri)
            .placeholder(R.drawable.aura_logo) // A default image while loading
            .error(R.drawable.aura_logo)       // A default image if loading fails
            .into(recapImage)

        slideCounterText.text = "${index + 1} / ${recapSlides.size}"
        prevButton.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        nextButton.visibility = if (index == recapSlides.size - 1) View.INVISIBLE else View.VISIBLE

        if (index == recapSlides.size - 1) {
            doneButton.visibility = View.VISIBLE
            navigationControls.visibility = View.GONE
        } else {
            doneButton.visibility = View.GONE
            navigationControls.visibility = View.VISIBLE
        }
    }
}