package dev.ujhhgtg.via.data

data class HistoryRange(val since: Long, val count: Int)

/** bb.v.t: convert ga.b.i's last-hour count and day buckets into the five deletion periods. */
internal fun historyDeletionPeriods(rows: List<HistoryRange>, nowSeconds: Long, rawOffsetSeconds: Int): List<HistoryRange> {
    val today = (nowSeconds + rawOffsetSeconds) / 86400 * 86400 - rawOffsetSeconds
    val starts = longArrayOf(rows[0].since, today, today - 86400, today - 518400, 0)
    val counts = IntArray(starts.size)
    counts[0] = rows[0].count
    for (row in rows.drop(1)) {
        for (index in 1 until starts.size) if (row.since >= starts[index]) counts[index] += row.count
    }
    return starts.indices.map { HistoryRange(starts[it], counts[it]) }
}
