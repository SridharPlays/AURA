package com.sridharplays.aura

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllSongsFragment : Fragment() {

    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private lateinit var allSongs: List<Song>
    private lateinit var recyclerView: RecyclerView
    private lateinit var toggleButton: ImageButton
    private var isGridView = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            musicService = null
        }
    }

    // Fragment Lifecycle

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
        // Restore the view state (list/grid) before the view is created
        isGridView = savedInstanceState?.getBoolean(KEY_IS_GRID_VIEW, false) ?: false
        return inflater.inflate(R.layout.fragment_all_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.songs_recyclerview)
        toggleButton = view.findViewById(R.id.button_toggle_view)

        // Set the initial icon based on the restored state
        updateToggleButtonIcon()

        toggleButton.setOnClickListener {
            isGridView = !isGridView
            updateToggleButtonIcon()
            setupRecyclerView()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            allSongs = withContext(Dispatchers.IO) {
                getAllAudioFromDevice(requireContext())
            }
            // Once songs are loaded, set up the RecyclerView on the main thread
            setupRecyclerView()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save the current view state
        outState.putBoolean(KEY_IS_GRID_VIEW, isGridView)
    }

    // UI Setup

    private fun updateToggleButtonIcon() {
        toggleButton.setImageResource(if (isGridView) R.drawable.ic_list_view else R.drawable.ic_grid_view)
    }

    private fun setupRecyclerView() {
        if (!::allSongs.isInitialized) return // Don't setup if songs aren't loaded yet

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
                    // [FIXED] Removed unused parameter from newInstance()
                    .replace(R.id.fragment_container, MusicPlayerFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }


    private fun getAllAudioFromDevice(context: Context): List<Song> {
        val songList = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )

        // This selection logic filters for the "AURA_Musics" folder
        val selection: String
        val selectionArgs: Array<String>

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 and above, we use RELATIVE_PATH
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            // The '%' is a wildcard to include subfolders within AURA_Musics
            selectionArgs = arrayOf("%AURA_Music/%")
        } else {
            // For older versions, we use the deprecated DATA column
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/AURA_Music/%")
        }

        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )

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

    companion object {
        private const val KEY_IS_GRID_VIEW = "is_grid_view_state"
    }
}