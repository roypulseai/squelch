package com.squelch.app.crypto

import com.squelch.app.util.Bytes

/**
 * Human-friendly "call signs" derived from the public-key fingerprint
 * (spec 3): retro CB/ham-radio style, e.g. "ECHO-4X9K".
 *
 * The call-sign is display-only. Routing/identity always uses the raw
 * public keys. On collision, the caller appends a numeric suffix.
 */
object Callsign {
    private val WORDS = listOf(
        "ALPHA", "BANJO", "COYOTE", "DAMON", "ECHO", "FOX", "GRACE", "HALCYON",
        "IVORY", "JUPITER", "KILO", "LIMELIGHT", "MOJO", "NIMBUS", "ORION", "PIXEL",
        "QUASAR", "RADIO", "SQUELCH", "THUNDER", "UPLINK", "VANTA", "WAVERLY", "XENON",
        "YARDBIRD", "ZEBRA", "ACORN", "BARLEY", "CEDAR", "DUNE", "EMERALD", "FERN",
        "GALENA", "HARBOR", "INDIGO", "JUNIPER", "KETTLE", "LOON", "MERIDIAN", "NEON",
        "OCEAN", "PALISADE", "QUARRY", "RAVEN", "SAGE", "TUNDRA", "UMBRA", "VIGIL",
        "WILLOW", "YUCCA", "AMBER", "BRASS", "CHROME", "DIESEL", "EMBER", "FLINT",
        "GEODE", "HACKLE", "IRIS", "JET", "KINDLE", "LANTERN", "MAGMA", "NICKEL",
        "OBSIDIAN", "PHANTOM", "QUILL", "RELAY", "SIREN", "TOPaz", "URCHIN", "VOLT",
        "WIRE", "XRAY", "YOKE", "ZINC", "AXLE", "BLINK", "CORSAIR", "DRIFT",
        "EASY", "FRAME", "GIMLET", "HAWK", "ICON", "JIG", "KESTREL", "LYRE",
        "MIRROR", "NADIR", "OXIDE", "PING", "QUAKE", "RUST", "STITCH", "TOLL",
        "UNION", "VAPOR", "WHEAT", "AXIS", "BEACON", "CIPHER", "DRONE", "ELM",
        "FLOOD", "GOOSE", "HERON", "INK", "JOULE", "KUDZU", "LARK", "MOSS",
        "NOOK", "OAK", "PLUM", "QUOTIENT", "RIDGE", "STONE", "THISTLE", "URN",
        "VALE", "WEED", "XERXES", "YEW", "ZORRO", "ADOBE", "BASALT", "COBALT",
        "DELTA", "ELK", "FALCON", "GRAVEL", "HONDA", "IBEX", "JADE", "KOI",
        "LUMBER", "MICA", "NIGHTSHADE", "OPAL", "PIP", "QUOTA", "REDWOOD", "SABLE",
        "TALON", "UMPIRE", "VORTEX", "WASP", "XENIAL", "YACHT", "ZEPHYR"
    )

    private const val WORD_COUNT = 10 // first 10 hex chars select words

    /** Derive a call-sign from an identity fingerprint. */
    fun fromFingerprint(fingerprint: ByteArray): String {
        val hex = Bytes.hex(fingerprint)
        val w1 = Integer.parseInt(hex.substring(0, 4), 16) % WORDS.size
        val w2 = Integer.parseInt(hex.substring(4, 8), 16) % WORDS.size
        val code = hex.substring(8, 12).uppercase()
        return "${WORDS[w1]}-${WORDS[w2]}-$code"
    }

    /** Short display (e.g., for blips): first word + code. */
    fun shortFrom(fingerprint: ByteArray): String {
        val hex = Bytes.hex(fingerprint)
        val w1 = Integer.parseInt(hex.substring(0, 4), 16) % WORDS.size
        val code = hex.substring(8, 12).uppercase()
        return "${WORDS[w1]}-$code"
    }

    /** Disambiguate a colliding call-sign by appending a suffix. */
    fun disambiguate(callsign: String, occurrence: Int): String = if (occurrence <= 1) callsign else "$callsign#$occurrence"
}
