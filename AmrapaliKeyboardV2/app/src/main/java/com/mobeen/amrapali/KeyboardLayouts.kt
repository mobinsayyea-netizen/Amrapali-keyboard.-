package com.mobeen.amrapali

/**
 * Letter-layer layouts for the 4 languages.
 *
 * IMPORTANT NOTE ON ACCURACY (read this before extending):
 * - English is standard QWERTY — not in question.
 * - Hindi and Marathi use InScript (the Government of India standard Devanagari
 *   keyboard, IS 13194). Both languages officially share this exact layout; Marathi
 *   does not have its own separate government-standard layout. The character-per-key
 *   mapping below was taken from Microsoft's own KBDINDEV.DLL keyboard driver tables
 *   (the real InScript implementation shipped in Windows), so the letters are accurate.
 * - Urdu uses the official Windows/Pakistan Urdu keyboard (KBDURDU.DLL), which is also
 *   the default layout for Punjabi and Sindhi in Pakistan. This is a fixed native
 *   layout (like QWERTY is for English) — it is NOT a phonetic/Latin-sounds keyboard.
 * - Some rarer characters that exist on the full desktop layouts (Devanagari vocalic
 *   ऋ/ॠ, the ॐ OM symbol, conjunct ligature keys like ज्ञ/त्र/क्ष/श्र, and a few Urdu
 *   diacritic-only keys reached via AltGr on a desktop) are intentionally left out of
 *   this first version to keep the on-screen grid simple. They can be added later
 *   (e.g. as a long-press alternate on an existing key) once the basic layout is
 *   tested and confirmed working.
 *
 * IMPORTANT — what gets ANNOUNCED vs what gets TYPED:
 * In every language, a key is always announced (single tap / TTS) by its English
 * QWERTY key name — "Q", "A", "M", etc. — never by the Hindi/Marathi/Urdu character
 * sitting on it. Only the double-tap COMMIT differs by language: typing the same
 * physical "Q" key commits "ौ" in Hindi, "ط" in Urdu, or "q"/"Q" in English. This is
 * by explicit request — the user already knows the physical grid by its English
 * key names and wants that same spoken confirmation regardless of which language's
 * characters are actually being typed.
 */
object KeyboardLayouts {

    // Row layout shape is the same for every language: 10 keys / 9 keys / 7 keys,
    // like a standard mobile QWERTY keyboard.
    private val letterNamesRow1 = "QWERTYUIOP".map { it.toString() }
    private val letterNamesRow2 = "ASDFGHJKL".map { it.toString() }
    private val letterNamesRow3 = "ZXCVBNM".map { it.toString() }

    private val englishRow1 = letterNamesRow1.map { Key(it.lowercase(), it) }
    private val englishRow2 = letterNamesRow2.map { Key(it.lowercase(), it) }
    private val englishRow3 = letterNamesRow3.map { Key(it.lowercase(), it) }

    // Hindi / Marathi — InScript. Pair = (normal, shift). Spoken label = English key name.
    private val hindiRow1 = listOf(
        "ौ" to "औ", "ै" to "ऐ", "ा" to "आ", "ी" to "ई", "ू" to "ऊ",
        "ब" to "भ", "ह" to "ङ", "ग" to "घ", "द" to "ध", "ज" to "झ"
    ).zip(letterNamesRow1).map { (pair, keyName) -> Key(pair.first, pair.second, spoken = keyName) }

    private val hindiRow2 = listOf(
        "ो" to "ओ", "े" to "ए", "्" to "अ", "ि" to "इ", "ु" to "उ",
        "प" to "फ", "र" to "ऱ", "क" to "ख", "त" to "थ"
    ).zip(letterNamesRow2).map { (pair, keyName) -> Key(pair.first, pair.second, spoken = keyName) }

    private val hindiRow3 = listOf(
        "ं" to "ँ", "म" to "ण", "न" to "ऩ", "व" to "ळ", "ल" to "ऴ",
        "स" to "श", "य" to "य़"
    ).zip(letterNamesRow3).map { (pair, keyName) -> Key(pair.first, pair.second, spoken = keyName) }

    // Urdu — official Windows/Pakistan Urdu layout. Pair = (normal, shift). Spoken = English key name.
    private val urduRow1 = listOf(
        "ط" to "ظ", "ص" to "ض", "ھ" to "ذ", "د" to "ڈ", "ٹ" to "ث",
        "پ" to "پ", "ت" to "ت", "ب" to "ب", "ج" to "چ", "ح" to "خ"
    ).zip(letterNamesRow1).map { (pair, keyName) -> Key(pair.first, pair.second, spoken = keyName) }

    private val urduRow2 = listOf(
        "م" to "ژ", "و" to "ز", "ر" to "ڑ", "ن" to "ں", "ل" to "ل",
        "ہ" to "ء", "ا" to "آ", "ک" to "گ", "ی" to "ي"
    ).zip(letterNamesRow2).map { (pair, keyName) -> Key(pair.first, pair.second, spoken = keyName) }

    private val urduRow3 = listOf(
        "ق" to "ق", "ف" to "ف", "ے" to "ۓ", "س" to "س",
        "ش" to "ؤ", "غ" to "ئ", "ع" to "ع"
    ).zip(letterNamesRow3).map { (pair, keyName) -> Key(pair.first, pair.second, spoken = keyName) }

    fun row1(lang: Language): List<Key> = when (lang) {
        Language.ENGLISH -> englishRow1
        Language.HINDI, Language.MARATHI -> hindiRow1
        Language.URDU -> urduRow1
    }

    fun row2(lang: Language): List<Key> = when (lang) {
        Language.ENGLISH -> englishRow2
        Language.HINDI, Language.MARATHI -> hindiRow2
        Language.URDU -> urduRow2
    }

    fun row3(lang: Language): List<Key> = when (lang) {
        Language.ENGLISH -> englishRow3
        Language.HINDI, Language.MARATHI -> hindiRow3
        Language.URDU -> urduRow3
    }

    // Punctuation flanking the space bar. (comma, period) — normal/shift pairs.
    // Spoken label stays "Comma"/"Period" in every language, same reasoning as above.
    fun commaKey(lang: Language): Key = when (lang) {
        Language.HINDI, Language.MARATHI -> Key(",", "ष", spoken = "Comma")
        Language.URDU -> Key("،", ">", spoken = "Comma")
        Language.ENGLISH -> Key(",", "<", spoken = "Comma")
    }

    fun periodKey(lang: Language): Key = when (lang) {
        Language.HINDI, Language.MARATHI -> Key(".", "।", spoken = "Period")
        Language.URDU -> Key("۔", "؟", spoken = "Period")
        Language.ENGLISH -> Key(".", ">", spoken = "Period")
    }

    // Numbers / symbols layer — shared across languages for simplicity in v1.
    val symbolsRow1 = "1234567890".map { Key(it.toString()) }
    val symbolsRow2 = "@#\$%&-+()".map { Key(it.toString()) }
    val symbolsRow3 = "*\"':;!?".map { Key(it.toString()) }

    // A small set of common emoji for the emoji panel (v1: flat grid, no categories).
    val emojis = listOf(
        "😀", "😂", "😍", "🙂", "😉", "😊", "😢", "😭", "😡", "😴",
        "👍", "👎", "👏", "🙏", "💪", "❤️", "🎉", "🔥", "⭐", "✅",
        "☀️", "🌧️", "🌙", "🍎", "🍵", "🚗", "🏠", "📞", "⏰", "🎂"
    ).map { Key(it, it, spoken = null, code = KeyCode.EMOJI_CHAR) }
}
