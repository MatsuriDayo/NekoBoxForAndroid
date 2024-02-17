package io.nekohasekai.sagernet.utils

import android.content.Context
import io.nekohasekai.sagernet.ktx.app
import libcore.Geosite
import java.io.File

object GeositeUtils {
    fun generateRuleSet(context: Context = app.applicationContext, code: String) {

        val filesDir = context.getExternalFilesDir(null) ?: context.filesDir

        val ruleSetDir = filesDir.resolve("ruleSets")
        ruleSetDir.mkdirs()

        val geositeFile = File(filesDir, "geosite.db")

        val geosite = Geosite()
        if (!geosite.checkGeositeCode(geositeFile.absolutePath, code)) {
            error("code $code not found in geosite")
        }

        geosite.convertGeosite(code, ruleSetDir.resolve("geosite-$code.srs").absolutePath)
    }
}