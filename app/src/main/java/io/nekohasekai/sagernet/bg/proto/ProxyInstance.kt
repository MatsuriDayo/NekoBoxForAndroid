package io.nekohasekai.sagernet.bg.proto

import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.runOnIoDispatcher
import kotlinx.coroutines.runBlocking

class ProxyInstance(profile: ProxyEntity, val service: BaseService.Interface) :
    BoxInstance(profile) {

    // for TrafficLooper
    private var looper: TrafficLooper? = null

    override fun buildConfig() {
        super.buildConfig()
        Logs.d(config.config)
    }

    override suspend fun init() {
        super.init()
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override fun launch() {
        box.setAsMain()
        super.launch()
        runOnIoDispatcher {
            looper = TrafficLooper(service.data, this)
            looper?.start()
        }
    }

    override fun close() {
        super.close()
        runBlocking {
            looper?.stop()
            looper = null
        }
    }
}
