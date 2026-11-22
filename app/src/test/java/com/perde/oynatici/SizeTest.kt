package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SizeTest {
    private fun p(t: String) = File.createTempFile("size", ".txt").apply { writeText(t); deleteOnExit() }.let { Dl.parsePrinted(it) }

    @Test fun videoPlusAudio() = assertEquals(2_000_000_000L + 129_000_000L, p("2000000000 129000000 129000000"))
    @Test fun onlyCombined() = assertEquals(500L, p("0 0 500"))
    @Test fun approxDecimals() = assertEquals(300L, p("100.0 200.0 NA"))
    @Test fun oldFormat() = assertEquals(777L, p("777"))
}
