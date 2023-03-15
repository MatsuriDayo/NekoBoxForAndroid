package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.View
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutDebugBinding
import io.nekohasekai.sagernet.ktx.snackbar

class DebugFragment : NamedFragment(R.layout.layout_debug) {

    override fun name0() = "Debug"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutDebugBinding.bind(view)

        binding.debugCrash.setOnClickListener {
            error("test crash")
        }
        binding.resetSettings.setOnClickListener {
            DataStore.configurationStore.reset()
            snackbar("Cleared").show()
        }
    }

}