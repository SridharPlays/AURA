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
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayerFragment : Fragment(), QueueBottomSheetFragment.QueueInteractionListener {

    // NOTE: The Song data class should be in its own file (Song.kt), not here.

    private var musicService: MusicPlaybackService? = null
    private var isBound = false
    private var userIsSeeking = false

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var effectsNavigationView: NavigationView
    private lateinit var openMenuButton: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrevious: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var btnShowQueue: ImageButton
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
    private var bassBoostSeekbar: SeekBar? = null
    private var bassBoostSeekbarMenuItem: MenuItem? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MusicPlaybackService.MusicBinder
            musicService = binder.getService()
            isBound = true
            observeServiceData()

            val currentQueue = musicService?.getCurrentQueue()
            if (currentQueue.isNullOrEmpty()) {
                lifecycleScope.launch {
                    val songList = withContext(Dispatchers.IO) {
                        getAllAudioFromDevice(requireContext())
                    }
                    if (songList.isNotEmpty()) {
                        musicService?.loadPlaylist(songList, 0)
                    } else {
                        Toast.makeText(requireContext(), "No music found", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            musicService = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_music_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        setupClickListeners()
        startAndBindService()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireActivity().unbindService(connection)
            isBound = false
        }
    }

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
            updateNextInQueueUI()
        }
        musicService?.repeatModeState?.observe(viewLifecycleOwner) { mode ->
            when (mode) {
                MusicPlaybackService.RepeatMode.OFF -> {
                    btnRepeat.setImageResource(R.drawable.ic_repeat)
                    btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
                }
                MusicPlaybackService.RepeatMode.ALL -> {
                    btnRepeat.setImageResource(R.drawable.ic_repeat)
                    btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aura_accent_muted))
                }
                MusicPlaybackService.RepeatMode.ONE -> {
                    btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                    btnRepeat.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aura_accent_muted))
                }
                null -> {}
            }
        }
        musicService?.is8DModeEnabled?.observe(viewLifecycleOwner) { isEnabled ->
            effectsNavigationView.menu.findItem(R.id.menu_8d_audio)?.isChecked = isEnabled
        }
        musicService?.bassBoostModeEnabled?.observe(viewLifecycleOwner) { isEnabled ->
            effectsNavigationView.menu.findItem(R.id.menu_bass_boost)?.isChecked = isEnabled
            bassBoostSeekbarMenuItem?.isVisible = isEnabled
        }
        musicService?.bassBoostStrength?.observe(viewLifecycleOwner) { strength ->
            bassBoostSeekbar?.progress = strength
        }
    }

    private fun updateMainUI(song: Song) {
        songTitle.text = song.title
        artistName.text = song.artist
        totalTime.text = formatTime(song.duration)
        seekBar.max = song.duration
        val albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), song.albumId)
        Picasso.get().load(albumArtUri).placeholder(R.drawable.aura_logo).error(R.drawable.aura_logo).into(albumArt)
        updateNextInQueueUI()
    }

    private fun updateNextInQueueUI() {
        val nextSong = musicService?.getNextSong()
        if (nextSong == null) {
            nextSongLayout.visibility = View.GONE
            nextSongLayout.setOnClickListener(null)
            return
        }
        nextSongLayout.visibility = View.VISIBLE
        nextSongTitle.text = nextSong.title
        nextSongArtist.text = nextSong.artist
        val nextArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), nextSong.albumId)
        Picasso.get().load(nextArtUri).placeholder(R.drawable.aura_logo).error(R.drawable.aura_logo).into(nextSongAlbumArt)
        nextSongLayout.setOnClickListener {
            musicService?.playNext()
        }
    }

    private fun initializeViews(view: View) {
        drawerLayout = view.findViewById(R.id.drawer_layout_player)
        effectsNavigationView = view.findViewById(R.id.effects_nav_view)
        openMenuButton = view.findViewById(R.id.button_effects_menu)
        songTitle = view.findViewById(R.id.songTitle)
        artistName = view.findViewById(R.id.artistName)
        albumArt = view.findViewById(R.id.albumArt)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnNext = view.findViewById(R.id.btnNext)
        btnPrevious = view.findViewById(R.id.btnPrevious)
        btnShuffle = view.findViewById(R.id.btnShuffle)
        btnRepeat = view.findViewById(R.id.btnRepeat)
        seekBar = view.findViewById(R.id.songSeekbar)
        currentTime = view.findViewById(R.id.startTime)
        totalTime = view.findViewById(R.id.endTime)
        nextSongTitle = view.findViewById(R.id.nextSongTitle)
        nextSongArtist = view.findViewById(R.id.nextSongArtist)
        nextSongAlbumArt = view.findViewById(R.id.nextSongAlbumArt)
        nextSongLayout = view.findViewById(R.id.nextSongLayout)
        btnShowQueue = view.findViewById(R.id.btnShowQueue)
        bassBoostSeekbarMenuItem = effectsNavigationView.menu.findItem(R.id.menu_bass_boost_seekbar_item)
        bassBoostSeekbar = bassBoostSeekbarMenuItem?.actionView?.findViewById(R.id.menu_bass_boost_seekbar)
        bassBoostSeekbarMenuItem?.isVisible = musicService?.bassBoostModeEnabled?.value ?: false
        songTitle.isSelected = true
        nextSongTitle.isSelected = true
    }

    private fun setupClickListeners() {
        openMenuButton.setOnClickListener { drawerLayout.openDrawer(GravityCompat.END) }
        effectsNavigationView.setNavigationItemSelectedListener { menuItem ->
            menuItem.isCheckable = true
            when (menuItem.itemId) {
                R.id.menu_8d_audio -> musicService?.toggle8DMode(!menuItem.isChecked)
                R.id.menu_bass_boost -> musicService?.toggleBassBoost(!menuItem.isChecked)
            }
            true
        }
        btnPlayPause.setOnClickListener { musicService?.playOrPause() }
        btnNext.setOnClickListener { musicService?.playNext() }
        btnPrevious.setOnClickListener { musicService?.playPrevious() }
        btnShuffle.setOnClickListener { musicService?.toggleShuffle() }
        btnRepeat.setOnClickListener { musicService?.cycleRepeatMode() }

        btnShowQueue.setOnClickListener {
            val currentQueue = musicService?.getCurrentQueue()
            val currentSong = musicService?.currentSong?.value
            if (!currentQueue.isNullOrEmpty() && currentSong != null) {
                val queueFragment = QueueBottomSheetFragment.newInstance(
                    ArrayList(currentQueue),
                    currentSong.id
                )
                queueFragment.show(childFragmentManager, "QueueBottomSheet")
            } else {
                Toast.makeText(requireContext(), "Queue is empty", Toast.LENGTH_SHORT).show()
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
        bassBoostSeekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val strength = it.progress
                    musicService?.setBassBoostStrength(strength)
                    val sharedPrefs = requireActivity().getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putInt("BASS_BOOST_STRENGTH", strength).apply()
                }
            }
        })
    }

    // THIS IS THE MAIN FIX
    override fun onQueueSongClicked(position: Int) {
        // 1. Get the queue that was displayed to the user
        val queue = musicService?.getCurrentQueue()
        // 2. Get the specific Song object that the user clicked
        val selectedSong = queue?.getOrNull(position) ?: return

        // 3. Get the original, unshuffled master list of all songs
        val masterList = musicService?.getMasterSongList()
        // 4. Find the index of the selected song within that master list
        val masterListIndex = masterList?.indexOf(selectedSong)

        // 5. Tell the service to play the song at its true index
        if (masterListIndex != null && masterListIndex != -1) {
            musicService?.playSongAtIndex(masterListIndex)
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
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
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf("%AURA_Music/%")
        } else {
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
        fun newInstance(): MusicPlayerFragment {
            return MusicPlayerFragment()
        }
    }
}