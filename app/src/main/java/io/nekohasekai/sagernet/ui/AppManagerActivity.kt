package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
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
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.core.util.contains
import androidx.core.util.set
import androidx.core.view.ViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.databinding.LayoutAppsBinding
import io.nekohasekai.sagernet.databinding.LayoutAppsItemBinding
import io.nekohasekai.sagernet.databinding.LayoutLoadingBinding
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.crossFadeFrom
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.utils.PackageCache
import io.nekohasekai.sagernet.widget.ListHolderListener
import io.nekohasekai.sagernet.widget.ListListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.DexFile
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

class AppManagerActivity : ThemedActivity() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: AppManagerActivity? = null
        private const val SWITCH = "switch"

        private val cachedApps
            get() = PackageCache.installedPackages.toMutableMap().apply {
                remove(BuildConfig.APPLICATION_ID)
            }
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
            binding.desc.text = "${app.packageName} (${app.uid})"
            binding.itemcheck.isChecked = isProxiedApp(app)
        }

        fun handlePayload(payloads: List<String>) {
            if (payloads.contains(SWITCH)) binding.itemcheck.isChecked = isProxiedApp(item)
        }

        override fun onClick(v: View?) {
            if (isProxiedApp(item)) proxiedUids.delete(item.uid) else proxiedUids[item.uid] = true
            DataStore.individual = apps.filter { isProxiedApp(it) }
                .joinToString("\n") { it.packageName }

            appsAdapter.notifyItemRangeChanged(0, appsAdapter.itemCount, SWITCH)
        }
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>(),
        Filterable,
        FastScrollRecyclerView.SectionedAdapter {
        var filteredApps = apps

        suspend fun reload() {
            apps = cachedApps.map { (packageName, packageInfo) ->
                coroutineContext[Job]!!.ensureActive()
                ProxiedApp(packageManager, packageInfo.applicationInfo, packageName)
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
                @Suppress("UNCHECKED_CAST") filteredApps = results.values as List<ProxiedApp>
                notifyDataSetChanged()
            }
        }

        override fun getFilter(): Filter = filterImpl

        override fun getSectionName(position: Int): String {
            return filteredApps[position].name.firstOrNull()?.toString() ?: ""
        }

    }

    private val loading by lazy { findViewById<View>(R.id.loading) }

    private lateinit var binding: LayoutAppsBinding
    private val proxiedUids = SparseBooleanArray()
    private var loader: Job? = null
    private var apps = emptyList<ProxiedApp>()
    private val appsAdapter = AppsAdapter()

    private fun initProxiedUids(str: String = DataStore.individual) {
        proxiedUids.clear()
        val apps = cachedApps
        for (line in str.lineSequence()) proxiedUids[(apps[line]
            ?: continue).applicationInfo.uid] = true
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ListHolderListener.setup(this)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.proxied_apps)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (!DataStore.proxyApps) {
            DataStore.proxyApps = true
        }

        binding.bypassGroup.check(if (DataStore.bypass) R.id.appProxyModeBypass else R.id.appProxyModeOn)
        binding.bypassGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.appProxyModeDisable -> {
                    DataStore.proxyApps = false
                    finish()
                }
                R.id.appProxyModeOn -> DataStore.bypass = false
                R.id.appProxyModeBypass -> DataStore.bypass = true
            }
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

        instance = this
        loadApps()
    }

    private var sysApps = true

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.per_app_proxy_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_china_apps -> {
                scanChinaApps()
                return true
            }
            R.id.action_invert_selections -> {
                runOnDefaultDispatcher {
                    for (app in apps) {
                        if (proxiedUids.contains(app.uid)) {
                            proxiedUids.delete(app.uid)
                        } else {
                            proxiedUids[app.uid] = true
                        }
                    }
                    DataStore.individual = apps.filter { isProxiedApp(it) }
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
                    DataStore.individual = ""
                    apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))
                    onMainDispatcher {
                        appsAdapter.filter.filter(binding.search.text?.toString() ?: "")
                    }
                }
            }
            R.id.action_export_clipboard -> {
                val success = SagerNet.trySetPrimaryClip("${DataStore.bypass}\n${DataStore.individual}")
                Snackbar.make(
                    binding.list,
                    if (success) R.string.action_export_msg else R.string.action_export_err,
                    Snackbar.LENGTH_LONG
                ).show()
                return true
            }
            R.id.action_import_clipboard -> {
                val proxiedAppString = SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!proxiedAppString.isNullOrEmpty()) {
                    val i = proxiedAppString.indexOf('\n')
                    try {
                        val (enabled, apps) = if (i < 0) {
                            proxiedAppString to ""
                        } else proxiedAppString.substring(
                            0, i
                        ) to proxiedAppString.substring(i + 1)
                        binding.bypassGroup.check(if (enabled.toBoolean()) R.id.appProxyModeBypass else R.id.appProxyModeOn)
                        DataStore.individual = apps
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
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    private fun scanChinaApps() {

        val text: TextView

        val dialog = MaterialAlertDialogBuilder(this).setView(
            LayoutLoadingBinding.inflate(layoutInflater).apply {
                text = loadingText
            }.root
        ).setCancelable(false).show()

        val txt = text.text.toString()

        runOnDefaultDispatcher {
            val chinaApps = ArrayList<Pair<PackageInfo, String>>()
            val chinaRegex = ("(" + arrayOf(
                "com.tencent",
                "com.alibaba",
                "com.umeng",
                "com.qihoo",
                "com.ali",
                "com.alipay",
                "com.amap",
                "com.sina",
                "com.weibo",
                "com.vivo",
                "com.xiaomi",
                "com.huawei",
                "com.taobao",
                "com.secneo",
                "s.h.e.l.l",
                "com.stub",
                "com.kiwisec",
                "com.secshell",
                "com.wrapper",
                "cn.securitystack",
                "com.mogosec",
                "com.secoen",
                "com.netease",
                "com.mx",
                "com.qq.e",
                "com.baidu",
                "com.bytedance",
                "com.bugly",
                "com.miui",
                "com.oppo",
                "com.coloros",
                "com.iqoo",
                "com.meizu",
                "com.gionee",
                "cn.nubia"
            ).joinToString("|") { "${it.replace(".", "\\.")}\\." } + ").*").toRegex()

            val bypass = DataStore.bypass
            val cachedApps = cachedApps

            apps = cachedApps.map { (packageName, packageInfo) ->
                kotlin.coroutines.coroutineContext[Job]!!.ensureActive()
                ProxiedApp(packageManager, packageInfo.applicationInfo, packageName)
            }.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))

            scan@ for ((pkg, app) in cachedApps.entries) {
                /*if (!sysApps && app.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                    continue
                }*/

                val index = appsAdapter.filteredApps.indexOfFirst { it.uid == app.applicationInfo.uid }
                var changed = false

                onMainDispatcher {
                    text.text = (txt + " " + app.packageName + "\n\n" + chinaApps.map { it.second }
                        .reversed()
                        .joinToString("\n", postfix = "\n")).trim()
                }

                try {

                    val dex = File(app.applicationInfo.publicSourceDir)
                    val zipFile = ZipFile(dex)
                    var dexFile: DexFile

                    for (entry in zipFile.entries()) {
                        if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) {
                            val input = zipFile.getInputStream(entry).readBytes()
                            dexFile = try {
                                DexBackedDexFile.fromInputStream(null, input.inputStream())
                            } catch (e: Exception) {
                                Logs.w(e)
                                break
                            }
                            for (clazz in dexFile.classes) {
                                val clazzName = clazz.type.substring(1, clazz.type.length - 1)
                                    .replace("/", ".")
                                    .replace("$", ".")

                                if (clazzName.matches(chinaRegex)) {
                                    chinaApps.add(
                                        app to app.applicationInfo.loadLabel(packageManager)
                                            .toString()
                                    )
                                    zipFile.closeQuietly()

                                    if (bypass) {
                                        changed = !proxiedUids[app.applicationInfo.uid]
                                        proxiedUids[app.applicationInfo.uid] = true
                                    } else {
                                        proxiedUids.delete(app.applicationInfo.uid)
                                    }

                                    continue@scan
                                }
                            }
                        }
                    }
                    zipFile.closeQuietly()

                    if (bypass) {
                        proxiedUids.delete(app.applicationInfo.uid)
                    } else {
                        changed = !proxiedUids[index]
                        proxiedUids[app.applicationInfo.uid] = true
                    }

                } catch (e: ZipException) {
                    Logs.w("Error in pkg ${app.packageName}:${app.versionName}", e)
                    continue
                }

            }

            DataStore.individual = apps.filter { isProxiedApp(it) }
                .joinToString("\n") { it.packageName }

            apps = apps.sortedWith(compareBy({ !isProxiedApp(it) }, { it.name.toString() }))

            onMainDispatcher {
                appsAdapter.filter.filter(binding.search.text?.toString() ?: "")

                dialog.dismiss()
            }

        }


    }

    override fun supportNavigateUpTo(upIntent: Intent) =
        super.supportNavigateUpTo(upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    override fun onKeyUp(keyCode: Int, event: KeyEvent?) = if (keyCode == KeyEvent.KEYCODE_MENU) {
        if (binding.toolbar.isOverflowMenuShowing) binding.toolbar.hideOverflowMenu() else binding.toolbar.showOverflowMenu()
    } else super.onKeyUp(keyCode, event)

    override fun onDestroy() {
        instance = null
        loader?.cancel()
        super.onDestroy()
    }
}
