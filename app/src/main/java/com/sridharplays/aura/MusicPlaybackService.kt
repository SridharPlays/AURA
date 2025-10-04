package com.sridharplays.aura

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.media.session.MediaButtonReceiver
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

class MusicPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    // Class Properties
    private lateinit var playCountDao: PlayCountDao
    private var songList: List<MusicPlayerFragment.Song> = listOf()
    private var currentSongIndex: Int = -1
    private var isShuffle = false
    private var repeatMode = RepeatMode.OFF
    enum class RepeatMode { OFF, ALL, ONE }

    // State for intelligent shuffle
    private var shuffledIndices: MutableList<Int> = mutableListOf()
    private var playHistory: MutableList<Int> = mutableListOf()

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    // Audio Effects
    private var reverb: EnvironmentalReverb? = null
    private var virtualizer: Virtualizer? = null
    private var bassBoost: BassBoost? = null
    private var is8DEnabled = false
    private var isBassBoostEnabled = false
    private var currentBassBoostStrength = 0

    // Binder for Service Connection
    private val binder = MusicBinder()

    // LiveData for UI Updates
    val isPlaying = MutableLiveData<Boolean>()
    val currentSong = MutableLiveData<MusicPlayerFragment.Song?>()
    val currentPosition = MutableLiveData<Int>()
    val shuffleModeEnabled = MutableLiveData<Boolean>()
    val repeatModeState = MutableLiveData<RepeatMode>()
    val is8DModeEnabled = MutableLiveData<Boolean>()
    val bassBoostModeEnabled = MutableLiveData<Boolean>()
    val bassBoostStrength = MutableLiveData<Int>()
    private val handler = Handler(Looper.getMainLooper())

    // Modern Audio Focus
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private var resumeOnFocusGain = false

    // MediaSession Callback
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { resumePlayback() }
        override fun onPause() { pausePlayback() }
        override fun onSkipToNext() { playNext() }
        override fun onSkipToPrevious() { playPrevious() }
        override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onBind(intent: Intent): IBinder = binder

    /**
     * Returns the current playback queue in the correct order (shuffled or sequential).
     * This should be used by the UI to display the "Up Next" list.
     */
    fun getCurrentQueue(): List<MusicPlayerFragment.Song> {
        if (!isShuffle) {
            return songList
        }

        // If shuffled, construct the list in the correct playback order:
        // [current song, ...upcoming shuffled songs]
        val shuffledQueue = mutableListOf<MusicPlayerFragment.Song>()
        if (currentSongIndex != -1 && currentSongIndex in songList.indices) {
            shuffledQueue.add(songList[currentSongIndex])
        }
        shuffledQueue.addAll(shuffledIndices.map { songList[it] })
        return shuffledQueue
    }

    override fun onCreate() {
        super.onCreate()

        playCountDao = AppDatabase.getDatabase(this).playCountDao()

        val sharedPrefs = getSharedPreferences("AuraAppPrefs", Context.MODE_PRIVATE)
        currentBassBoostStrength = sharedPrefs.getInt("BASS_BOOST_STRENGTH", 0)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mediaPlayer = MediaPlayer()
        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(this)
            .build()

        val mediaButtonReceiver = ComponentName(applicationContext, MediaButtonReceiver::class.java)
        mediaSession = MediaSessionCompat(applicationContext, "AuraMusicPlayer", mediaButtonReceiver, null)
        mediaSession?.setCallback(mediaSessionCallback)
        mediaSession?.isActive = true

        mediaPlayer?.setOnCompletionListener {
            val finishedSong = currentSong.value
            if (finishedSong != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val existingEntry = playCountDao.getPlayCount(finishedSong.id)
                    if (existingEntry == null) {
                        playCountDao.insert(SongPlayCount(songId = finishedSong.id, playCount = 1))
                    } else {
                        playCountDao.incrementPlayCount(finishedSong.id)
                    }
                }
            }

            when (repeatMode) {
                RepeatMode.ONE -> playSongAtIndex(currentSongIndex)
                RepeatMode.ALL -> playNext()
                RepeatMode.OFF -> {
                    // Only stop if it's the last song and not shuffling
                    val isLastSong = if (isShuffle) shuffledIndices.isEmpty() else currentSongIndex == songList.size - 1
                    if (isLastSong) {
                        pausePlayback()
                        seekTo(0)
                        stopForeground(false)
                    } else {
                        playNext()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(seekBarUpdateRunnable)
        reverb?.release()
        virtualizer?.release()
        bassBoost?.release()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mediaPlayer?.isPlaying == true) {
                    resumeOnFocusGain = true
                    pausePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumePlayback()
                    resumeOnFocusGain = false
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    // Playback Controls
    fun loadPlaylist(songs: List<MusicPlayerFragment.Song>, startIndex: Int) {
        this.songList = songs
        this.currentSongIndex = if (songs.isNotEmpty()) startIndex else -1

        if (isShuffle) {
            generateShuffledIndices()
        }

        if (currentSongIndex != -1) {
            playSongAtIndex(currentSongIndex)
        }
    }

    private fun resumePlayback() {
        if (songList.isEmpty() || currentSongIndex == -1) return
        if (mediaPlayer?.isPlaying == false) {
            if (requestAudioFocus()) {
                mediaPlayer?.start()
                isPlaying.postValue(true)
                handler.post(seekBarUpdateRunnable)
                updatePlaybackState()
                updateAndShowNotification(songList[currentSongIndex])
            }
        }
    }

    private fun pausePlayback() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPlaying.postValue(false)
            handler.removeCallbacks(seekBarUpdateRunnable)
            updatePlaybackState()
            stopForeground(false)
            updateAndShowNotification(songList[currentSongIndex])
        }
    }

    fun playOrPause() {
        if (mediaPlayer?.isPlaying == true) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    fun playNext() {
        if (songList.isEmpty()) return

        if (isShuffle) {
            if (shuffledIndices.isEmpty()) {
                // If the upcoming queue is empty, reshuffle everything for continuous play
                generateShuffledIndices()
                if (shuffledIndices.isEmpty()) return // Playlist finished or has only one song
            }

            // Add the song that's about to end to our history
            if (currentSongIndex != -1) {
                playHistory.add(currentSongIndex)
            }

            // Get and remove the next song from the upcoming shuffled list
            val nextIndex = shuffledIndices.removeAt(0)
            currentSongIndex = nextIndex
        } else {
            // Standard sequential playback
            currentSongIndex = (currentSongIndex + 1) % songList.size
        }
        playSongAtIndex(currentSongIndex)
    }

    fun playPrevious() {
        if (songList.isEmpty()) return
        // If song has played for more than 3 seconds, restart it. Otherwise, go to previous.
        if (mediaPlayer?.currentPosition ?: 0 > 3000) {
            seekTo(0)
        } else {
            if (isShuffle) {
                if (playHistory.isEmpty()) return // No history to go back to

                // Put the current song back at the beginning of the upcoming queue
                if (currentSongIndex != -1) {
                    shuffledIndices.add(0, currentSongIndex)
                }

                // Pop the last song from history to play it
                val previousIndex = playHistory.removeLast()
                currentSongIndex = previousIndex
            } else {
                // Standard sequential playback
                currentSongIndex = if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
            }
            playSongAtIndex(currentSongIndex)
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState()
        currentPosition.postValue(position)
    }

    fun playSongAtIndex(index: Int) {
        if (songList.isEmpty() || index !in songList.indices) return
        if (!requestAudioFocus()) return

        if (isShuffle && index != currentSongIndex) {
            // If the user manually picks a song, we adjust the queue.
            val wasInUpcomingQueue = shuffledIndices.remove(index)
            if (wasInUpcomingQueue && currentSongIndex != -1) {
                // The previously playing song is added to history.
                playHistory.add(currentSongIndex)
            } else if (playHistory.contains(index)) {
                // If user jumps back to a song in history, we rebuild the upcoming queue.
                generateShuffledIndices(newCurrentIndex = index)
            }
        }

        currentSongIndex = index
        val song = songList[index]
        currentSong.postValue(song)

        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(this, song.contentUri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()

            isPlaying.postValue(true)
            updateMetadata(song)
            updatePlaybackState()
            setupAudioEffects(mediaPlayer!!.audioSessionId)
            handler.post(seekBarUpdateRunnable)
        } catch (e: Exception) {
            Log.e("MusicPlaybackService", "Error playing song at index $index", e)
            isPlaying.postValue(false)
        }
        updateAndShowNotification(song)
    }

    fun getNextSong(): MusicPlayerFragment.Song? {
        if (songList.size < 2) return null

        val nextIndex: Int? = if (isShuffle) {
            shuffledIndices.firstOrNull() // Get the actual next song from the shuffled queue
        } else {
            (currentSongIndex + 1) % songList.size
        }

        return if (nextIndex != null) songList.getOrNull(nextIndex) else null
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        shuffleModeEnabled.postValue(isShuffle)
        if (isShuffle) {
            generateShuffledIndices()
        } else {
            // Clear shuffle state when turning it off
            playHistory.clear()
            shuffledIndices.clear()
        }
    }

    private fun generateShuffledIndices(newCurrentIndex: Int? = null) {
        if (songList.isNotEmpty()) {
            val indexToPreserve = newCurrentIndex ?: currentSongIndex

            // Get all song indices except the one we're preserving as the current song
            shuffledIndices = (0 until songList.size).toMutableList()
            if (indexToPreserve != -1) {
                shuffledIndices.remove(indexToPreserve)
            }
            shuffledIndices.shuffle()

            if (newCurrentIndex != null) {
                // If user jumped to a new song, it becomes the current one and history is cleared.
                currentSongIndex = newCurrentIndex
                playHistory.clear()
            }
        }
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        repeatModeState.postValue(repeatMode)
    }

    // Audio Effects
    private fun setupAudioEffects(audioSessionId: Int) {
        if (audioSessionId == -1) return

        // Release old instances
        reverb?.release()
        virtualizer?.release()
        bassBoost?.release()

        try {
            reverb = EnvironmentalReverb(0, audioSessionId).apply {
                reverbLevel = -2000
                roomLevel = -1000
                enabled = is8DEnabled
            }
        } catch (e: Exception) {
            reverb = null
            Log.e("AudioEffects", "EnvironmentalReverb failed to initialize.", e)
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                if (strengthSupported) setStrength(1000)
                enabled = is8DEnabled
            }
        } catch (e: Exception) {
            virtualizer = null
            Log.e("AudioEffects", "Virtualizer failed to initialize.", e)
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(currentBassBoostStrength.toShort())
                }
                enabled = isBassBoostEnabled
            }
        } catch (e: Exception) {
            bassBoost = null
            Log.e("AudioEffects", "BassBoost failed to initialize.", e)
        }
    }

    fun toggle8DMode(enable: Boolean) {
        is8DEnabled = enable
        is8DModeEnabled.postValue(enable)
        try {
            reverb?.enabled = enable
            virtualizer?.enabled = enable
        } catch (e: Exception) {
            is8DModeEnabled.postValue(false)
            Log.e("AudioEffects", "Error toggling 8D effects", e)
        }
    }

    fun toggleBassBoost(enable: Boolean) {
        isBassBoostEnabled = enable
        bassBoostModeEnabled.postValue(enable)
        try {
            bassBoost?.enabled = enable
        } catch (e: Exception) {
            bassBoostModeEnabled.postValue(false)
            Log.e("AudioEffects", "Error toggling BassBoost", e)
        }
    }

    fun setBassBoostStrength(strength: Int) {
        if (strength in 0..1000) {
            currentBassBoostStrength = strength
            try {
                bassBoost?.setStrength(strength.toShort())
                bassBoostStrength.postValue(strength)
            } catch (e: Exception) {
                Log.e("AudioEffects", "Error setting BassBoost strength", e)
            }
        }
    }

    // System UI & Notifications
    private val seekBarUpdateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    currentPosition.postValue(it.currentPosition)
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun updateAndShowNotification(song: MusicPlayerFragment.Song?) {
        if (song == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val playPauseIcon = if (mediaPlayer?.isPlaying == true) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
        val playPauseAction = NotificationCompat.Action(playPauseIcon, "Play/Pause", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE))
        val nextAction = NotificationCompat.Action(R.drawable.ic_skip_next, "Next", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
        val prevAction = NotificationCompat.Action(R.drawable.ic_skip_previous, "Previous", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSmallIcon(R.drawable.ic_headphones)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .setColor(ContextCompat.getColor(this, R.color.aura_cream))
            .setOngoing(mediaPlayer?.isPlaying ?: false)
            .setContentIntent(contentPendingIntent)
            .setOnlyAlertOnce(true)

        val albumArtUri = ContentUris.withAppendedId("content://media/external/audio/albumart".toUri(), song.albumId)
        Picasso.get().load(albumArtUri).into(object : Target {
            override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                builder.setLargeIcon(bitmap)
                startForeground(NOTIFICATION_ID, builder.build())
            }
            override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                startForeground(NOTIFICATION_ID, builder.build())
            }
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) {}
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Music playback controls"
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updatePlaybackState() {
        if (mediaPlayer == null || mediaSession == null) return
        val state = if (mediaPlayer!!.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, mediaPlayer!!.currentPosition.toLong(), 1.0f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMetadata(song: MusicPlayerFragment.Song) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration.toLong())
            .build()
        mediaSession?.setMetadata(metadata)
    }

    companion object {
        const val CHANNEL_ID = "MusicPlaybackServiceChannel"
        const val NOTIFICATION_ID = 1
    }
}