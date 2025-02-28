package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.SparseBooleanArray
import android.view.*
import android.widget.Filter
import android.widget.Filterable
import androidx.annotation.UiThread
import androidx.core.util.contains
import androidx.core.util.set
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppListBinding
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.ListHolderListener
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import moe.matsuri.nb4a.plugin.NekoPluginManager
import moe.matsuri.nb4a.plugin.Plugins
import moe.matsuri.nb4a.proxy.neko.NekoJSInterface
import moe.matsuri.nb4a.ui.Dialogs
import kotlin.coroutines.coroutineContext

class AppListActivity : ThemedActivity() {
    companion object {
        private const val SWITCH = "switch"
    }

    private class ProxiedApp(
        private val pm: PackageManager, private val appInfo: ApplicationInfo,
        val packageName: String,
    ) {
        val name: CharSequence = appInfo.loadLabel(pm)    // cached for sorting
        val icon: Drawable get() = appInfo.loadIcon(pm)
        val uid get() = appInfo.uid
        val sys get() = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    private inner class AppViewHolder(val binding: LayoutAppsItemBinding) : RecyclerView.ViewHolder(
        binding.root
    ),
        View.OnClickListener {
        private lateinit var item: ProxiedApp

        init {
            binding.root.setOnClickListener(this)
        }

        fun bind(app: ProxiedApp) {
            item = app
            binding.itemicon.setImageDrawable(app.icon)
            binding.title.text = app.name
            if (forNeko) {
                val packageName = app.packageName
                val ver = getCachedApps()[packageName]?.versionName ?: ""
                binding.desc.text = "$packageName ($ver)"
                //
                binding.button.isVisible = true
                binding.button.setImageDrawable(getDrawable(R.drawable.ic_baseline_info_24))
                binding.button.setOnClickListener {
                    runOnIoDispatcher {
                        val jsi = NekoJSInterface(packageName)
                        jsi.init()
                        val about = jsi.getAbout()
                        jsi.destorySuspend()
                        Dialogs.message(
                            this@AppListActivity, app.name as String,
                            "PackageName: ${packageName}\n" +
                                    "Version: ${ver}\n" +
                                    "--------\n" + about
                        )
                    }
                }
            } else {
                binding.desc.text = "${app.packageName} (${app.uid})"
            }
            handlePayload(listOf(SWITCH))
        }

        fun handlePayload(payloads: List<String>) {
            if (payloads.contains(SWITCH)) {
                val selected = isProxiedApp(item)
                binding.itemcheck.isChecked = selected
                binding.button.isVisible = forNeko && selected
            }
        }

        override fun onClick(v: View?) {
            if (isProxiedApp(item)) proxiedUids.delete(item.uid) else proxiedUids[item.uid] = true
            DataStore.routePackages = apps.filter { isProxiedApp(it) }
                .joinToString("\n") { it.packageName }

            if (forNeko) {
                if (isProxiedApp(item)) {
                    runOnIoDispatcher {
                        try {
                            NekoPluginManager.installPlugin(item.packageName)
                        } catch (e: Exception) {
                            // failed UI
                            runOnUiThread { onClick(v) }
                            Dialogs.logExceptionAndShow(this@AppListActivity, e) { }
                        }
                    }
                } else {
                    NekoPluginManager.removeManagedPlugin(item.packageName)
                }
            }

            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>(),
        Filterable,
        FastScrollRecyclerView.SectionedAdapter {
        var filteredApps = apps

        suspend fun reload() {
            apps = getCachedApps().mapNotNull { (packageName, packageInfo) ->
                coroutineContext[Job]!!.ensureActive()
                packageInfo.applicationInfo?.let { ProxiedApp(packageManager, it, packageName) }
            }.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) =
            holder.bind(filteredApps[position])

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: List<Any>) {
            if (payloads.isNotEmpty()) {
                @Suppress("UNCHECKED_CAST") holder.handlePayload(payloads as List<String>)
                return
            }

            onBindViewHolder(holder, position)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder =
            AppViewHolder(LayoutAppsItemBinding.inflate(layoutInflater, parent, false))

        override fun getItemCount(): Int = filteredApps.size

        private val filterImpl = object : Filter() {
            override fun performFiltering(constraint: CharSequence) = FilterResults().apply {
                var filteredApps = if (constraint.isEmpty()) apps else apps.filter {
                    it.name.contains(constraint, true) || it.packageName.contains(
                        constraint, true
                    ) || it.uid.toString().contains(constraint)
                }
                if (!sysApps) filteredApps = filteredApps.filter { !it.sys }
                count = filteredApps.size
                values = filteredApps
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                @Suppress("UNCHECKED_CAST")
                filteredApps = results.values as List<ProxiedApp>
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = filterImpl

        override fun getSectionName(position: Int): String {
            return filteredApps[position].name.firstOrNull()?.toString() ?: ""
        }

    }

    private val loading by lazy { findViewById<View>(R.id.loading) }

    private lateinit var binding: LayoutAppListBinding
    private val proxiedUids = SparseBooleanArray()
    private var loader: Job? = null
    private var apps = emptyList<ProxiedApp>()
    private val appsAdapter = AppsAdapter()

    private fun initProxiedUids(str: String = DataStore.routePackages) {
        proxiedUids.clear()
        val apps = getCachedApps()
        for (line in str.lineSequence()) {
            val app = (apps[line] ?: continue)
            val uid = app.applicationInfo?.uid ?: continue
            proxiedUids[uid] = true
        }
    }

    private fun isProxiedApp(app: ProxiedApp) = proxiedUids[app.uid]

    @UiThread
    private fun loadApps() {
        loader?.cancel()
        loader = lifecycleScope.launchWhenCreated {
            loading.crossFadeFrom(binding.list)
            val adapter = binding.list.adapter as AppsAdapter
            withContext(Dispatchers.IO) { adapter.reload() }
            adapter.filter.filter(binding.search.text?.toString() ?: "")
            binding.list.crossFadeFrom(loading)
        }
    }

    private var forNeko = false

    fun getCachedApps(): MutableMap<String, PackageInfo> {
        val packages =
            if (forNeko) PackageCache.installedPluginPackages else PackageCache.installedPackages
        return packages.toMutableMap().apply {
            remove(BuildConfig.APPLICATION_ID)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forNeko = intent?.hasExtra(Key.NEKO_PLUGIN_MANAGED) == true

        binding = LayoutAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ListHolderListener.setup(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.select_apps)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        initProxiedUids()
        binding.list.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        binding.list.itemAnimator = DefaultItemAnimator()
        binding.list.adapter = appsAdapter

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        binding.search.addTextChangedListener {
            appsAdapter.filter.filter(it?.toString() ?: "")
        }

        binding.showSystemApps.isChecked = sysApps
        binding.showSystemApps.setOnCheckedChangeListener { _, isChecked ->
            sysApps = isChecked
            appsAdapter.filter.filter(binding.search.text?.toString() ?: "")
        }

        if (forNeko) {
            DataStore.routePackages = DataStore.nekoPlugins
            binding.search.setText(Plugins.AUTHORITIES_PREFIX_NEKO_PLUGIN)
        }

        binding.searchLayout.isGone = forNeko
        binding.hintNekoPlugin.isGone = !forNeko
        binding.actionLearnMore.setOnClickListener {
            launchCustomTab("https://matsuridayo.github.io/m-plugin/")
        }

        loadApps()
    }

    private var sysApps = false

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (forNeko) {
            menuInflater.inflate(R.menu.app_list_neko_menu, menu)
        } else {
            menuInflater.inflate(R.menu.app_list_menu, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_invert_selections -> {
                runOnDefaultDispatcher {
                    for (app in apps) {
                        if (proxiedUids.contains(app.uid)) {
                            proxiedUids.delete(app.uid)
                        } else {
                            proxiedUids[app.uid] = true
                        }
                    }
                    DataStore.routePackages = apps.filter { isProxiedApp(it) }
                        .joinToString("\n") { it.packageName }
                    apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter(binding.search.text?.toString() ?: "")
                    }
                }

                return true
            }

            R.id.action_clear_selections -> {
                runOnDefaultDispatcher {
                    proxiedUids.clear()
                    DataStore.routePackages = ""
                    apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter(binding.search.text?.toString() ?: "")
                    }
                }
            }

            R.id.action_export_clipboard -> {
                val success = SagerNet.trySetPrimaryClip("false\n${DataStore.routePackages}")
                Snackbar.make(
                    binding.list,
                    if (success) R.string.action_export_msg else R.string.action_export_err,
                    Snackbar.LENGTH_LONG
                ).show()
                return true
            }

            R.id.action_import_clipboard -> {
                val proxiedAppString =
                    SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!proxiedAppString.isNullOrEmpty()) {
                    val i = proxiedAppString.indexOf('\n')
                    try {
                        val apps = if (i < 0) "" else proxiedAppString.substring(i + 1)
                        DataStore.routePackages = apps
                        Snackbar.make(
                            binding.list, R.string.action_import_msg, Snackbar.LENGTH_LONG
                        ).show()
                        initProxiedUids(apps)
                        appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
                        return true
                    } catch (_: IllegalArgumentException) {
                    }
                }
                Snackbar.make(binding.list, R.string.action_import_err, Snackbar.LENGTH_LONG).show()
            }

            R.id.uninstall_all -> {
                runOnDefaultDispatcher {
                    proxiedUids.clear()
                    DataStore.routePackages = ""
                    apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
                    NekoPluginManager.plugins.forEach {
                        NekoPluginManager.removeManagedPlugin(it)
                    }
                    onMainDispatcher {
                        appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun supportNavigateUpTo(upIntent: Intent) =
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    override fun onKeyUp(keyCode: Int, event: KeyEvent?) = if (keyCode == KeyEvent.KEYCODE_MENU) {
        if (binding.toolbar.isOverflowMenuShowing) binding.toolbar.hideOverflowMenu() else binding.toolbar.showOverflowMenu()
    } else super.onKeyUp(keyCode, event)

    override fun onDestroy() {
        loader?.cancel()
        if (forNeko) DataStore.nekoPlugins = DataStore.routePackages
        super.onDestroy()
    }
}
