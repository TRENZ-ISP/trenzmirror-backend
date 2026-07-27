package com.trenz.mirror.util

/**
 * Generates pairing codes like "7K4X-9M2P" - short enough to read aloud or type on a phone
 * keyboard, using only characters that are hard to confuse with each other (no 0/O, 1/I/L).
 */
object PairingCodeGenerator {
    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generate(): String {
        val part1 = (1..4).map { ALPHABET.random() }.joinToString("")
        val part2 = (1..4).map { ALPHABET.random() }.joinToString("")
        return "$part1-$part2"
    }
}
