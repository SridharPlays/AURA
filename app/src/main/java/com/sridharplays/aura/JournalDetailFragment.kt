package com.sridharplays.aura

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class JournalDetailFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_journal_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Find views
        val dateTextView: TextView = view.findViewById(R.id.detail_date_textview)
        val moodTextView: TextView = view.findViewById(R.id.detail_mood_textview)
        val moodIconImageView: ImageView = view.findViewById(R.id.moodIconImageView)
        val notesEditText: EditText = view.findViewById(R.id.notesEditText)
        val saveNoteButton: Button = view.findViewById(R.id.saveNoteButton)

        // Retrieve the data passed from JournalFragment
        val date = arguments?.getString(ARG_DATE)
        val mood = arguments?.getString(ARG_MOOD)

        // Set the text data
        dateTextView.text = date
        moodTextView.text = mood

        // Set the correct mood icon based on the mood string
        val moodIconResId = when (mood?.lowercase()) {
            "happy" -> R.drawable.ic_mood_happy
            "sad" -> R.drawable.ic_mood_sad
            "chill" -> R.drawable.ic_mood_chill
            "energetic" -> R.drawable.ic_mood_energetic
            "excited" -> R.drawable.ic_mood_excited
            "motivated" -> R.drawable.ic_mood_motivated
            "romantic" -> R.drawable.ic_mood_romantic
            "sleepy" -> R.drawable.ic_mood_sleepy
            else -> R.drawable.ic_mood // A default icon
        }
        moodIconImageView.setImageResource(moodIconResId)

        // Handle Notes
        val sharedPrefs = requireActivity().getSharedPreferences("AuraJournalNotes", Context.MODE_PRIVATE)
        // Create a unique key for this entry's note using its date string
        val noteKey = "note_$date"

        // Load any existing note
        val savedNote = sharedPrefs.getString(noteKey, "")
        notesEditText.setText(savedNote)

        // Set click listener for the save button
        saveNoteButton.setOnClickListener {
            val noteText = notesEditText.text.toString()
            sharedPrefs.edit().putString(noteKey, noteText).apply()
            Toast.makeText(requireContext(), "Note saved!", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val ARG_DATE = "entry_date"
        private const val ARG_MOOD = "entry_mood"

        fun newInstance(date: String, mood: String): JournalDetailFragment {
            val fragment = JournalDetailFragment()
            val args = Bundle()
            args.putString(ARG_DATE, date)
            args.putString(ARG_MOOD, mood)
            fragment.arguments = args
            return fragment
        }
    }
}