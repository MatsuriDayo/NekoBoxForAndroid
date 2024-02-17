package io.nekohasekai.sagernet.utils

import android.content.Context
import io.nekohasekai.sagernet.ktx.app
import libcore.Libcore
import java.io.File

object GeoipUtils {
    fun generateRuleSet(context: Context = app.applicationContext, country: String) {

        val filesDir = context.getExternalFilesDir(null) ?: context.filesDir

        val ruleSetDir = filesDir.resolve("ruleSets")
        ruleSetDir.mkdirs()

        val geositeFile = File(filesDir, "geoip.db")

        val geoip = Libcore.newGeoip()
        if (!geoip.openGeosite(geositeFile.absolutePath)) {
            error("open geoip failed")
        }

        geoip.convertGeoip(country, ruleSetDir.resolve("geoip-$country.srs").absolutePath)
    }
}