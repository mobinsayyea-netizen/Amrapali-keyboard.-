package com.mobeen.amrapali

/**
 * One key on the keyboard.
 *
 * [normal] is what gets typed on a plain double-tap.
 * [shifted] is what gets typed on a double-tap while the Shift key is "on" for this
 * language. For English this is the upper-case letter. For Hindi/Marathi it is the
 * independent vowel letter or aspirated consonant (e.g. Shift+क -> ख). For Urdu it is
 * the second letter printed on that key. If a key has no shifted form, [shifted]
 * equals [normal].
 * [spoken] is the text spoken aloud on a single tap (accessibility preview), before
 * the character is committed on the second tap. Defaults to [normal].
 * [code] identifies special (non-character) keys such as Backspace, Space, Enter,
 * the language-switch key, the symbols toggle, etc.
 */
data class Key(
    val normal: String,
    val shifted: String = normal,
    val spoken: String? = null,
    val code: Int = KeyCode.CHARACTER,
    val flexWeight: Float = 1f
) {
    fun output(shiftOn: Boolean): String = if (shiftOn) shifted else normal
    fun announcement(shiftOn: Boolean): String = spoken ?: output(shiftOn)
}

object KeyCode {
    const val CHARACTER = 0
    const val SHIFT = 1
    const val BACKSPACE = 2
    const val SPACE = 3
    const val ENTER = 4
    const val LANGUAGE_SWITCH = 5   // tap: cycle language. long-press (double-tap-hold): emoji panel
    const val SYMBOLS_TOGGLE = 6   // switch to numbers/symbols layer
    const val LETTERS_TOGGLE = 7   // switch back to the letters layer
    const val MIC = 8
    const val EMOJI_BACK = 9       // leave the emoji panel, back to language-switch key
    const val EMOJI_CHAR = 10
}

enum class Language(val displayName: String, val ttsLocaleTag: String) {
    ENGLISH("English", "en"),
    HINDI("हिंदी", "hi"),
    MARATHI("मराठी", "mr"),
    URDU("اردو", "ur");

    fun next(): Language = entries[(ordinal + 1) % entries.size]
}
