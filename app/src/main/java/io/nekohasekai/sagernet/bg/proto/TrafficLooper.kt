package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
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

    private suspend fun loop() {
        val delayMs = DataStore.speedInterval
        val showDirectSpeed = DataStore.showDirectSpeed
        if (delayMs == 0) return

        var trafficUpdater: TrafficUpdater? = null
        var proxy: ProxyInstance?
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
                val tags = hashSetOf("bypass")
                proxy.config.trafficMap.forEach { (tag, ents) ->
                    for (ent in ents) {
                        val item = TrafficUpdater.TrafficLooperData(
                            tag = tag,
                            rx = ent.rx,
                            tx = ent.tx,
                        )
                        if (ent.id == proxy.config.mainEntId) {
                            itemMain = item
                            itemMainBase = TrafficUpdater.TrafficLooperData(
                                tag = tag,
                                rx = ent.rx,
                                tx = ent.tx,
                            )
                            Logs.d("traffic count $tag to main")
                        }
                        items[ent.id] = item
                        tags.add(tag)
                        Logs.d("traffic count $tag to ${ent.id}")
                    }
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