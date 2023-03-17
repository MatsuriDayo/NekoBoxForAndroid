package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.util.Linkify
import android.view.View
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.databinding.LayoutAboutBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.plugin.PluginManager.loadString
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.ListHolderListener
import libcore.Libcore
import moe.matsuri.nb4a.plugin.Plugins

class AboutFragment : ToolbarFragment(R.layout.layout_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = LayoutAboutBinding.bind(view)

        ViewCompat.setOnApplyWindowInsetsListener(view, ListHolderListener)
        toolbar.setTitle(R.string.menu_about)

        parentFragmentManager.beginTransaction()
            .replace(R.id.about_fragment_holder, AboutContent())
            .commitAllowingStateLoss()

        runOnDefaultDispatcher {
            val license = view.context.assets.open("LICENSE").bufferedReader().readText()
            onMainDispatcher {
                binding.license.text = license
                Linkify.addLinks(binding.license, Linkify.EMAIL_ADDRESSES or Linkify.WEB_URLS)
            }
        }
    }

    class AboutContent : MaterialAboutFragment() {

        val requestIgnoreBatteryOptimizations = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (resultCode, _) ->
            if (resultCode == Activity.RESULT_OK) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.about_fragment_holder, AboutContent())
                    .commitAllowingStateLoss()
            }
        }

        override fun getMaterialAboutList(activityContext: Context): MaterialAboutList {

            var versionName = BuildConfig.VERSION_NAME
            if (!isOss) {
                versionName += " ${BuildConfig.FLAVOR}"
            }
            if (BuildConfig.DEBUG) {
                versionName += " DEBUG"
            }

            return MaterialAboutList.Builder()
                .addCard(MaterialAboutCard.Builder()
                    .outline(false)
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_update_24)
                        .text(R.string.app_version)
                        .subText(versionName)
                        .setOnClickAction {
                            requireContext().launchCustomTab(
                                "https://github.com/MatsuriDayo/NekoBoxForAndroid/releases"
                            )
                        }
                        .build())
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_layers_24)
                        .text(getString(R.string.version_x, "sing-box"))
                        .subText(Libcore.versionBox())
                        .setOnClickAction { }
                        .build())
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_card_giftcard_24)
                        .text(R.string.donate)
                        .subText(R.string.donate_info)
                        .setOnClickAction {
                            requireContext().launchCustomTab(
                                "https://matsuridayo.github.io/#donate"
                            )
                        }
                        .build())
                    .apply {
                        for ((_, pkg) in PackageCache.installedPluginPackages) {
                            try {
                                val pluginId = pkg.providers[0].loadString(Plugins.METADATA_KEY_ID)
                                if (pluginId.isNullOrBlank() || pluginId.startsWith(Plugins.AUTHORITIES_PREFIX_NEKO_PLUGIN)) continue
                                addItem(MaterialAboutActionItem.Builder()
                                    .icon(R.drawable.ic_baseline_nfc_24)
                                    .text(
                                        getString(
                                            R.string.version_x,
                                            pluginId
                                        ) + " (${Plugins.displayExeProvider(pkg.packageName)})"
                                    )
                                    .subText("v" + pkg.versionName)
                                    .setOnClickAction {
                                        startActivity(Intent().apply {
                                            action =
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                            data = Uri.fromParts(
                                                "package", pkg.packageName, null
                                            )
                                        })
                                    }
                                    .build())
                            } catch (e: Exception) {
                                Logs.w(e)
                            }
                        }
                    }
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val pm = app.getSystemService(Context.POWER_SERVICE) as PowerManager
                            if (!pm.isIgnoringBatteryOptimizations(app.packageName)) {
                                addItem(MaterialAboutActionItem.Builder()
                                    .icon(R.drawable.ic_baseline_running_with_errors_24)
                                    .text(R.string.ignore_battery_optimizations)
                                    .subText(R.string.ignore_battery_optimizations_sum)
                                    .setOnClickAction {
                                        requestIgnoreBatteryOptimizations.launch(
                                            Intent(
                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                Uri.parse("package:${app.packageName}")
                                            )
                                        )
                                    }
                                    .build())
                            }
                        }
                    }
                    .build())
                .addCard(MaterialAboutCard.Builder()
                    .outline(false)
                    .title(R.string.project)
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_baseline_sanitizer_24)
                        .text(R.string.github)
                        .setOnClickAction {
                            requireContext().launchCustomTab(
                                "https://github.com/MatsuriDayo/NekoBoxForAndroid"

                            )
                        }
                        .build())
                    .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_qu_shadowsocks_foreground)
                        .text(R.string.telegram)
                        .setOnClickAction {
                            requireContext().launchCustomTab(
                                "https://t.me/MatsuriDayo"
                            )
                        }
                        .build())
                    .build())
                .build()

        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            view.findViewById<RecyclerView>(R.id.mal_recyclerview).apply {
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            }
        }

    }

}