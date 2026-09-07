package com.mobeen.amrapali

import android.inputmethodservice.InputMethodService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.View
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import java.util.Locale

class AmrapaliIME : InputMethodService(), CustomKeyboardView.Listener {

    private lateinit var keyboardView: CustomKeyboardView
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    private var currentLanguage = Language.ENGLISH
    private var shiftOn = false

    private enum class Layer { LETTERS, SYMBOLS, EMOJI }
    private var currentLayer = Layer.LETTERS

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale(currentLanguage.ttsLocaleTag)
            }
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = CustomKeyboardView(this)
        keyboardView.listener = this
        refreshKeyboard()
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentLayer = Layer.LETTERS
        refreshKeyboard()
    }

    // ---------------------------------------------------------------
    // Building the on-screen rows for the current language/layer/shift
    // ---------------------------------------------------------------
    private fun refreshKeyboard() {
        keyboardView.shiftOn = shiftOn
        when (currentLayer) {
            Layer.LETTERS -> {
                keyboardView.setRows(
                    listOf(
                        KeyboardLayouts.row1(currentLanguage),
                        KeyboardLayouts.row2(currentLanguage),
                        KeyboardLayouts.row3(currentLanguage).let {
                            listOf(Key("⇧", spoken = "Shift", code = KeyCode.SHIFT)) + it +
                                listOf(Key("⌫", spoken = "Backspace", code = KeyCode.BACKSPACE))
                        }
                    )
                )
                keyboardView.setBottomRow(
                    listOf(
                        Key("?123", spoken = "Symbols", code = KeyCode.SYMBOLS_TOGGLE),
                        Key("🌐", spoken = "Language switch", code = KeyCode.LANGUAGE_SWITCH),
                        Key("🎤", spoken = "Voice input", code = KeyCode.MIC),
                        KeyboardLayouts.commaKey(currentLanguage),
                        Key(" ", spoken = "Space", code = KeyCode.SPACE, flexWeight = 3f),
                        KeyboardLayouts.periodKey(currentLanguage),
                        Key("⏎", spoken = "Enter", code = KeyCode.ENTER)
                    )
                )
            }
            Layer.SYMBOLS -> {
                keyboardView.setRows(
                    listOf(
                        KeyboardLayouts.symbolsRow1,
                        KeyboardLayouts.symbolsRow2,
                        KeyboardLayouts.symbolsRow3 + listOf(Key("⌫", spoken = "Backspace", code = KeyCode.BACKSPACE))
                    )
                )
                keyboardView.setBottomRow(
                    listOf(
                        Key("ABC", spoken = "Letters", code = KeyCode.LETTERS_TOGGLE),
                        Key(",", spoken = "Comma", code = KeyCode.CHARACTER),
                        Key(" ", spoken = "Space", code = KeyCode.SPACE, flexWeight = 3f),
                        Key(".", spoken = "Period", code = KeyCode.CHARACTER),
                        Key("⏎", spoken = "Enter", code = KeyCode.ENTER)
                    )
                )
            }
            Layer.EMOJI -> {
                val chunks = KeyboardLayouts.emojis.chunked(10)
                keyboardView.setRows(chunks)
                keyboardView.setBottomRow(
                    listOf(Key("⌨", spoken = "Back to keyboard", code = KeyCode.EMOJI_BACK, flexWeight = 1f))
                )
            }
        }
    }

    // ---------------------------------------------------------------
    // CustomKeyboardView.Listener
    // ---------------------------------------------------------------
    override fun onAnnounce(key: Key) {
        // Key announcements are always the English key name (see KeyboardLayouts doc
        // comment) or a special-key label like "Space"/"Backspace" — so they are always
        // spoken with the English TTS voice, regardless of which language is active.
        // Only what gets TYPED on the second tap depends on currentLanguage.
        val text = key.announcement(shiftOn)
        tts?.language = Locale.ENGLISH
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "announce")
    }

    override fun onEmojiPanelRequested() {
        currentLayer = Layer.EMOJI
        refreshKeyboard()
    }

    override fun onKey(key: Key) {
        val ic = currentInputConnection ?: return
        when (key.code) {
            KeyCode.CHARACTER -> {
                ic.commitText(key.output(shiftOn), 1)
                if (shiftOn && currentLanguage == Language.ENGLISH) {
                    shiftOn = false
                    refreshKeyboard()
                }
            }
            KeyCode.EMOJI_CHAR -> ic.commitText(key.normal, 1)
            KeyCode.SPACE -> ic.commitText(" ", 1)
            KeyCode.ENTER -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                .also { ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)) }
            KeyCode.BACKSPACE -> ic.deleteSurroundingText(1, 0)
            KeyCode.SHIFT -> {
                shiftOn = !shiftOn
                refreshKeyboard()
            }
            KeyCode.LANGUAGE_SWITCH -> {
                currentLanguage = currentLanguage.next()
                shiftOn = false
                tts?.language = Locale(currentLanguage.ttsLocaleTag)
                tts?.speak(currentLanguage.displayName, TextToSpeech.QUEUE_FLUSH, null, "lang")
                refreshKeyboard()
            }
            KeyCode.SYMBOLS_TOGGLE -> {
                currentLayer = Layer.SYMBOLS
                refreshKeyboard()
            }
            KeyCode.LETTERS_TOGGLE -> {
                currentLayer = Layer.LETTERS
                refreshKeyboard()
            }
            KeyCode.EMOJI_BACK -> {
                currentLayer = Layer.LETTERS
                refreshKeyboard()
            }
            KeyCode.MIC -> startVoiceInput()
        }
    }

    // ---------------------------------------------------------------
    // Voice input
    // ---------------------------------------------------------------
    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Voice input उपलब्ध नहीं है", Toast.LENGTH_SHORT).show()
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { currentInputConnection?.commitText(it, 1) }
                }
                override fun onError(error: Int) {
                    Toast.makeText(this@AmrapaliIME, "Voice input error", Toast.LENGTH_SHORT).show()
                }
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.ttsLocaleTag)
            }
            startListening(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
