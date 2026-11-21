package com.perde.oynatici

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleTest {
    data class S(val id: Int, val artist: String, val recent: Boolean = false)

    private fun adjacent(l: List<S>) = l.zipWithNext().count { (a, b) -> a.artist.isNotBlank() && a.artist == b.artist }

    private fun maxRun(l: List<S>): Int { var m = 1; var c = 1; for (i in 1 until l.size) { c = if (l[i].artist == l[i - 1].artist) c + 1 else 1; m = maxOf(m, c) }; return m }

    @Test fun neverThreeInARowButNotRigid() {
        // 10 Tarkan, 6 Sezen, 4 Sertab
        val items = (0 until 10).map { S(it, "Tarkan") } + (10 until 16).map { S(it, "Sezen") } + (16 until 20).map { S(it, "Sertab") }
        var pairs = 0
        repeat(50) { seed ->
            val o = SmartShuffle.order(items, { it.artist }, { it.recent }, null, Random(seed))
            assertEquals(items.toSet(), o.toSet())
            assertTrue("seed $seed: ${o.map { it.artist }}", maxRun(o) <= 2)
            pairs += adjacent(o)
        }
        assertTrue("aynı sanatçı arada art arda gelebilmeli", pairs > 50)
    }

    @Test fun recentGoLast() {
        val items = (0 until 8).map { S(it, "A$it", recent = it < 3) }
        val o = SmartShuffle.order(items, { it.artist }, { it.recent }, null, Random(1))
        assertTrue(o.takeLast(3).all { it.recent }); assertTrue(o.take(5).none { it.recent })
    }

    @Test fun respectsCurrentArtist() {
        val items = listOf(S(1, "Tarkan"), S(2, "Sezen"))
        repeat(20) { assertEquals("Sezen", SmartShuffle.order(items, { it.artist }, { false }, "Tarkan", Random(it)).first().artist) }
    }

    @Test fun actuallyRandom() {
        val items = (0 until 30).map { S(it, "") }
        val a = SmartShuffle.order(items, { it.artist }, { false }, null, Random(1))
        val b = SmartShuffle.order(items, { it.artist }, { false }, null, Random(2))
        assertTrue(a != b && a != items)
    }
}
