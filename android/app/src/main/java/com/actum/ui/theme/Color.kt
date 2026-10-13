package com.actum.ui.theme

import androidx.compose.ui.graphics.Color

// Two grounds, one set of names. Dark is the original: amber on deep
// warm ink. Light is the same idea turned over:
// beige paper, near-black ink for words, and the amber deepened until it
// reads on white. Every name below answers for whichever is showing (see
// [Look]), so nothing that draws with them needs to know there are two.
//
// The names say what a colour is for in the dark theme, where they began:
// "Ink" is the ground and "Bone" the words, in either theme.

private fun pick(dark: Long, light: Long) = Color(if (Look.dark) dark else light)

/** The ground. Dark matches @color/brand_ink so the splash hands off without a colour jump. */
val Ink get() = pick(0xFF171310, 0xFFF1E7D6)
/** A surface lifted off the ground: a card, a sheet, a field. */
val InkElevated get() = pick(0xFF221B16, 0xFFFAF4E8)
val InkBorder get() = pick(0xFF2E2620, 0xFFE0D2BC)
/** Quiet words and outlines: counts, hints, an unticked box. */
val InkOutline get() = pick(0xFF7A6E62, 0xFF857866)
/** Words. */
val Bone get() = pick(0xFFEDE3D8, 0xFF1E1A16)
/** Words that step back. */
val BoneDim get() = pick(0xFFBFB2A4, 0xFF5A4F43)

/** The accent, as a button's fill and as words on the ground. Deep enough on paper to carry both. */
val AmberBright get() = pick(0xFFFFC46B, 0xFF9C5800)
val AmberDeep = Color(0xFF8A5A00)
val AmberContainerDark get() = pick(0xFF5A4420, 0xFFD9B06C)
val AmberContainerLight = Color(0xFFFFDCA8)
/** Words on an amber fill. */
val AmberOnContainer get() = pick(0xFF2C1A00, 0xFFFFFBF4)

val Parchment = Color(0xFFFFF8F3)
val ParchmentVariant = Color(0xFFF0E0CF)
val ParchmentBorder = Color(0xFFE2D3C3)
val Umber = Color(0xFF1F1B16)
val UmberDim = Color(0xFF4F4539)

/** Dims the host app behind the capture sheet. The same in both: it darkens someone else's screen. */
val CaptureScrim = Color(0xFF080605)

// The search screens. The app is a frame around other people's screenshots,
// so everything here sits a step off the ground.
/** Behind a thumbnail or a page: a shade off the lifted surface. */
val InkMedia get() = pick(0xFF1C1713, 0xFFE8DCC8)
/** The search field's edge at rest; it turns [AmberContainerDark] while typing. */
val InkFieldBorder get() = pick(0xFF322A23, 0xFFDCCDB5)
/** A sheet's grabber and the empty half of a placeholder line. */
val InkGrabber get() = pick(0xFF3A322A, 0xFFCFC0A8)
/** Section labels that should barely be there ("text found"). */
val InkLabel get() = pick(0xFF5A4E44, 0xFF9C8E7A)
/** Behind the matched word in a result's fragment. */
val HitBackground get() = pick(0xFF3A2C14, 0xFFF6D99A)
