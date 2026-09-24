package com.example.dinorush

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager

class SoundManager(private val context: Context) {
    private var soundPool: SoundPool? = null
    private var musicPlayer: MediaPlayer? = null
    private val loadedSounds = HashSet<Int>()
    private val pendingPlays = HashMap<Int, Int>()
    private val prefs = GamePrefs(context)
    private var idJump = 0
    private var idLand = 0
    private var idCoin = 0
    private var idPowerUp = 0
    private var idCrash = 0
    private var idClick = 0
    private var idHighScore = 0
    private var idGameOver = 0

    fun init() {
        releaseSoundPoolOnly()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val pool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
        soundPool = pool
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            val count = synchronized(loadedSounds) {
                loadedSounds.add(sampleId)
                pendingPlays.remove(sampleId) ?: 0
            }
            if (prefs.soundEnabled && count > 0) {
                repeat(count) { pool.play(sampleId, 1f, 1f, 1, 0, 1f) }
            }
        }
        idJump = pool.load(context, R.raw.sfx_jump, 1)
        idLand = pool.load(context, R.raw.sfx_land, 1)
        idCoin = pool.load(context, R.raw.sfx_coin, 1)
        idPowerUp = pool.load(context, R.raw.sfx_powerup, 1)
        idCrash = pool.load(context, R.raw.sfx_crash, 1)
        idClick = pool.load(context, R.raw.sfx_click, 1)
        idHighScore = pool.load(context, R.raw.sfx_highscore, 1)
        idGameOver = pool.load(context, R.raw.sfx_gameover, 1)
    }

    private fun play(id: Int) {
        if (!prefs.soundEnabled || id == 0) return
        val pool = soundPool ?: return
        synchronized(loadedSounds) {
            if (loadedSounds.contains(id)) {
                pool.play(id, 1f, 1f, 1, 0, 1f)
            } else {
                pendingPlays[id] = (pendingPlays[id] ?: 0) + 1
            }
        }
    }

    fun playJump() = play(idJump)
    fun playLand() = play(idLand)
    fun playCoin() = play(idCoin)
    fun playPowerUp() = play(idPowerUp)
    fun playCrash() = play(idCrash)
    fun playClick() = play(idClick)
    fun playHighScore() = play(idHighScore)
    fun playGameOver() = play(idGameOver)

    fun startMusic() {
        if (!prefs.musicEnabled) return
        if (musicPlayer == null) {
            musicPlayer = MediaPlayer.create(context, R.raw.bg_music)?.apply {
                isLooping = true
                setVolume(0.35f, 0.35f)
            }
        }
        try {
            musicPlayer?.let { if (!it.isPlaying) it.start() }
        } catch (_: IllegalStateException) {
            musicPlayer?.release()
            musicPlayer = null
        }
    }

    fun stopMusic() {
        try { musicPlayer?.pause() } catch (_: IllegalStateException) {}
    }

    fun refreshMusicState() {
        if (prefs.musicEnabled) startMusic() else stopMusic()
    }

    fun vibrate(durationMs: Long) {
        if (!prefs.vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        durationMs,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    private fun releaseSoundPoolOnly() {
        synchronized(loadedSounds) {
            loadedSounds.clear()
            pendingPlays.clear()
        }
        soundPool?.release()
        soundPool = null
    }

    fun release() {
        releaseSoundPoolOnly()
        musicPlayer?.release()
        musicPlayer = null
    }
}
