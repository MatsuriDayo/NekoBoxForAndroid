package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.fmt.TAG_BYPASS
import io.nekohasekai.sagernet.fmt.TAG_PROXY
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class TrafficLooper
    (
    val data: BaseService.Data, private val sc: CoroutineScope
) {

    private var job: Job? = null
    private val items = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>()

    suspend fun stop() {
        job?.cancel()
        // finally traffic post
        if (!DataStore.profileTrafficStatistics) return
        val traffic = mutableMapOf<Long, TrafficData>()
        data.proxy?.config?.trafficMap?.forEach { (_, ents) ->
            for (ent in ents) {
                val item = items[ent.id] ?: return@forEach
                ent.rx = item.rx
                ent.tx = item.tx
                ProfileManager.updateProfile(ent) // update DB
                traffic[ent.id] = TrafficData(
                    id = ent.id,
                    rx = ent.rx,
                    tx = ent.tx,
                )
            }
        }
        data.binder.broadcast { b ->
            for (t in traffic) {
                b.cbTrafficUpdate(t.value)
            }
        }
        Logs.d("finally traffic post done")
    }

    fun start() {
        job = sc.launch { loop() }
    }

    var selectorNowId = -1L
    var selectorNowFakeTag = ""

    fun selectMain(id: Long) {
        Logs.d("select traffic count $TAG_PROXY to $id, old id is $selectorNowId")
        val oldData = items[selectorNowId]
        val data = items[id] ?: return
        oldData?.tag = selectorNowFakeTag
        selectorNowFakeTag = data.tag
        data.tag = TAG_PROXY
        selectorNowId = id
    }

    private suspend fun loop() {
        val delayMs = DataStore.speedInterval
        val showDirectSpeed = DataStore.showDirectSpeed
        if (delayMs == 0) return

        var trafficUpdater: TrafficUpdater? = null
        var proxy: ProxyInstance?

        // for display
        var itemMain: TrafficUpdater.TrafficLooperData? = null
        var itemMainBase: TrafficUpdater.TrafficLooperData? = null
        var itemBypass: TrafficUpdater.TrafficLooperData? = null

        while (sc.isActive) {
            delay(delayMs.toDuration(DurationUnit.MILLISECONDS))
            proxy = data.proxy ?: continue

            if (trafficUpdater == null) {
                if (!proxy.isInitialized()) continue
                items.clear()
                itemBypass = TrafficUpdater.TrafficLooperData(tag = "bypass")
                items[-1] = itemBypass
                //
                val tags = hashSetOf(TAG_PROXY, TAG_BYPASS)
                proxy.config.trafficMap.forEach { (tag, ents) ->
                    tags.add(tag)
                    for (ent in ents) {
                        val item = TrafficUpdater.TrafficLooperData(
                            tag = tag,
                            rx = ent.rx,
                            tx = ent.tx,
                        )
                        if (tag == TAG_PROXY && itemMain == null) {
                            itemMain = item
                            itemMainBase = TrafficUpdater.TrafficLooperData(
                                tag = tag,
                                rx = ent.rx,
                                tx = ent.tx,
                            )
                            Logs.d("traffic count $tag to main to ${ent.id}")
                        }
                        items[ent.id] = item
                        Logs.d("traffic count $tag to ${ent.id}")
                    }
                }
                if (proxy.config.selectorGroupId >= 0L) {
                    itemMain = TrafficUpdater.TrafficLooperData(tag = TAG_PROXY)
                    itemMainBase = TrafficUpdater.TrafficLooperData(tag = TAG_PROXY)
                    selectMain(proxy.config.mainEntId)
                }
                //
                trafficUpdater = TrafficUpdater(
                    box = proxy.box, items = items.values.toList()
                )
                proxy.box.setV2rayStats(tags.joinToString("\n"))
            }

            trafficUpdater.updateAll()
            if (!sc.isActive) return

            // speed
            val speed = SpeedDisplayData(
                itemMain!!.txRate,
                itemMain!!.rxRate,
                if (showDirectSpeed) itemBypass!!.txRate else 0L,
                if (showDirectSpeed) itemBypass!!.rxRate else 0L,
                itemMain!!.tx - itemMainBase!!.tx,
                itemMain!!.rx - itemMainBase!!.rx
            )

            // traffic
            val traffic = mutableMapOf<Long, TrafficData>()
            if (DataStore.profileTrafficStatistics) {
                proxy.config.trafficMap.forEach { (tag, ents) ->
                    for (ent in ents) {
                        val item = items[ent.id] ?: return@forEach
                        ent.rx = item.rx
                        ent.tx = item.tx
//                    ProfileManager.updateProfile(ent) // update DB
                        traffic[ent.id] = TrafficData(
                            id = ent.id,
                            rx = ent.rx,
                            tx = ent.tx,
                        ) // display
                    }
                }
            }

            // broadcast
            data.binder.broadcast { b ->
                b.cbSpeedUpdate(speed)
                for (t in traffic) {
                    b.cbTrafficUpdate(t.value)
                }
            }
        }
    }
}