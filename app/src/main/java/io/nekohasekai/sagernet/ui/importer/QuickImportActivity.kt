package io.nekohasekai.sagernet.ui.importer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.parseProxies
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher

class QuickImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dialog = QuickImportDialogFragment { text, autoConnect ->
            runOnDefaultDispatcher {
                try {
                    val proxies = parseProxies(text)
                    if (proxies.isEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(this@QuickImportActivity)
                                .setMessage(getString(R.string.no_proxies_found))
                                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                                .show()
                        }
                        return@runOnDefaultDispatcher
                    }

                    val profile = proxies[0]
                    val newProfile = ProfileManager.createProfile(DataStore.selectedGroupForImport(), profile as AbstractBean)

                    onMainDispatcher {
                        // select profile
                        DataStore.selectedProxy = newProfile.id

                        // auto connect
                        if (autoConnect) {
                            if (DataStore.serviceState.canStop) SagerNet.reloadService() else SagerNet.startService()
                        }

                        MaterialAlertDialogBuilder(this@QuickImportActivity)
                            .setMessage(getString(R.string.profile_import_message, newProfile.displayName()))
                            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                            .show()
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        MaterialAlertDialogBuilder(this@QuickImportActivity)
                            .setTitle(R.string.action_import_err)
                            .setMessage(e.readableMessage)
                            .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                            .show()
                    }
                }
            }
        }
        dialog.show(supportFragmentManager, "quick_import")
    }

}
