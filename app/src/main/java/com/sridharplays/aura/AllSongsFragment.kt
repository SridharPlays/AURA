package com.sridharplays.aura

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.*
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sridharplays.aura.MusicPlayerFragment.Song
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllSongsFragment : Fragment() {

    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private lateinit var allSongs: List<MusicPlayerFragment.Song>
    private lateinit var recyclerView: RecyclerView
    private lateinit var toggleButton: ImageButton // Added button reference
    private var isGridView = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(requireContext(), MusicPlaybackService::class.java).also { intent ->
            requireActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireActivity().unbindService(connection)
            isBound = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // setHasOptionsMenu(true) has been removed as it's no longer needed
        return inflater.inflate(R.layout.fragment_all_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.songs_recyclerview)
        toggleButton = view.findViewById(R.id.button_toggle_view) // Find the button

        // Set up the click listener for the new button
        toggleButton.setOnClickListener {
            isGridView = !isGridView // Flip the state
            setupRecyclerView() // Re-setup the RecyclerView
            // Update the button's icon
            toggleButton.setImageResource(if (isGridView) R.drawable.ic_list_view else R.drawable.ic_grid_view)
        }

        // Load songs in the background (unchanged)
        viewLifecycleOwner.lifecycleScope.launch {
            allSongs = withContext(Dispatchers.IO) {
                getAllAudioFromDevice(requireContext())
            }
            // Once songs are loaded, set up the RecyclerView on the main thread
            setupRecyclerView()
        }
    }

    // The OptionsMenu methods (onCreateOptionsMenu, onOptionsItemSelected) have been removed.

    private fun setupRecyclerView() {
        recyclerView.layoutManager = if (isGridView) {
            GridLayoutManager(context, 2)
        } else {
            LinearLayoutManager(context)
        }

        recyclerView.adapter = AllSongsAdapter(allSongs, isGridView) { selectedSong ->
            val globalIndex = allSongs.indexOf(selectedSong)
            if (globalIndex != -1 && isBound) {
                musicService?.loadPlaylist(allSongs, globalIndex)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MusicPlayerFragment.newInstance("allsongs"))
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun getAllAudioFromDevice(context: Context): List<Song> {
        val songList = mutableListOf<Song>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION)
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf("AURA_Music/%")
        } else {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/AURA_Music/%")
        }
        val cursor = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, MediaStore.Audio.Media.TITLE + " ASC")
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                val title = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val artist = c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                val albumId = c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                val duration = c.getInt(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                songList.add(Song(id, title, artist, albumId, duration, contentUri))
            }
        }
        return songList
    }
}