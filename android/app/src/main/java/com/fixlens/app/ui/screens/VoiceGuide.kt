package com.fixlens.app.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * On-device text-to-speech for guided steps. Uses the platform TTS engine,
 * no network call, no API key, nothing recorded. English voice; queue mode
 * FLUSH so step changes replace the previous utterance instead of stacking.
 *
 * Lifecycle: one instance per activity screen; [shutdown] on dispose.
 * Mute state is remembered across steps but never persisted (a phone restart
 * gives a fresh voice-on default, deliberate: silence should be a choice).
 */
class VoiceGuide(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    /** Observable mute state for the UI toggle. */
    var muted: Boolean = false
        private set

    var onMutedChanged: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts?.language = Locale.US
            tts?.setSpeechRate(0.95f)
            pending?.let {
                speak(it)
                pending = null
            }
        }
    }

    /** Speak now (flushes any current utterance). No-op when muted/not ready. */
    fun speak(text: String) {
        if (muted) return
        if (!ready) {
            pending = text
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fixlens_step")
    }

    fun setMuted(value: Boolean) {
        if (muted == value) return
        muted = value
        if (value) stop()
        onMutedChanged?.invoke(value)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
