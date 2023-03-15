package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.trojan.TrojanBean

class TrojanSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = TrojanBean()

}
