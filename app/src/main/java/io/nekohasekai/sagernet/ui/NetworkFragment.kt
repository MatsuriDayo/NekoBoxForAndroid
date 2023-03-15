package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.databinding.LayoutNetworkBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Cloudflare
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking

class NetworkFragment : NamedFragment(R.layout.layout_network) {

    override fun name0() = app.getString(R.string.tools_network)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutNetworkBinding.bind(view)
        binding.stunTest.setOnClickListener {
            startActivity(Intent(requireContext(), StunActivity::class.java))
        }

        //Markwon.create(requireContext())
        //    .setMarkdown(binding.wrapLicense, getString(R.string.warp_license))

        binding.warpGenerate.setOnClickListener {
            runBlocking {
                generateWarpConfiguration()
            }
        }
    }

    suspend fun generateWarpConfiguration() {
        val activity = requireActivity() as MainActivity
        val binding = LayoutProgressBinding.inflate(layoutInflater).apply {
            content.setText(R.string.generating)
        }
        var job: Job? = null
        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                job?.cancel()
            }
            .show()
        job = runOnDefaultDispatcher {
            try {
                val bean = Cloudflare.makeWireGuardConfiguration()
                if (isActive) {
                    val groupId = DataStore.selectedGroupForImport()
                    if (DataStore.selectedGroup != groupId) {
                        DataStore.selectedGroup = groupId
                    }
                    onMainDispatcher {
                        activity.displayFragmentWithId(R.id.nav_configuration)
                    }
                    delay(1000L)
                    onMainDispatcher {
                        dialog.dismiss()
                    }
                    ProfileManager.createProfile(groupId, bean)
                }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    if (isActive) {
                        dialog.dismiss()
                        activity.snackbar(e.readableMessage).show()
                    }
                }
            }
        }

    }

}