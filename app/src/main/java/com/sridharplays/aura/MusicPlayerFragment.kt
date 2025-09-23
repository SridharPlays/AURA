package com.sridharplays.aura

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.squareup.picasso.Picasso

class MusicPlayerFragment : Fragment() {

    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private var userIsSeeking = false
    private var nextSongIndex: Int = -1

    // --- View Components ---
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    // REMOVED: btn8D is no longer a separate button
    private lateinit var seekBar: SeekBar
    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var albumArt: ImageView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var nextSongTitle: TextView
    private lateinit var nextSongArtist: TextView
    private lateinit var nextSongAlbumArt: ImageView
    private lateinit var nextSongLayout: LinearLayout

    data class Song(
        val id: Long, val title: String, val artist: String,
        val albumId: Long, val duration: Int, val contentUri: Uri
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicBinder
            musicService = binder.getService()
            isBound = true

            if (musicService?.isPlaying?.value == false && musicService?.currentSong?.value == null) {
                val songList = getAllAudioFromDevice(requireContext())
                if (songList.isNotEmpty()) {
                    musicService?.loadPlaylist(songList, 0)
                } else {
                    Toast.makeText(requireContext(), "No music found in AURA_Music folder", Toast.LENGTH_LONG).show()
                }
            }
            observeServiceData()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // This line is required to show the options menu in a fragment
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_music_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)

        // Setup the Toolbar
        val toolbar: Toolbar = view.findViewById(R.id.player_toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)
        (activity as AppCompatActivity).supportActionBar?.title = "" // Optional: clear title

        setupClickListeners()
        startAndBindService()
    }

    // --- ADDED: Methods to create and handle the options menu ---
    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.player_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
        // Ensure the menu item's checked state is in sync when it's created
        musicService?.is8DModeEnabled?.value?.let { isEnabled ->
            val menuItem = menu.findItem(R.id.menu_8d_audio)
            menuItem?.isChecked = isEnabled
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_8d_audio -> {
                // Toggle the 8D mode and update the menu item's checkmark
                val isCurrentlyEnabled = item.isChecked
                musicService?.toggle8DMode(!isCurrentlyEnabled)
                item.isChecked = !isCurrentlyEnabled
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    // --------------------------------------------------------

    private fun startAndBindService() {
        Intent(requireContext(), MusicPlaybackService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireActivity().startForegroundService(intent)
            } else {
                requireActivity().startService(intent)
            }
            requireActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeServiceData() {
        musicService?.isPlaying?.observe(viewLifecycleOwner) { playing ->
            btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause_circle else R.drawable.ic_play_circle)
        }
        musicService?.currentSong?.observe(viewLifecycleOwner) { song ->
            song?.let { updateMainUI(it) }
        }
        musicService?.currentPosition?.observe(viewLifecycleOwner) { position ->
            if (!userIsSeeking) {
                seekBar.progress = position
                currentTime.text = formatTime(position)
            }
        }
        musicService?.shuffleModeEnabled?.observe(viewLifecycleOwner) { isEnabled ->
            val tintColor = if (isEnabled) R.color.aura_accent_muted else R.color.white
            btnShuffle.setColorFilter(ContextCompat.getColor(requireContext(), tintColor))
            Toast.makeText(requireContext(), if (isEnabled) "Shuffle ON" else "Shuffle OFF", Toast.LENGTH_SHORT).show()
            updateNextInQueueUI()
        }
        musicService?.repeatModeState?.observe(viewLifecycleOwner) { mode ->
            when (mode) {
                MusicPlaybackService.RepeatMode.OFF -> {
                    btnRepeat.setImageResource(R.drawable.ic_repeat)
                    btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
                    Toast.makeText(requireContext(), "Repeat OFF", Toast.LENGTH_SHORT).show()
                }
                MusicPlaybackService.RepeatMode.ALL -> {
                    btnRepeat.setImageResource(R.drawable.ic_repeat)
                    btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aura_accent_muted))
                    Toast.makeText(requireContext(), "Repeat ALL", Toast.LENGTH_SHORT).show()
                }
                MusicPlaybackService.RepeatMode.ONE -> {
                    btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                    btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aura_accent_muted))
                    Toast.makeText(requireContext(), "Repeat ONE", Toast.LENGTH_SHORT).show()
                }
                null -> {}
            }
        }
        musicService?.is8DModeEnabled?.observe(viewLifecycleOwner) { isEnabled ->
            // Invalidate the options menu to redraw it with the correct checkmark state
            activity?.invalidateOptionsMenu()
            Toast.makeText(requireContext(), if (isEnabled) "8D Audio ON" else "8D Audio OFF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateMainUI(song: Song) {
        songTitle.text = truncateText(song.title)
        artistName.text = truncateText(song.artist)
        totalTime.text = formatTime(song.duration)
        seekBar.max = song.duration
        val albumArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
        Picasso.get().load(albumArtUri).placeholder(R.drawable.aura_logo).error(R.drawable.aura_logo).into(albumArt)
        updateNextInQueueUI()
    }

    private fun updateNextInQueueUI() {
        if (!isBound || musicService == null) return
        val songList = musicService!!.getSongList()
        val currentIndex = musicService!!.getCurrentIndex()
        val isShuffleOn = musicService!!.isShuffleOn()

        if (songList.size < 2) {
            nextSongLayout.visibility = View.GONE
            nextSongIndex = -1
            return
        }

        nextSongLayout.visibility = View.VISIBLE

        val nextIndexLocal = if (isShuffleOn) {
            (0 until songList.size).filter { it != currentIndex }.random()
        } else {
            (currentIndex + 1) % songList.size
        }

        this.nextSongIndex = nextIndexLocal

        val nextSong = songList[nextIndexLocal]
        nextSongTitle.text = truncateText(nextSong.title)
        nextSongArtist.text = truncateText(nextSong.artist)
        val nextArtUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), nextSong.albumId)
        Picasso.get().load(nextArtUri).placeholder(R.drawable.aura_logo).error(R.drawable.aura_logo).into(nextSongAlbumArt)
    }

    private fun initializeViews(view: View) {
        songTitle = view.findViewById(R.id.songTitle)
        artistName = view.findViewById(R.id.artistName)
        albumArt = view.findViewById(R.id.albumArt)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnNext = view.findViewById(R.id.btnNext)
        btnPrevious = view.findViewById(R.id.btnPrevious)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)
        // REMOVED: btn8D is no longer a separate button
        seekBar = view.findViewById(R.id.songSeekbar)
        currentTime = view.findViewById(R.id.startTime)
        totalTime = view.findViewById(R.id.endTime)
        nextSongTitle = view.findViewById(R.id.nextSongTitle)
        nextSongArtist = view.findViewById(R.id.nextSongArtist)
        nextSongAlbumArt = view.findViewById(R.id.nextSongAlbumArt)
        nextSongLayout = view.findViewById(R.id.nextSongLayout)
    }

    private fun setupClickListeners() {
        btnPlayPause.setOnClickListener { musicService?.playOrPause() }
        btnNext.setOnClickListener { musicService?.playNext() }
        btnPrevious.setOnClickListener { musicService?.playPrevious() }
        btnShuffle.setOnClickListener { musicService?.toggleShuffle() }
        btnRepeat.setOnClickListener { musicService?.cycleRepeatMode() }

        nextSongLayout.setOnClickListener {
            if (nextSongIndex != -1) {
                musicService?.playSongAtIndex(nextSongIndex)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) currentTime.text = formatTime(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userIsSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                sb?.let { musicService?.seekTo(it.progress) }
                userIsSeeking = false
            }
        })
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            requireActivity().unbindService(connection)
            isBound = false
        }
    }

    private fun truncateText(text: String, maxLength: Int = 25): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
    }

    private fun formatTime(ms: Int): String {
        val minutes = ms / 1000 / 60
        val seconds = (ms / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
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

    companion object {
        fun newInstance(mood: String): MusicPlayerFragment {
            return MusicPlayerFragment()
        }
    }
}