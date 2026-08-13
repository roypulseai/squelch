package com.squelch.app.util

/** Hex helpers for `ByteArray`. Mirrors [Bytes] but lives in a
 *  separate file so that classes already importing [Bytes] for hex
 *  encoding don't pull in this file via accidental wildcard imports. */
fun ByteArray.toHex(): String = Bytes.hex(this)
