package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.aidl.SpeedDisplayData
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.fmt.TAG_BYPASS
import io.nekohasekai.sagernet.fmt.TAG_PROXY
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.*

class TrafficLooper
    (
    val data: BaseService.Data, private val sc: CoroutineScope
) {

    private var job: Job? = null
    private val items = mutableMapOf<Long, TrafficUpdater.TrafficLooperData>() // associate ent id

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

    var selectorNowId = -114514L
    var selectorNowFakeTag = ""

    fun selectMain(id: Long) {
        Logs.d("select traffic count $TAG_PROXY to $id, old id is $selectorNowId")
        val oldData = items[selectorNowId]
        val newData = items[id] ?: return
        oldData?.apply {
            tag = selectorNowFakeTag
            ignore = true
            // post traffic when switch
            if (DataStore.profileTrafficStatistics) {
                data.proxy?.config?.trafficMap?.get(tag)?.firstOrNull()?.let {
                    it.rx = rx
                    it.tx = tx
                    runOnDefaultDispatcher {
                        ProfileManager.updateProfile(it) // update DB
                    }
                }
            }
        }
        selectorNowFakeTag = newData.tag
        selectorNowId = id
        newData.apply {
            tag = TAG_PROXY
            ignore = false
        }
    }

    private suspend fun loop() {
        val delayMs = DataStore.speedInterval.toLong()
        val showDirectSpeed = DataStore.showDirectSpeed
        val profileTrafficStatistics = DataStore.profileTrafficStatistics
        if (delayMs == 0L) return

        var trafficUpdater: TrafficUpdater? = null
        var proxy: ProxyInstance?

        // for display
        var itemMain: TrafficUpdater.TrafficLooperData? = null
        var itemMainBase: TrafficUpdater.TrafficLooperData? = null
        var itemBypass: TrafficUpdater.TrafficLooperData? = null

        while (sc.isActive) {
            delay(delayMs)
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
                            ignore = proxy.config.selectorGroupId >= 0L,
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
                    items[-2] = itemMain!!
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

            // broadcast (MainActivity)
            if (data.state == BaseService.State.Connected
                && data.binder.callbackIdMap.containsValue(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
            ) {
                data.binder.broadcast { b ->
                    if (data.binder.callbackIdMap[b] == SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND) {
                        b.cbSpeedUpdate(speed)
                        if (profileTrafficStatistics) {
                            items.forEach { (id, item) ->
                                b.cbTrafficUpdate(
                                    TrafficData(id = id, rx = item.rx, tx = item.tx) // display
                                )
                            }
                        }
                    }
                }
            }

            // ServiceNotification
            data.notification?.apply {
                if (listenPostSpeed) postNotificationSpeedUpdate(speed)
            }
        }
    }
}