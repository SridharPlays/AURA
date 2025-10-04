package com.sridharplays.aura

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.sridharplays.aura.network.JournalEntry
import com.sridharplays.aura.network.RetrofitInstance
import kotlinx.coroutines.launch


class JournalFragment : Fragment() {

    private lateinit var journalAdapter: JournalAdapter
    private var journalEntries = mutableListOf<JournalEntry>()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_journal, container, false)

        // Initialize views
        recyclerView = view.findViewById(R.id.journal_recyclerview)
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout)

        setupRecyclerView()
        setupSwipeToRefresh()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Perform the initial fetch of journal entries from the server
        fetchJournalEntries()
    }

    private fun setupRecyclerView() {
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
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = journalAdapter
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            Log.d("JournalFragment", "Pull to refresh initiated.")
            fetchJournalEntries()
        }
    }

    private fun fetchJournalEntries() {
        swipeRefreshLayout.isRefreshing = true // Show the loading indicator
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.getJournalEntries()
                if (isAdded && response.isSuccessful && response.body() != null) {
                    Log.d("JournalFragment", "Successfully fetched ${response.body()!!.data.size} entries.")
                    journalEntries.clear()
                    journalEntries.addAll(response.body()!!.data)
                    journalAdapter.notifyDataSetChanged()
                } else if (isAdded) {
                    Log.e("JournalFragment", "API Error: ${response.code()} - ${response.message()}")
                    Toast.makeText(context, "Failed to load journal entries.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.i("JournalFragment", "Fetch entries job was cancelled. This is normal.")
                } else if (isAdded) {
                    Log.e("JournalFragment", "Network Exception", e)
                    Toast.makeText(context, "Network error. Please check your connection.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                if (isAdded) {
                    swipeRefreshLayout.isRefreshing = false // Hide the loading indicator
                }
            }
        }
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

    /**
     * Navigates to the detail screen, passing all necessary data.
     */
    private fun openDetailFragment(entry: JournalEntry) {
        val detailFragment = JournalDetailFragment.newInstance(entry.id, entry.date, entry.mood, entry.note)
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

    /**
     * Deletes a journal entry via an API call and updates the UI.
     */
    private fun deleteEntry(entry: JournalEntry) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitInstance.api.deleteEntry(id = entry.id)
                if (response.isSuccessful) {
                    // If the API call is successful, remove the item from our local list
                    val position = journalEntries.indexOf(entry)
                    if (position != -1) {
                        journalEntries.removeAt(position)
                        journalAdapter.notifyItemRemoved(position)
                    }
                    Toast.makeText(context, "Entry deleted.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("JournalFragment", "API Error on delete: ${response.code()} - ${response.message()}")
                    Toast.makeText(context, "Failed to delete entry.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("JournalFragment", "Network Exception on delete", e)
                Toast.makeText(context, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}