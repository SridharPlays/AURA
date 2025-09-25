package com.sridharplays.aura

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target

class MusicPlaybackService : Service() {

    // State Management
    private var songList: List<MusicPlayerFragment.Song> = listOf()
    private var currentSongIndex: Int = -1
    private var isShuffle = false
    private var repeatMode = RepeatMode.OFF
    enum class RepeatMode { OFF, ALL, ONE }
    private var lastPreviousClickTime: Long = 0

    // Media Player & Session
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null

    // Audio Effects
    private var reverb: EnvironmentalReverb? = null
    private var virtualizer: Virtualizer? = null
    private var bassBoost: BassBoost? = null // Bass Boost
    private var is8DEnabled = false
    private var isBassBoostEnabled = false   // Bass Boost state
    private var currentBassBoostStrength = 0 // Bass Boost strength (0-1000)

    // Communication with UI
    private val binder = MusicBinder()
    val isPlaying = MutableLiveData<Boolean>()
    val currentSong = MutableLiveData<MusicPlayerFragment.Song?>()
    val currentPosition = MutableLiveData<Int>()
    val shuffleModeEnabled = MutableLiveData<Boolean>()
    val repeatModeState = MutableLiveData<RepeatMode>()
    val is8DModeEnabled = MutableLiveData<Boolean>()
    val bassBoostModeEnabled = MutableLiveData<Boolean>() // LiveData for Bass Boost state
    val bassBoostStrength = MutableLiveData<Int>()      // LiveData for Bass Boost strength
    private val handler = Handler(Looper.getMainLooper())

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { playOrPause() }
        override fun onPause() { playOrPause() }
        override fun onSkipToNext() { playNext() }
        override fun onSkipToPrevious() { playPrevious() }
        override fun onSeekTo(pos: Long) {
            seekTo(pos.toInt())
            updatePlaybackState()
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "AuraMusicPlayer")
        mediaSession?.setCallback(mediaSessionCallback)
        mediaSession?.isActive = true

        mediaPlayer?.setOnCompletionListener {
            when (repeatMode) {
                RepeatMode.ONE -> playSongAtIndex(currentSongIndex)
                RepeatMode.ALL -> playNext()
                RepeatMode.OFF -> {
                    if (currentSongIndex < songList.size - 1 || isShuffle) {
                        playNext()
                    } else {
                        mediaPlayer?.pause()
                        mediaPlayer?.seekTo(0)
                        isPlaying.postValue(false)
                        currentPosition.postValue(0)
                        updatePlaybackState()
                        if (currentSongIndex != -1) {
                            updateAndShowNotification(songList[currentSongIndex])
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        updateAndShowNotification(null)
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playOrPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(seekBarUpdateRunnable)
        reverb?.release()
        virtualizer?.release()
        bassBoost?.release() // Release BassBoost
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        super.onDestroy()
    }

    // Public Control Methods
    fun loadPlaylist(songs: List<MusicPlayerFragment.Song>, startIndex: Int) {
        if (this.songList == songs) {
            if (currentSongIndex != startIndex) {
                currentSongIndex = startIndex
                updateAndShowNotification(songList[currentSongIndex])
                playSongAtIndex(currentSongIndex)
            }
            return // Don't reload the whole list
        }

        // If it's a completely new playlist (or the first time), load it from scratch
        this.songList = songs
        this.currentSongIndex = if (songs.isNotEmpty()) startIndex else -1
        if (currentSongIndex != -1) {
            playSongAtIndex(currentSongIndex)
        }
    }

    fun playOrPause() {
        if (songList.isEmpty()) return
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPlaying.postValue(false)
            handler.removeCallbacks(seekBarUpdateRunnable)
        } else {
            mediaPlayer?.start()
            isPlaying.postValue(true)
            handler.post(seekBarUpdateRunnable)
        }
        updatePlaybackState()
        updateAndShowNotification(songList[currentSongIndex])
    }

    fun playNext() {
        if (songList.isEmpty()) return
        currentSongIndex = if (isShuffle) {
            (0 until songList.size).filter { it != currentSongIndex }.random()
        } else {
            (currentSongIndex + 1) % songList.size
        }
        playSongAtIndex(currentSongIndex)
    }

    fun playPrevious() {
        if (songList.isEmpty()) return

        if (mediaPlayer?.currentPosition ?: 0 > 3000) {
            seekTo(0)
        } else {
            currentSongIndex = if (isShuffle) {
                (0 until songList.size).filter { it != currentSongIndex }.random()
            } else {
                if (currentSongIndex - 1 < 0) songList.size - 1 else currentSongIndex - 1
            }
            playSongAtIndex(currentSongIndex)
        }
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        shuffleModeEnabled.postValue(isShuffle)
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        repeatModeState.postValue(repeatMode)
    }

    fun toggle8DMode(enable: Boolean) {
        is8DEnabled = enable
        is8DModeEnabled.postValue(enable)
        try {
            reverb?.enabled = enable
            virtualizer?.enabled = enable
        } catch (e: Exception) {
            is8DModeEnabled.postValue(false)
        }
    }

    // BASS BOOST METHODS
    fun toggleBassBoost(enable: Boolean) {
        isBassBoostEnabled = enable
        bassBoostModeEnabled.postValue(enable)
        try {
            bassBoost?.enabled = enable
        } catch (e: Exception) {
            bassBoostModeEnabled.postValue(false)
        }
    }

    fun setBassBoostStrength(strength: Int) {
        // Strength is from 0 to 1000
        if (strength in 0..1000) {
            currentBassBoostStrength = strength
            try {
                bassBoost?.setStrength(strength.toShort())
                bassBoostStrength.postValue(strength)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
        updatePlaybackState()
        currentPosition.postValue(position) // Immediately update UI
    }

    // Expose state for the Fragment's UI
    fun getSongList(): List<MusicPlayerFragment.Song> = songList
    fun getCurrentIndex(): Int = currentSongIndex
    fun isShuffleOn(): Boolean = isShuffle

    fun playSongAtIndex(index: Int) {
        if (songList.isEmpty() || index !in songList.indices) return
        val song = songList[index]
        currentSong.postValue(song)
        isPlaying.postValue(true)
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(this, song.contentUri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()

            updateMetadata(song)
            updatePlaybackState()

            // Release previous effects
            reverb?.release()
            virtualizer?.release()
            bassBoost?.release() // Release old BassBoost instance

            val audioSessionId = mediaPlayer!!.audioSessionId
            if (audioSessionId != -1) {
                // Attach 8D Audio Effects
                reverb = EnvironmentalReverb(0, audioSessionId).apply {
                    reverbLevel = -2000
                    roomLevel = -1000
                }
                try {
                    virtualizer = Virtualizer(0, audioSessionId).apply {
                        if (strengthSupported) setStrength(1000)
                    }
                } catch (e: Exception) { virtualizer = null }

                reverb?.enabled = is8DEnabled
                virtualizer?.enabled = is8DEnabled

                // Attach Bass Boost Effect
                try {
                    bassBoost = BassBoost(0, audioSessionId).apply {
                        if (strengthSupported) {
                            setStrength(currentBassBoostStrength.toShort())
                        }
                    }
                    bassBoost?.enabled = isBassBoostEnabled
                } catch (e: Exception) {
                    bassBoost = null // Device may not support it
                    e.printStackTrace()
                }
            }
            handler.post(seekBarUpdateRunnable)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateAndShowNotification(song)
    }

    private val seekBarUpdateRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    currentPosition.postValue(it.currentPosition)
                    updatePlaybackState()
                    handler.postDelayed(this, 1000)
                }
            }
        }
    }

    private fun updateAndShowNotification(song: MusicPlayerFragment.Song?) {
        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return // Cannot post a notification without permission
        }

        // Create intents for notification actions
        val playPauseIcon = if (mediaPlayer?.isPlaying == true) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
        val playPauseIntent = createPendingIntent(ACTION_PLAY_PAUSE)
        val nextIntent = createPendingIntent(ACTION_NEXT)
        val prevIntent = createPendingIntent(ACTION_PREVIOUS)

        // Create an intent to open the app when the notification is tapped
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification with text and actions first
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "AURA Music")
            .setContentText(song?.artist ?: "Select a song")
            .setSmallIcon(R.drawable.ic_headphones)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)) // Show all 3 actions
            .setColor(ContextCompat.getColor(this, R.color.aura_cream))
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)

        // Now, handle the album art asynchronously
        if (song != null) {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), song.albumId
            )
            Picasso.get().load(albumArtUri).into(object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                    builder.setLargeIcon(bitmap)
                    // Post the notification with the loaded image
                    startForeground(NOTIFICATION_ID, builder.build())
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    // If image fails, post the notification without the large icon
                    startForeground(NOTIFICATION_ID, builder.build())
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                    // Optional: You could show a placeholder notification here if loading is slow
                }
            })
        } else {
            // If there's no song, just show the basic notification
            startForeground(NOTIFICATION_ID, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(action)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getService(this, 0, intent, flags)
    }


    private fun updatePlaybackState() {
        if (mediaPlayer == null) return
        val state = if (mediaPlayer!!.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO)
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
        const val ACTION_PLAY_PAUSE = "com.sridharplays.aura.PLAY_PAUSE"
        const val ACTION_NEXT = "com.sridharplays.aura.NEXT"
        const val ACTION_PREVIOUS = "com.sridharplays.aura.PREVIOUS"
    }
}