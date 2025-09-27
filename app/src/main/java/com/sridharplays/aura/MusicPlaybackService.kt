package com.sridharplays.aura

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context // AUDIO FOCUS CHANGE: New import
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager // AUDIO FOCUS CHANGE: New import
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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import java.lang.Exception

// AUDIO FOCUS CHANGE: Implement the listener
class MusicPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    // ... (Your existing variables are all fine)
    private var songList: List<MusicPlayerFragment.Song> = listOf()
    private var currentSongIndex: Int = -1
    private var isShuffle = false
    private var repeatMode = RepeatMode.OFF
    enum class RepeatMode { OFF, ALL, ONE }
    private var lastPreviousClickTime: Long = 0
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var reverb: EnvironmentalReverb? = null
    private var virtualizer: Virtualizer? = null
    private var bassBoost: BassBoost? = null
    private var is8DEnabled = false
    private var isBassBoostEnabled = false
    private var currentBassBoostStrength = 0
    private val binder = MusicBinder()
    val isPlaying = MutableLiveData<Boolean>()
    val currentSong = MutableLiveData<MusicPlayerFragment.Song?>()
    val currentPosition = MutableLiveData<Int>()
    val shuffleModeEnabled = MutableLiveData<Boolean>()
    val repeatModeState = MutableLiveData<RepeatMode>()
    val is8DModeEnabled = MutableLiveData<Boolean>()
    val bassBoostModeEnabled = MutableLiveData<Boolean>()
    val bassBoostStrength = MutableLiveData<Int>()
    private val handler = Handler(Looper.getMainLooper())
    fun isShuffleOn(): Boolean = isShuffle

    // AUDIO FOCUS CHANGE: Add AudioManager variable
    private lateinit var audioManager: AudioManager
    private var resumeOnFocusGain = false


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

        // AUDIO FOCUS CHANGE: Initialize the AudioManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

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
        val action = intent?.action

        when (action) {
            ACTION_PLAY_PAUSE -> playOrPause()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
        }

        return START_NOT_STICKY
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

        // Abandon focus when the service is destroyed
        audioManager.abandonAudioFocus(this)
    }

    // Implement the listener method
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (mediaPlayer?.isPlaying == true) {
                    playOrPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (mediaPlayer?.isPlaying == true) {
                    resumeOnFocusGain = true
                    playOrPause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (mediaPlayer?.isPlaying == true) {
                    resumeOnFocusGain = true
                    playOrPause() // Pausing is simpler and often better than ducking for music
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    playOrPause()
                    resumeOnFocusGain = false
                }
            }
        }
    }

    // Helper function to request focus
    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }


    // Public Control Methods
    fun loadPlaylist(songs: List<MusicPlayerFragment.Song>, startIndex: Int) {
        
        if (this.songList == songs) {
            if (currentSongIndex != startIndex) {
                currentSongIndex = startIndex
                playSongAtIndex(currentSongIndex)
            }
            return
        }
        this.songList = songs
        this.currentSongIndex = if (songs.isNotEmpty()) startIndex else -1
        if (currentSongIndex != -1) {
            playSongAtIndex(currentSongIndex)
        }
    }

    fun playOrPause() {
        if (songList.isEmpty() || currentSongIndex == -1) return

        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            isPlaying.postValue(false)
            handler.removeCallbacks(seekBarUpdateRunnable)
        } else {
            if (requestAudioFocus()) {
                mediaPlayer?.start()
                isPlaying.postValue(true)
                handler.post(seekBarUpdateRunnable)
            }
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

    fun seekTo(position: Int) {
        
        mediaPlayer?.seekTo(position)
        updatePlaybackState()
        currentPosition.postValue(position)
    }

    fun getSongList(): List<MusicPlayerFragment.Song> = songList
    fun getCurrentIndex(): Int = currentSongIndex

    fun playSongAtIndex(index: Int) {
        if (songList.isEmpty() || index !in songList.indices) return

        // Request focus before playing a new track
        if (!requestAudioFocus()) {
            return // Don't play if we can't get focus
        }

        val song = songList[index]
        currentSongIndex = index
        currentSong.postValue(song)
        isPlaying.postValue(true)
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(this, song.contentUri)
            mediaPlayer?.prepare()
            mediaPlayer?.start()

            updateMetadata(song)
            updatePlaybackState()

            reverb?.release()
            virtualizer?.release()
            bassBoost?.release()

            val audioSessionId = mediaPlayer!!.audioSessionId
            if (audioSessionId != -1) {
                // ... (The rest of this function is unchanged)
                try {
                    reverb = EnvironmentalReverb(0, audioSessionId).apply {
                        reverbLevel = -2000
                        roomLevel = -1000
                    }
                    reverb?.enabled = is8DEnabled
                } catch (e: Exception) {
                    reverb = null
                    Log.e("AudioEffects", "EnvironmentalReverb failed to initialize.", e)
                }

                try {
                    virtualizer = Virtualizer(0, audioSessionId).apply {
                        if (strengthSupported) setStrength(1000)
                    }
                    virtualizer?.enabled = is8DEnabled
                } catch (e: Exception) {
                    virtualizer = null
                    Log.e("AudioEffects", "Virtualizer failed to initialize.", e)
                }

                try {
                    bassBoost = BassBoost(0, audioSessionId).apply {
                        if (strengthSupported) {
                            setStrength(currentBassBoostStrength.toShort())
                        }
                    }
                    bassBoost?.enabled = isBassBoostEnabled
                } catch (e: Exception) {
                    bassBoost = null
                    Log.e("AudioEffects", "BassBoost failed to initialize.", e)
                }
            }
            handler.post(seekBarUpdateRunnable)
        } catch (e: Exception) {
            Log.e("MusicPlaybackService", "Error playing song at index $index", e)
            isPlaying.postValue(false)
        }
        updateAndShowNotification(song)
    }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val playPauseIcon = if (mediaPlayer?.isPlaying == true) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
        val playPauseIntent = createPendingIntent(ACTION_PLAY_PAUSE)
        val nextIntent = createPendingIntent(ACTION_NEXT)
        val prevIntent = createPendingIntent(ACTION_PREVIOUS)

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "AURA Music")
            .setContentText(song?.artist ?: "Select a song")
            .setSmallIcon(R.drawable.ic_headphones)
            .addAction(R.drawable.ic_skip_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, "Play/Pause", playPauseIntent)
            .addAction(R.drawable.ic_skip_next, "Next", nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .setColor(ContextCompat.getColor(this, R.color.aura_cream))
            .setOngoing(mediaPlayer?.isPlaying ?: false)
            .setContentIntent(contentPendingIntent)

        if (song != null) {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), song.albumId
            )
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
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
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

    private fun createPendingIntent(action: String): PendingIntent {
        
        val intent = Intent(this, MusicPlaybackService::class.java).setAction(action)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getService(this, 0, intent, flags)
    }

    private fun updatePlaybackState() {
        
        if (mediaPlayer == null || mediaSession == null) return
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