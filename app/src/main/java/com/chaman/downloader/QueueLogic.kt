package com.chaman.downloader

/** Queue item ki halat (DB me naam String me jaata hai). */
enum class QStatus {
    QUEUED, RUNNING, PASS, FAIL, CANCELLED;

    companion object {
        /** Anjaan naam aaye to FAIL (Retry ka raasta khula rahe). */
        fun parse(s: String): QStatus = values().firstOrNull { it.name == s } ?: FAIL
    }
}

private fun rank(s: QStatus): Int = when (s) {
    QStatus.RUNNING -> 0
    QStatus.QUEUED -> 1
    else -> 2
}

/** Queue tab ki list: RUNNING, phir QUEUED (line ke hisaab se), phir baaki (naya upar). */
fun sortForDisplay(items: List<DlItem>): List<DlItem> =
    items.sortedWith(Comparator<DlItem> { a, b ->
        val sa = QStatus.parse(a.status)
        val sb = QStatus.parse(b.status)
        val r = rank(sa).compareTo(rank(sb))
        when {
            r != 0 -> r
            sa == QStatus.QUEUED || sa == QStatus.RUNNING -> compareValuesBy(a, b, { it.addedAt }, { it.id })
            else -> compareValuesBy(b, a, { it.addedAt }, { it.id })
        }
    })

/** Chalti line ka kram: RUNNING pehle, phir QUEUED. Sirf active items. */
fun activeOrder(items: List<DlItem>): List<DlItem> =
    sortForDisplay(items).filter {
        val s = QStatus.parse(it.status)
        s == QStatus.RUNNING || s == QStatus.QUEUED
    }

/** Active line me item ka number (1 se shuru). Active nahi to 0. */
fun queuePosition(items: List<DlItem>, id: Long): Int = activeOrder(items).indexOfFirst { it.id == id } + 1

/** Queue card ka chhota status text. */
fun queueStatusLabel(s: QStatus, position: Int): String = when (s) {
    QStatus.RUNNING -> "Download ho raha hai"
    QStatus.QUEUED -> if (position > 0) "Queue me #$position" else "Queue me"
    QStatus.PASS -> "Complete"
    QStatus.FAIL -> "Fail ho gaya"
    QStatus.CANCELLED -> "Cancel kiya"
}
