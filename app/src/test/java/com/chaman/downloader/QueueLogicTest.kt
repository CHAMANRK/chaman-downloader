package com.chaman.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueLogicTest {
    private fun mk(id: Long, st: QStatus, at: Long) = DlItem(
        id = id, url = "u$id", fmt = "", title = "t$id", thumb = "", status = st.name,
        progress = 0f, error = "", savedUri = "", savedMime = "", addedAt = at
    )

    @Test
    fun parseUnknownIsFail() {
        assertEquals(QStatus.PASS, QStatus.parse("PASS"))
        assertEquals(QStatus.FAIL, QStatus.parse("kuch aur"))
        assertEquals(QStatus.QUEUED, QStatus.parse("QUEUED"))
    }

    @Test
    fun displayOrderRunningThenQueuedThenDone() {
        val items = listOf(
            mk(1, QStatus.PASS, 100),
            mk(2, QStatus.QUEUED, 300),
            mk(3, QStatus.RUNNING, 200),
            mk(4, QStatus.QUEUED, 250),
            mk(5, QStatus.FAIL, 400),
            mk(6, QStatus.CANCELLED, 50)
        )
        // RUNNING, QUEUED (purana pehle), baaki (naya upar)
        assertEquals(listOf(3L, 4L, 2L, 5L, 1L, 6L), sortForDisplay(items).map { it.id })
    }

    @Test
    fun queuePositionCountsRunningFirst() {
        val items = listOf(
            mk(1, QStatus.QUEUED, 20),
            mk(2, QStatus.RUNNING, 10),
            mk(3, QStatus.QUEUED, 30),
            mk(4, QStatus.PASS, 5)
        )
        assertEquals(1, queuePosition(items, 2))
        assertEquals(2, queuePosition(items, 1))
        assertEquals(3, queuePosition(items, 3))
        assertEquals(0, queuePosition(items, 4)) // active nahi
        assertEquals(3, activeOrder(items).size)
    }

    @Test
    fun retriedItemGoesToEndOfLine() {
        // Retry addedAt naya karta hai: line ke aakhir me
        val items = listOf(mk(1, QStatus.QUEUED, 500), mk(2, QStatus.QUEUED, 100), mk(3, QStatus.QUEUED, 200))
        assertEquals(listOf(2L, 3L, 1L), activeOrder(items).map { it.id })
    }

    @Test
    fun statusLabels() {
        assertEquals("Queue me #2", queueStatusLabel(QStatus.QUEUED, 2))
        assertEquals("Queue me", queueStatusLabel(QStatus.QUEUED, 0))
        assertEquals("Complete", queueStatusLabel(QStatus.PASS, 0))
        assertEquals("Fail ho gaya", queueStatusLabel(QStatus.FAIL, 0))
        assertEquals("Cancel kiya", queueStatusLabel(QStatus.CANCELLED, 0))
        assertEquals("Download ho raha hai", queueStatusLabel(QStatus.RUNNING, 1))
    }

    @Test
    fun moreInQueueLine() {
        assertEquals("", moreInQueueText(0))
        assertEquals("1 aur queue me", moreInQueueText(1))
        assertEquals("4 aur queue me", moreInQueueText(4))
    }
}
