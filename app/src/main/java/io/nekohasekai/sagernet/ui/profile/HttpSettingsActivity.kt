package io.nekohasekai.sagernet.ui.profile

import io.nekohasekai.sagernet.fmt.http.HttpBean

class HttpSettingsActivity : StandardV2RaySettingsActivity() {

    override fun createEntity() = HttpBean()

}
