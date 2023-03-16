package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.ktx.onIoDispatcher

class TrafficUpdater(
    private val box: libcore.BoxInstance,
    val items: List<TrafficLooperData>, // contain "bypass"
) {

    class TrafficLooperData(
        // Don't associate proxyEntity
        var tag: String,
        var tx: Long = 0,
        var rx: Long = 0,
        var txRate: Long = 0,
        var rxRate: Long = 0,
        var lastUpdate: Long = 0,
    )

    private suspend fun updateOne(item: TrafficLooperData): TrafficLooperData {
        // last update
        val now = System.currentTimeMillis()
        val interval = now - item.lastUpdate
        item.lastUpdate = now
        if (interval <= 0) return item.apply {
            rxRate = 0
            txRate = 0
        }

        // query
        var tx = 0L
        var rx = 0L
        onIoDispatcher {
            tx = box.queryStats(item.tag, "uplink")
            rx = box.queryStats(item.tag, "downlink")
        }

        // add diff
        item.rx += rx
        item.tx += tx
        item.rxRate = rx * 1000 / interval
        item.txRate = tx * 1000 / interval

        // return diff
        return TrafficLooperData(
            tag = item.tag,
            rx = rx,
            tx = tx,
            rxRate = item.rxRate,
            txRate = item.txRate,
        )
    }

    suspend fun updateAll() {
        val updated = mutableMapOf<String, TrafficLooperData>() // diffs
        items.forEach { item ->
            var diff = updated[item.tag]
            // query a tag only once
            if (diff == null) {
                diff = updateOne(item)
                updated[item.tag] = diff
            } else {
                item.rx += diff.rx
                item.tx += diff.tx
                item.rxRate += diff.rxRate
                item.txRate += diff.txRate
            }
        }
    }
}
