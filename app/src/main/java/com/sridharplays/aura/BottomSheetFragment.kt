package com.sridharplays.aura

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class QueueBottomSheetFragment : BottomSheetDialogFragment() {

    // Interface to communicate back to the MusicPlayerFragment
    interface QueueInteractionListener {
        fun onQueueSongClicked(position: Int)
    }

    private var listener: QueueInteractionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Attach the listener from the parent fragment
        listener = parentFragment as? QueueInteractionListener
            ?: throw ClassCastException("$parentFragment must implement QueueInteractionListener")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val songs = arguments?.getParcelableArrayList<MusicPlayerFragment.Song>(ARG_SONGS)
        val currentSongId = arguments?.getLong(ARG_CURRENT_SONG_ID, -1L) ?: -1L

        if (songs.isNullOrEmpty()) {
            dismiss()
            return
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.queue_recycler_view)
        val queueAdapter = QueueAdapter(songs, currentSongId) { position ->
            listener?.onQueueSongClicked(position)
            dismiss() // Close the bottom sheet after a song is clicked
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = queueAdapter
            // Scroll to the currently playing song
            val currentPosition = songs.indexOfFirst { it.id == currentSongId }
            if (currentPosition != -1) {
                (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(currentPosition, 200)
            }
        }
    }

    companion object {
        private const val ARG_SONGS = "songs_list"
        private const val ARG_CURRENT_SONG_ID = "current_song_id"

        fun newInstance(
            songs: ArrayList<MusicPlayerFragment.Song>,
            currentSongId: Long
        ): QueueBottomSheetFragment {
            val fragment = QueueBottomSheetFragment()
            val args = Bundle().apply {
                putParcelableArrayList(ARG_SONGS, songs)
                putLong(ARG_CURRENT_SONG_ID, currentSongId)
            }
            fragment.arguments = args
            return fragment
        }
    }
}