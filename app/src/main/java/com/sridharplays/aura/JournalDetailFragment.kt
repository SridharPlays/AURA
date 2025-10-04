package com.sridharplays.aura

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sridharplays.aura.network.NoteUpdateData
import com.sridharplays.aura.network.RetrofitInstance
import kotlinx.coroutines.launch

class JournalDetailFragment : Fragment() {

    private lateinit var notesEditText: EditText
    private lateinit var saveNoteButton: Button

    private var initialNoteText: String = ""
    private var entryId: String = ""

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
        notesEditText = view.findViewById(R.id.notesEditText)
        saveNoteButton = view.findViewById(R.id.saveNoteButton)

        // Retrieve data
        entryId = requireArguments().getString(ARG_ID)!!
        val date = requireArguments().getString(ARG_DATE)
        val mood = requireArguments().getString(ARG_MOOD)
        val note = requireArguments().getString(ARG_NOTE)

        // Set UI elements
        dateTextView.text = date
        moodTextView.text = mood
        notesEditText.setText(note ?: "")
        initialNoteText = note ?: ""

        // Set mood icon
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

        // Set listener for the explicit save button
        saveNoteButton.setOnClickListener {
            saveNote()
        }

        // Intercept the back button press to check for unsaved changes
        setupOnBackPressed()
    }

    private fun saveNote(andNavigateBack: Boolean = false) {
        val noteText = notesEditText.text.toString()
        saveNoteButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.updateNote(
                    NoteUpdateData(id = entryId, note = noteText)
                )

                if (response.isSuccessful) {
                    initialNoteText = noteText
                    Toast.makeText(requireContext(), "Note saved!", Toast.LENGTH_SHORT).show()
                    if (andNavigateBack) {
                        parentFragmentManager.popBackStack()
                    }
                } else {
                    Log.e("JournalDetail", "API Error: ${response.code()} - ${response.message()}")
                    Toast.makeText(requireContext(), "Failed to save note.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("JournalDetail", "Network Exception", e)
                Toast.makeText(requireContext(), "Network error. Please try again.", Toast.LENGTH_SHORT).show()
            } finally {
                saveNoteButton.isEnabled = true
            }
        }
    }

    private fun setupOnBackPressed() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val hasChanges = notesEditText.text.toString() != initialNoteText
                if (hasChanges) {
                    showUnsavedChangesDialog()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Save Changes?")
            .setMessage("You have unsaved changes. Would you like to save them before leaving?")
            .setPositiveButton("Save") { _, _ ->
                saveNote(andNavigateBack = true)
            }
            .setNegativeButton("Discard") { _, _ ->
                parentFragmentManager.popBackStack()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    companion object {
        private const val ARG_ID = "entry_id"
        private const val ARG_DATE = "entry_date"
        private const val ARG_MOOD = "entry_mood"
        private const val ARG_NOTE = "entry_note"

        fun newInstance(id: String, date: String, mood: String, note: String?): JournalDetailFragment {
            val fragment = JournalDetailFragment()
            val args = Bundle().apply {
                putString(ARG_ID, id)
                putString(ARG_DATE, date)
                putString(ARG_MOOD, mood)
                putString(ARG_NOTE, note)
            }
            fragment.arguments = args
            return fragment
        }
    }
}