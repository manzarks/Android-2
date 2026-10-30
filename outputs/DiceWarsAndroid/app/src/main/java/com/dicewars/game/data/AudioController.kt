package com.dicewars.game.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin

class AudioController(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var toneGenerator: ToneGenerator? = null
    private var track: AudioTrack? = null
    private var currentSettings = LocalSettings()
    private var foreground = true
    private var hasFocus = false

    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusRequest: AudioFocusRequest? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(musicAttributes)
            .setOnAudioFocusChangeListener { change ->
                if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) track?.pause()
                else if (currentSettings.music && foreground) track?.play()
            }
            .build()
    } else null

    @Synchronized
    fun apply(settings: LocalSettings) {
        currentSettings = settings
        toneGenerator?.release()
        toneGenerator = if (settings.effects) ToneGenerator(AudioManager.STREAM_MUSIC, settings.effectVolume.coerceIn(0, 100)) else null
        if (settings.music && settings.musicVolume > 0) {
            ensureMusicTrack()
            track?.setVolume(settings.musicVolume.coerceIn(0, 100) / 100f)
            if (foreground) requestMusicFocusAndPlay()
        } else {
            track?.pause()
            abandonMusicFocus()
        }
    }

    @Synchronized
    fun playEffect(battleWon: Boolean? = null) {
        val tone = toneGenerator ?: return
        val kind = when (battleWon) {
            true -> ToneGenerator.TONE_PROP_ACK
            false -> ToneGenerator.TONE_PROP_NACK
            null -> ToneGenerator.TONE_PROP_BEEP
        }
        tone.startTone(kind, 115)
    }

    @Synchronized
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            track?.pause()
            abandonMusicFocus()
        } else if (currentSettings.music && currentSettings.musicVolume > 0) {
            requestMusicFocusAndPlay()
        }
    }

    @Synchronized
    fun release() {
        abandonMusicFocus()
        toneGenerator?.release()
        toneGenerator = null
        track?.stop()
        track?.release()
        track = null
    }

    private fun ensureMusicTrack() {
        if (track != null) return
        val sampleRate = 22050
        val seconds = 8
        val samples = ShortArray(sampleRate * seconds)
        val chordRoots = doubleArrayOf(130.81, 164.81, 174.61, 146.83)
        for (i in samples.indices) {
            val time = i.toDouble() / sampleRate
            val chord = chordRoots[(time / 2.0).toInt().coerceIn(0, chordRoots.lastIndex)]
            val local = time % 2.0
            val fade = (minOf(local / 0.28, (2.0 - local) / 0.32, 1.0)).coerceAtLeast(0.0)
            val pad = sin(2.0 * PI * chord * time) * 0.55 +
                sin(2.0 * PI * chord * 1.5 * time) * 0.22 +
                sin(2.0 * PI * chord * 2.0 * time) * 0.12
            val bell = sin(2.0 * PI * (chord * 4.0) * time) * (0.035 * (0.5 + 0.5 * sin(2.0 * PI * time / 4.0)))
            samples[i] = ((pad * 0.075 * fade + bell) * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val made = AudioTrack.Builder()
            .setAudioAttributes(musicAttributes)
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        made.write(samples, 0, samples.size)
        made.setLoopPoints(0, samples.size, -1)
        track = made
    }

    private fun requestMusicFocusAndPlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val result = focusRequest?.let(audioManager::requestAudioFocus) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            hasFocus = audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (hasFocus) track?.play()
    }

    private fun abandonMusicFocus() {
        if (!hasFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let(audioManager::abandonAudioFocusRequest)
        else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        hasFocus = false
    }

    companion object {
        @Volatile private var active: AudioController? = null
        fun register(controller: AudioController) { active = controller }
        fun pauseForBackground() { active?.setForeground(false) }
        fun resumeForForeground() { active?.setForeground(true) }
        fun clear(controller: AudioController) { if (active === controller) active = null }
    }
}

