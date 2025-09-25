package com.sridharplays.aura

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JournalEntry(val date: String, val mood: String)

class JournalFragment : Fragment() {

    private lateinit var journalAdapter: JournalAdapter
    private var journalEntries = mutableListOf<JournalEntry>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_journal, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.journal_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Load the journal entries from SharedPreferences
        journalEntries = loadJournalEntries().toMutableList()

        // Initialize the adapter with click and long-click listeners
        journalAdapter = JournalAdapter(
            entries = journalEntries,
            onItemClick = { entry ->
                // A regular click opens the detail/update screen
                openDetailFragment(entry)
            },
            onItemLongClick = { entry, itemView ->
                // A long click shows the context menu
                showContextMenu(entry, itemView)
            }
        )
        recyclerView.adapter = journalAdapter

        return view
    }

    private fun showContextMenu(entry: JournalEntry, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.journal_context_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_share -> {
                    shareEntry(entry)
                    true
                }
                R.id.menu_update -> {
                    openDetailFragment(entry)
                    true
                }
                R.id.menu_delete -> {
                    deleteEntry(entry)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun openDetailFragment(entry: JournalEntry) {
        val detailFragment = JournalDetailFragment.newInstance(entry.date, entry.mood)
        parentFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_container, detailFragment)
            addToBackStack(null) // Allows user to return to the journal list
            commit()
        }
    }

    private fun shareEntry(entry: JournalEntry) {
        val shareText = "My mood on ${entry.date}: ${entry.mood}. - Logged with AURA"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share your mood"))
    }

    private fun deleteEntry(entry: JournalEntry) {
        val sharedPrefs = requireActivity().getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
        val entryStrings = sharedPrefs.getStringSet("JOURNAL_ENTRIES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // Find the original string representation of the entry to remove it
        val stringToRemove = "${entry.date} - ${entry.mood}"
        if (entryStrings.contains(stringToRemove)) {
            entryStrings.remove(stringToRemove)
            sharedPrefs.edit().putStringSet("JOURNAL_ENTRIES", entryStrings).apply()

            // Update the UI by removing the item from the list and notifying the adapter
            val position = journalEntries.indexOf(entry)
            if (position != -1) {
                journalEntries.removeAt(position)
                journalAdapter.notifyItemRemoved(position)
            }
        }
    }


    private fun loadJournalEntries(): List<JournalEntry> {
        val sharedPrefs = requireActivity().getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
        val entryStrings = sharedPrefs.getStringSet("JOURNAL_ENTRIES", setOf()) ?: setOf()

        val sdf = SimpleDateFormat("dd MMM yyyy | hh:mm a", Locale.getDefault())

        return entryStrings
            .mapNotNull { entryString ->
                try {
                    val dateString = entryString.substringBefore(" - ")
                    val date = sdf.parse(dateString)
                    // Pair the parsed Date object with the original string for sorting
                    Pair(date, entryString)
                } catch (e: Exception) {
                    null // Ignore any entries that have a malformed date
                }
            }
            .sortedByDescending { it.first } // Sort by the Date object, newest first
            .map { (_, entryString) ->
                // Map the sorted strings back to JournalEntry objects for display
                val parts = entryString.split(" - ")
                JournalEntry(date = parts[0], mood = parts.getOrElse(1) { "" })
            }
    }
}