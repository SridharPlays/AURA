package com.sridharplays.aura

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class JournalAdapter(
    private val entries: List<JournalEntry>,
    private val onItemClick: (JournalEntry) -> Unit,
    // Add a new listener for long clicks
    private val onItemLongClick: (JournalEntry, View) -> Unit
) : RecyclerView.Adapter<JournalAdapter.JournalViewHolder>() {

    class JournalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateTextView: TextView = itemView.findViewById(R.id.item_date_textview)
        val moodTextView: TextView = itemView.findViewById(R.id.item_mood_textview)
        val moodIconImageView: ImageView = itemView.findViewById(R.id.moodIconImageView) // New view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): JournalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_journal_entry, parent, false)
        return JournalViewHolder(view)
    }

    override fun onBindViewHolder(holder: JournalViewHolder, position: Int) {
        val entry = entries[position]
        holder.dateTextView.text = entry.date
        holder.moodTextView.text = entry.mood

        // Set the correct mood icon
        val moodIconResId = when (entry.mood.lowercase()) {
            "happy" -> R.drawable.ic_mood_happy
            "sad" -> R.drawable.ic_mood_sad
            "chill" -> R.drawable.ic_mood_chill
            "energetic" -> R.drawable.ic_mood_energetic
            "excited" -> R.drawable.ic_mood_excited
            "motivated" -> R.drawable.ic_mood_motivated
            "romantic" -> R.drawable.ic_mood_romantic
            "sleepy" -> R.drawable.ic_mood_sleepy
            else -> R.drawable.ic_mood
        }
        holder.moodIconImageView.setImageResource(moodIconResId)

        // Set click listeners
        holder.itemView.setOnClickListener { onItemClick(entry) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(entry, holder.itemView)
            true // Important: return true to indicate the event was handled
        }
    }

    override fun getItemCount() = entries.size
}