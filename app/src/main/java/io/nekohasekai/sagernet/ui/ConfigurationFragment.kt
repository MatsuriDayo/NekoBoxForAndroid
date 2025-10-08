package io.nekohasekai.sagernet.ui

import android.content.DialogInterface
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import kotlinx.coroutines.delay
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceDataStore
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.proto.UrlTest
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.databinding.LayoutProfileListBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressListBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.FixedGridLayoutManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnLifecycleDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.ktx.showAllowingStateLoss
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.tryToShow
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.profile.JuicitySettingsActivity
import io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.profile.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanGoSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.matsuri.nb4a.Protocols
import moe.matsuri.nb4a.Protocols.getProtocolColor
import moe.matsuri.nb4a.proxy.anytls.AnyTLSSettingsActivity
import moe.matsuri.nb4a.proxy.config.ConfigSettingActivity
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSSettingsActivity
import moe.matsuri.nb4a.ui.ConnectionTestNotification
import okhttp3.internal.closeQuietly
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.collections.set
import androidx.appcompat.app.AlertDialog
import io.nekohasekai.sagernet.database.SubscriptionBean

class ConfigurationFragment @JvmOverloads constructor(
    val select: Boolean = false, val selectedItem: ProxyEntity? = null, val titleRes: Int = 0
) : ToolbarFragment(R.layout.layout_group_list),
    PopupMenu.OnMenuItemClickListener,
    Toolbar.OnMenuItemClickListener,
    SearchView.OnQueryTextListener,
    OnPreferenceDataStoreChangeListener {

    interface SelectCallback {
        fun returnProfile(profileId: Long)
    }

    lateinit var adapter: GroupPagerAdapter
    lateinit var tabLayout: TabLayout
    lateinit var groupPager: ViewPager2

    val alwaysShowAddress by lazy { DataStore.alwaysShowAddress }

    fun getCurrentGroupFragment(): GroupFragment? {
        return try {
            childFragmentManager.findFragmentByTag("f" + DataStore.selectedGroup) as GroupFragment?
        } catch (e: Exception) {
            Logs.e(e)
            null
        }
    }

    fun switchAllGroupFragmentsLayout() {
        adapter.groupFragments.values.forEach { fragment ->
            if (fragment.isAdded && fragment.view != null) {
                fragment.switchLayoutMode()
            }
        }
    }

    val updateSelectedCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageScrolled(
            position: Int, positionOffset: Float, positionOffsetPixels: Int
        ) {
            if (adapter.groupList.size > position) {
                DataStore.selectedGroup = adapter.groupList[position].id
            }
        }
    }

    override fun onQueryTextChange(query: String): Boolean {
        getCurrentGroupFragment()?.adapter?.filter(query)
        return false
    }

    override fun onQueryTextSubmit(query: String): Boolean = false

    @SuppressLint("DetachAndAttachSameFragment")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        if (savedInstanceState != null) {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(false)
                .detach(this)
                .attach(this)
                .commit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!select) {
            toolbar.inflateMenu(R.menu.add_profile_menu)
            toolbar.menu.findItem(R.id.action_global_mode)?.isChecked = DataStore.globalMode
            toolbar.setOnMenuItemClickListener(this)
        } else {
            toolbar.setTitle(titleRes)
            toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
            toolbar.setNavigationOnClickListener {
                requireActivity().finish()
            }
        }

        val searchView = toolbar.findViewById<SearchView>(R.id.action_search)
        if (searchView != null) {
            searchView.setOnQueryTextListener(this)
            searchView.maxWidth = Int.MAX_VALUE

            searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    cancelSearch(searchView)
                }
            }
        }

        groupPager = view.findViewById(R.id.group_pager)
        tabLayout = view.findViewById(R.id.group_tab)
        adapter = GroupPagerAdapter()
        ProfileManager.addListener(adapter)
        GroupManager.addListener(adapter)

        groupPager.adapter = adapter
        groupPager.offscreenPageLimit = 2

        TabLayoutMediator(tabLayout, groupPager) { tab, position ->
            if (adapter.groupList.size > position) {
                tab.text = adapter.groupList[position].displayName()
            }
            tab.view.setOnLongClickListener { // clear toast
                true
            }
        }.attach()

        toolbar.setOnClickListener {
            val fragment = getCurrentGroupFragment()

            if (fragment != null) {
                val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                val selectedProfileIndex =
                    fragment.adapter!!.configurationIdList.indexOf(selectedProxy)
                if (selectedProfileIndex != -1) {
                    val layoutManager = fragment.layoutManager
                    if (layoutManager is LinearLayoutManager) {
                        val first = layoutManager.findFirstVisibleItemPosition()
                        val last = layoutManager.findLastVisibleItemPosition()

                        if (selectedProfileIndex !in first..last) {
                            fragment.configurationListView.scrollTo(selectedProfileIndex, true)
                            return@setOnClickListener
                        }
                    } else {
                        fragment.configurationListView.scrollTo(selectedProfileIndex, true)
                        return@setOnClickListener
                    }

                }

                fragment.configurationListView.scrollTo(0)
            }

        }

        DataStore.profileCacheStore.registerChangeListener(this)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.action_global_mode)?.isChecked = DataStore.globalMode
        super.onPrepareOptionsMenu(menu)
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        runOnMainDispatcher {
            // editingGroup
            if (key == Key.PROFILE_GROUP) {
                val targetId = DataStore.editingGroup
                if (targetId > 0 && targetId != DataStore.selectedGroup) {
                    DataStore.selectedGroup = targetId
                    val targetIndex = adapter.groupList.indexOfFirst { it.id == targetId }
                    if (targetIndex >= 0) {
                        groupPager.setCurrentItem(targetIndex, false)
                    } else {
                        adapter.reload()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)

        if (::adapter.isInitialized) {
            GroupManager.removeListener(adapter)
            ProfileManager.removeListener(adapter)
        }

        super.onDestroy()
    }

    override fun onKeyDown(ketCode: Int, event: KeyEvent): Boolean {
        val fragment = getCurrentGroupFragment()
        fragment?.configurationListView?.apply {
            if (!hasFocus()) requestFocus()
        }
        return super.onKeyDown(ketCode, event)
    }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
            if (file != null) runOnDefaultDispatcher {
                try {
                    val fileName =
                        requireContext().contentResolver.query(file, null, null, null, null)
                            ?.use { cursor ->
                                cursor.moveToFirst()
                                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                                    .let(cursor::getString)
                            }
                    val proxies = mutableListOf<AbstractBean>()
                    if (fileName != null && fileName.endsWith(".zip")) {
                        // try parse wireguard zip
                        val zip =
                            ZipInputStream(requireContext().contentResolver.openInputStream(file)!!)
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.isDirectory) continue
                            val fileText = zip.bufferedReader().readText()
                            RawUpdater.parseRaw(fileText, entry.name)
                                ?.let { pl -> proxies.addAll(pl) }
                            zip.closeEntry()
                        }
                        zip.closeQuietly()
                    } else {
                        val fileText =
                            requireContext().contentResolver.openInputStream(file)!!.use {
                                it.bufferedReader().readText()
                            }
                        RawUpdater.parseRaw(fileText, fileName ?: "")
                            ?.let { pl -> proxies.addAll(pl) }
                    }
                    if (proxies.isEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.no_proxies_found_in_file)).show()
                    } else import(proxies)
                } catch (e: SubscriptionFoundException) {
                    (requireActivity() as MainActivity).importSubscription(e.link.toUri())
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }
            }
        }

    suspend fun import(proxies: List<AbstractBean>) {
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        onMainDispatcher {
            DataStore.editingGroup = targetId
            snackbar(
                requireContext().resources.getQuantityString(
                    R.plurals.added, proxies.size, proxies.size
                )
            ).show()
        }

    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_qr_code -> {
                startActivity(Intent(context, ScannerActivity::class.java))
            }

            R.id.action_import_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                } else runOnDefaultDispatcher {
                    try {
                        val proxies = RawUpdater.parseRaw(text)
                        if (proxies.isNullOrEmpty()) {
                            onMainDispatcher {
                                snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                            }
                        } else {
                            import(proxies)
                        }
                    } catch (e: SubscriptionFoundException) {
                        onMainDispatcher {
                            val subscriptionLink = Uri.parse(e.link).getQueryParameter("url") ?: e.link
                            
                            val group = ProxyGroup(type = GroupType.SUBSCRIPTION)
                            val subscription = SubscriptionBean()
                            group.subscription = subscription
                            subscription.link = subscriptionLink
                            subscription.autoUpdate = false
                            group.name = ""
                            startActivity(Intent(requireContext(), GroupSettingsActivity::class.java).apply {
                                putExtra(GroupSettingsActivity.EXTRA_FROM_CLIPBOARD, true)
                                putExtra(GroupSettingsActivity.EXTRA_GROUP_SUBSCRIPTION_LINK, subscriptionLink)
                            })
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }

            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
            }

            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
            }

            R.id.action_new_http -> {
                startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
            }

            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
            }

            R.id.action_new_vmess -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
            }

            R.id.action_new_vless -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java).apply {
                    putExtra("vless", true)
                })
            }

            R.id.action_new_trojan -> {
                startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
            }

            R.id.action_new_trojan_go -> {
                startActivity(Intent(requireActivity(), TrojanGoSettingsActivity::class.java))
            }

            R.id.action_new_mieru -> {
                startActivity(Intent(requireActivity(), MieruSettingsActivity::class.java))
            }

            R.id.action_new_naive -> {
                startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
            }

            R.id.action_new_hysteria -> {
                startActivity(Intent(requireActivity(), HysteriaSettingsActivity::class.java))
            }

            R.id.action_new_tuic -> {
                startActivity(Intent(requireActivity(), TuicSettingsActivity::class.java))
            }

            R.id.action_new_juicity -> {
                startActivity(Intent(requireActivity(), JuicitySettingsActivity::class.java))
            }

            R.id.action_new_ssh -> {
                startActivity(Intent(requireActivity(), SSHSettingsActivity::class.java))
            }

            R.id.action_new_wg -> {
                startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java))
            }

            R.id.action_new_shadowtls -> {
                startActivity(Intent(requireActivity(), ShadowTLSSettingsActivity::class.java))
            }

            R.id.action_new_anytls -> {
                startActivity(Intent(requireActivity(), AnyTLSSettingsActivity::class.java))
            }

            R.id.action_new_config -> {
                startActivity(Intent(requireActivity(), ConfigSettingActivity::class.java))
            }

            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
            }

            R.id.action_update_subscription -> {
                val group = DataStore.currentGroup()
                if (group.type != GroupType.SUBSCRIPTION) {
                    snackbar(R.string.group_not_subscription).show()
                    Logs.e("onMenuItemClick: Group(${group.displayName()}) is not subscription")
                } else {
                    runOnLifecycleDispatcher {
                        GroupUpdater.startUpdate(group, true)
                    }
                }
            }

            R.id.action_clear_traffic_statistics -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.tx != 0L || profile.rx != 0L) {
                            profile.tx = 0
                            profile.rx = 0
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }

            R.id.action_connection_test_clear_results -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != 0) {
                            profile.status = 0
                            profile.ping = 0
                            profile.error = null
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }

            R.id.action_connection_test_delete_unavailable -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != 0 && profile.status != 1) {
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(R.string.delete_confirm_prompt)
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
                                            val index = configurationIdList.indexOf(profile.id)
                                            if (index >= 0) {
                                                configurationIdList.removeAt(index)
                                                configurationList.remove(profile.id)
                                                notifyItemRemoved(index)
                                            }
                                        }
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        }
                    }
                }
            }

            R.id.action_remove_duplicate -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
                    for (pf in profiles) {
                        val proxy = Protocols.Deduplication(pf.requireBean(), pf.displayType())
                        if (!uniqueProxies.add(proxy)) {
                            toClear += pf
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(
                                    getString(R.string.delete_confirm_prompt) + "\n" +
                                            toClear.mapIndexedNotNull { index, proxyEntity ->
                                                if (index < 20) {
                                                    proxyEntity.displayName()
                                                } else if (index == 20) {
                                                    "......"
                                                } else {
                                                    null
                                                }
                                            }.joinToString("\n")
                                )
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.groupFragments[DataStore.selectedGroup]?.adapter?.apply {
                                            val index = configurationIdList.indexOf(profile.id)
                                            if (index >= 0) {
                                                configurationIdList.removeAt(index)
                                                configurationList.remove(profile.id)
                                                notifyItemRemoved(index)
                                            }
                                        }
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        }
                    }
                }
            }

            R.id.action_connection_tcp_ping -> {
                pingTest(false)
            }

            R.id.action_connection_url_test -> {
                urlTest()
            }

            R.id.action_global_mode -> {
                item.isChecked = !item.isChecked
                DataStore.globalMode = item.isChecked
                if (DataStore.serviceState.canStop) {
                    runOnDefaultDispatcher {
                        try {
                            // 等待一段时间确保配置已保存
                            delay(500)
                            snackbar(getString(R.string.need_reload)).setAction(R.string.apply) {
                                runOnDefaultDispatcher {
                                    try {
                                        // 再次等待确保配置已保存
                                        delay(100)
                                        SagerNet.reloadService()
                                    } catch (e: Exception) {
                                        Logs.w(e)
                                        onMainDispatcher {
                                            snackbar(getString(R.string.service_failed)).show()
                                        }
                                    }
                                }
                            }.show()
                        } catch (e: Exception) {
                            Logs.w(e)
                            onMainDispatcher {
                                snackbar(getString(R.string.service_failed)).show()
                            }
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    inner class TestDialog {
        val binding = LayoutProgressListBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext()).setView(binding.root)
            .setPositiveButton(R.string.minimize) { _, _ ->
                minimize()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                cancel()
            }
            .setCancelable(false)

        lateinit var cancel: () -> Unit
        lateinit var minimize: () -> Unit

        val dialogStatus = AtomicInteger(0) // 1: hidden 2: cancelled
        var notification: ConnectionTestNotification? = null

        val results: MutableSet<ProxyEntity> = ConcurrentHashMap.newKeySet()
        var proxyN = 0
        val finishedN = AtomicInteger(0)

        fun update(profile: ProxyEntity) {
            if (dialogStatus.get() != 2) {
                results.add(profile)
            }
            runOnMainDispatcher {
                val context = context ?: return@runOnMainDispatcher
                val progress = finishedN.addAndGet(1)
                val status = dialogStatus.get()
                notification?.updateNotification(
                    progress,
                    proxyN,
                    progress >= proxyN || status == 2
                )
                if (status >= 1) return@runOnMainDispatcher
                if (!isAdded) return@runOnMainDispatcher

                // refresh dialog

                var profileStatusText: String? = null
                var profileStatusColor = 0

                when (profile.status) {
                    -1 -> {
                        profileStatusText = profile.error
                        profileStatusColor = context.getColorAttr(android.R.attr.textColorSecondary)
                    }

                    0 -> {
                        profileStatusText = getString(R.string.connection_test_testing)
                        profileStatusColor = context.getColorAttr(android.R.attr.textColorSecondary)
                    }

                    1 -> {
                        profileStatusText = getString(R.string.available, profile.ping)
                        profileStatusColor = context.getColour(R.color.material_green_500)
                    }

                    2 -> {
                        profileStatusText = profile.error
                        profileStatusColor = context.getColour(R.color.material_red_500)
                    }

                    3 -> {
                        val err = profile.error ?: ""
                        val msg = Protocols.genFriendlyMsg(err)
                        profileStatusText = if (msg != err) msg else getString(R.string.unavailable)
                        profileStatusColor = context.getColour(R.color.material_red_500)
                    }
                }

                val text = SpannableStringBuilder().apply {
                    append("\n" + profile.displayName())
                    append("\n")
                    append(
                        profile.displayType(),
                        ForegroundColorSpan(context.getProtocolColor(profile.type)),
                        SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append(" ")
                    append(
                        profileStatusText,
                        ForegroundColorSpan(profileStatusColor),
                        SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append("\n")
                }

                binding.nowTesting.text = text
                binding.progress.text = "$progress / $proxyN"
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("EXPERIMENTAL_API_USAGE")
    fun pingTest(icmpPing: Boolean) {
        if (DataStore.runningTest) return else DataStore.runningTest = true
        val test = TestDialog()
        val dialog = test.builder.show()
        val testJobs = mutableListOf<Job>()
        val group = DataStore.currentGroup()

        val mainJob = runOnDefaultDispatcher {
            val profilesList = SagerDatabase.proxyDao.getByGroup(group.id).filter {
                if (icmpPing) {
                    if (it.requireBean().canICMPing()) {
                        return@filter true
                    }
                } else {
                    if (it.requireBean().canTCPing()) {
                        return@filter true
                    }
                }
                return@filter false
            }
            test.proxyN = profilesList.size
            val profiles = ConcurrentLinkedQueue(profilesList)
            repeat(DataStore.connectionTestConcurrent) {
                testJobs.add(launch(Dispatchers.IO) {
                    while (isActive) {
                        val profile = profiles.poll() ?: break

                        profile.status = 0
                        var address = profile.requireBean().serverAddress
                        if (!address.isIpAddress()) {
                            try {
                                SagerNet.underlyingNetwork!!.getAllByName(address).apply {
                                    if (isNotEmpty()) {
                                        address = this[0].hostAddress
                                    }
                                }
                            } catch (ignored: UnknownHostException) {
                            }
                        }
                        if (!isActive) break
                        if (!address.isIpAddress()) {
                            profile.status = 2
                            profile.error = app.getString(R.string.connection_test_domain_not_found)
                            test.update(profile)
                            continue
                        }
                        try {
                            if (icmpPing) {
                                // removed
                            } else {
                                val socket =
                                    SagerNet.underlyingNetwork?.socketFactory?.createSocket()
                                        ?: Socket()
                                try {
                                    socket.soTimeout = 3000
                                    socket.bind(InetSocketAddress(0))
                                    val start = SystemClock.elapsedRealtime()
                                    socket.connect(
                                        InetSocketAddress(
                                            address, profile.requireBean().serverPort
                                        ), 3000
                                    )
                                    if (!isActive) break
                                    profile.status = 1
                                    profile.ping = (SystemClock.elapsedRealtime() - start).toInt()
                                    test.update(profile)
                                } finally {
                                    socket.closeQuietly()
                                }
                            }
                        } catch (e: Exception) {
                            if (!isActive) break
                            val message = e.readableMessage

                            if (icmpPing) {
                                profile.status = 2
                                profile.error = getString(R.string.connection_test_unreachable)
                            } else {
                                profile.status = 2
                                when {
                                    !message.contains("failed:") -> profile.error =
                                        getString(R.string.connection_test_timeout_error)

                                    else -> when {
                                        message.contains("ECONNREFUSED") -> {
                                            profile.error =
                                                getString(R.string.connection_test_refused)
                                        }

                                        message.contains("ENETUNREACH") -> {
                                            profile.error =
                                                getString(R.string.connection_test_unreachable)
                                        }

                                        else -> {
                                            profile.status = 3
                                            profile.error = message
                                        }
                                    }
                                }
                            }
                            test.update(profile)
                        }
                    }
                })
            }

            testJobs.joinAll()

            runOnMainDispatcher {
                test.cancel()
            }
        }
        test.cancel = {
            test.dialogStatus.set(2)
            dialog.dismiss()
            runOnDefaultDispatcher {
                mainJob.cancel()
                testJobs.forEach { it.cancel() }
                test.results.forEach {
                    try {
                        ProfileManager.updateProfile(it)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
                GroupManager.postReload(DataStore.currentGroupId())
                DataStore.runningTest = false
            }
        }
        test.minimize = {
            test.dialogStatus.set(1)
            test.notification = ConnectionTestNotification(
                dialog.context,
                "[${group.displayName()}] ${getString(R.string.connection_test)}"
            )
            dialog.hide()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun urlTest() {
        if (DataStore.runningTest) return else DataStore.runningTest = true
        val test = TestDialog()
        val dialog = test.builder.show()
        val testJobs = mutableListOf<Job>()
        val group = DataStore.currentGroup()

        val mainJob = runOnDefaultDispatcher {
            val profilesList = SagerDatabase.proxyDao.getByGroup(group.id)
            test.proxyN = profilesList.size
            val profiles = ConcurrentLinkedQueue(profilesList)
            repeat(DataStore.connectionTestConcurrent) {
                testJobs.add(launch(Dispatchers.IO) {
                    val urlTest = UrlTest() // note: this is NOT in bg process
                    while (isActive) {
                        val profile = profiles.poll() ?: break
                        profile.status = 0

                        try {
                            val result = urlTest.doTest(profile)
                            profile.status = 1
                            profile.ping = result
                        } catch (e: PluginManager.PluginNotFoundException) {
                            profile.status = 2
                            profile.error = e.readableMessage
                        } catch (e: Exception) {
                            profile.status = 3
                            profile.error = e.readableMessage
                        }

                        test.update(profile)
                    }
                })
            }

            testJobs.joinAll()

            runOnMainDispatcher {
                test.cancel()
            }
        }
        test.cancel = {
            test.dialogStatus.set(2)
            dialog.dismiss()
            runOnDefaultDispatcher {
                mainJob.cancel()
                testJobs.forEach { it.cancel() }
                test.results.forEach {
                    try {
                        ProfileManager.updateProfile(it)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
                GroupManager.postReload(DataStore.currentGroupId())
                DataStore.runningTest = false
            }
        }
        test.minimize = {
            test.dialogStatus.set(1)
            test.notification = ConnectionTestNotification(
                dialog.context,
                "[${group.displayName()}] ${getString(R.string.connection_test)}"
            )
            dialog.hide()
        }
    }

    inner class GroupPagerAdapter : FragmentStateAdapter(this),
        ProfileManager.Listener,
        GroupManager.Listener {

        var selectedGroupIndex = 0
        var groupList: ArrayList<ProxyGroup> = ArrayList()
        var groupFragments: HashMap<Long, GroupFragment> = HashMap()

        fun reload(now: Boolean = false) {

            if (!select) {
                groupPager.unregisterOnPageChangeCallback(updateSelectedCallback)
            }

            runOnDefaultDispatcher {
                var newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                if (newGroupList.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                    newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }
                newGroupList.find { it.ungrouped }?.let {
                    if (SagerDatabase.proxyDao.countByGroup(it.id) == 0L) {
                        newGroupList.remove(it)
                    }
                }

                var selectedGroup = selectedItem?.groupId ?: DataStore.currentGroupId()
                var set = false
                if (selectedGroup > 0L) {
                    selectedGroupIndex = newGroupList.indexOfFirst { it.id == selectedGroup }
                    set = true
                } else if (groupList.size == 1) {
                    selectedGroup = groupList[0].id
                    if (DataStore.selectedGroup != selectedGroup) {
                        DataStore.selectedGroup = selectedGroup
                    }
                }

                val runFunc = if (now) activity?.let { it::runOnUiThread } else groupPager::post
                if (runFunc != null) {
                    runFunc {
                        groupList = newGroupList
                        notifyDataSetChanged()
                        if (set) groupPager.setCurrentItem(selectedGroupIndex, false)
                        val hideTab = groupList.size < 2
                        tabLayout.isGone = hideTab
                        toolbar.elevation = if (hideTab) 0F else dp2px(4).toFloat()
                        if (!select) {
                            groupPager.registerOnPageChangeCallback(updateSelectedCallback)
                        }
                    }
                }
            }
        }

        init {
            reload(true)
        }

        override fun getItemCount(): Int {
            return groupList.size
        }

        override fun createFragment(position: Int): Fragment {
            return GroupFragment().apply {
                proxyGroup = groupList[position]
                groupFragments[proxyGroup.id] = this
                if (position == selectedGroupIndex) {
                    selected = true
                }
            }
        }

        override fun getItemId(position: Int): Long {
            return groupList[position].id
        }

        override fun containsItem(itemId: Long): Boolean {
            return groupList.any { it.id == itemId }
        }

        override suspend fun groupAdd(group: ProxyGroup) {
            tabLayout.post {
                groupList.add(group)

                if (groupList.any { !it.ungrouped }) tabLayout.post {
                    tabLayout.visibility = View.VISIBLE
                }

                notifyItemInserted(groupList.size - 1)
                tabLayout.getTabAt(groupList.size - 1)?.select()
            }
        }

        override suspend fun groupRemoved(groupId: Long) {
            val index = groupList.indexOfFirst { it.id == groupId }
            if (index == -1) return

            tabLayout.post {
                groupList.removeAt(index)
                notifyItemRemoved(index)
            }
        }

        override suspend fun groupUpdated(group: ProxyGroup) {
            val index = groupList.indexOfFirst { it.id == group.id }
            if (index == -1) return

            tabLayout.post {
                tabLayout.getTabAt(index)?.text = group.displayName()
            }
        }

        override suspend fun groupUpdated(groupId: Long) = Unit

        override suspend fun onAdd(profile: ProxyEntity) {
            if (groupList.find { it.id == profile.groupId } == null) {
                DataStore.selectedGroup = profile.groupId
                reload()
            }
        }

        override suspend fun onUpdated(data: TrafficData) = Unit

        override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) = Unit

        override suspend fun onRemoved(groupId: Long, profileId: Long) {
            val group = groupList.find { it.id == groupId } ?: return
            if (group.ungrouped && SagerDatabase.proxyDao.countByGroup(groupId) == 0L) {
                reload()
            }
        }
    }

    class GroupFragment : Fragment() {

        lateinit var proxyGroup: ProxyGroup
        var selected = false

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View {
            return LayoutProfileListBinding.inflate(inflater).root
        }

        lateinit var undoManager: UndoSnackbarManager<ProxyEntity>
        var adapter: ConfigurationAdapter? = null

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)

            if (::proxyGroup.isInitialized) {
                outState.putParcelable("proxyGroup", proxyGroup)
            }
        }

        override fun onViewStateRestored(savedInstanceState: Bundle?) {
            super.onViewStateRestored(savedInstanceState)

            savedInstanceState?.getParcelable<ProxyGroup>("proxyGroup")?.also {
                proxyGroup = it
                onViewCreated(requireView(), null)
            }
        }

        private val isEnabled: Boolean
            get() {
                return DataStore.serviceState.let { it.canStop || it == BaseService.State.Stopped }
            }

        lateinit var layoutManager: RecyclerView.LayoutManager
        private lateinit var itemTouchHelper: ItemTouchHelper
        
        private fun setupItemTouchHelper() {
            if (select) return
            
            if (::itemTouchHelper.isInitialized) {
                itemTouchHelper.attachToRecyclerView(null)
            }
            
            itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, 0) {
                override fun getMovementFlags(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val dragFlags = if (DataStore.groupLayoutMode == 1) {
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    } else {
                        ItemTouchHelper.UP or ItemTouchHelper.DOWN
                    }
                    return makeMovementFlags(dragFlags, 0) // No swipe flags
                }

                override fun getSwipeDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    return 0
                }

                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int {
                    return if (isEnabled) {
                        if (DataStore.groupLayoutMode == 1) {
                            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                        } else {
                            ItemTouchHelper.UP or ItemTouchHelper.DOWN
                        }
                    } else 0
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
                ): Boolean {
                    val fromPosition = viewHolder.bindingAdapterPosition
                    val toPosition = target.bindingAdapterPosition
                    
                    if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                        return false
                    }
                    
                    adapter?.move(fromPosition, toPosition)
                    return true
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    adapter?.commitMove()
                }
            })
            itemTouchHelper.attachToRecyclerView(configurationListView)
        }
        lateinit var configurationListView: RecyclerView

        val select by lazy {
            try {
                (parentFragment as ConfigurationFragment).select
            } catch (e: Exception) {
                Logs.e(e)
                false
            }
        }
        val selectedItem by lazy {
            try {
                (parentFragment as ConfigurationFragment).selectedItem
            } catch (e: Exception) {
                Logs.e(e)
                null
            }
        }

        override fun onResume() {
            super.onResume()

            if (::configurationListView.isInitialized && configurationListView.size == 0) {
                configurationListView.adapter = adapter
                runOnDefaultDispatcher {
                    adapter?.reloadProfiles()
                }
            } else if (!::configurationListView.isInitialized) {
                onViewCreated(requireView(), null)
            }
            checkOrderMenu()
            configurationListView.requestFocus()
        }

        fun checkOrderMenu() {
            if (select) return

            val pf = requireParentFragment() as? ToolbarFragment ?: return
            val menu = pf.toolbar.menu
            val origin = menu.findItem(R.id.action_order_origin)
            val byName = menu.findItem(R.id.action_order_by_name)
            val byDelay = menu.findItem(R.id.action_order_by_delay)
            when (proxyGroup.order) {
                GroupOrder.ORIGIN -> {
                    origin.isChecked = true
                }

                GroupOrder.BY_NAME -> {
                    byName.isChecked = true
                }

                GroupOrder.BY_DELAY -> {
                    byDelay.isChecked = true
                }
            }

            fun updateTo(order: Int) {
                if (proxyGroup.order == order) return
                runOnDefaultDispatcher {
                    proxyGroup.order = order
                    GroupManager.updateGroup(proxyGroup)
                }
            }

            origin.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.ORIGIN)
                true
            }
            byName.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.BY_NAME)
                true
            }
            byDelay.setOnMenuItemClickListener {
                it.isChecked = true
                updateTo(GroupOrder.BY_DELAY)
                true
            }
            
            val layoutSingle = menu.findItem(R.id.action_layout_single)
            val layoutDouble = menu.findItem(R.id.action_layout_double)
            when (DataStore.groupLayoutMode) {
                0 -> layoutSingle.isChecked = true
                1 -> layoutDouble.isChecked = true
            }
            layoutSingle.setOnMenuItemClickListener {
                it.isChecked = true
                if (DataStore.groupLayoutMode != 0) {
                    DataStore.groupLayoutMode = 0
                    (parentFragment as? ConfigurationFragment)?.switchAllGroupFragmentsLayout()
                }
                true
            }
            layoutDouble.setOnMenuItemClickListener {
                it.isChecked = true
                if (DataStore.groupLayoutMode != 1) {
                    DataStore.groupLayoutMode = 1
                    (parentFragment as? ConfigurationFragment)?.switchAllGroupFragmentsLayout()
                }
                true
            }
        }
        
        private fun setupLayoutManager() {
            layoutManager = if (DataStore.groupLayoutMode == 1) {
                FixedGridLayoutManager(configurationListView, 2)
            } else {
                FixedLinearLayoutManager(configurationListView)
            }
        }
        
        fun switchLayoutMode() {
            setupLayoutManager()
            configurationListView.layoutManager = layoutManager
            
            setupItemTouchHelper()
            
            adapter?.notifyDataSetChanged()
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            if (!::proxyGroup.isInitialized) return

            configurationListView = view.findViewById(R.id.configuration_list)
            setupLayoutManager()
            configurationListView.layoutManager = layoutManager
            adapter = ConfigurationAdapter()
            ProfileManager.addListener(adapter!!)
            GroupManager.addListener(adapter!!)
            configurationListView.adapter = adapter
            configurationListView.setItemViewCacheSize(20)

            if (!select) {
                undoManager = UndoSnackbarManager(activity as MainActivity, adapter!!)
                setupItemTouchHelper()
            }

        }

        override fun onDestroy() {
            adapter?.let {
                ProfileManager.removeListener(it)
                GroupManager.removeListener(it)
            }

            super.onDestroy()

            if (!::undoManager.isInitialized) return
            undoManager.flush()
        }

        inner class ConfigurationAdapter : RecyclerView.Adapter<ConfigurationHolder>(),
            ProfileManager.Listener,
            GroupManager.Listener,
            UndoSnackbarManager.Interface<ProxyEntity> {

            init {
                setHasStableIds(true)
            }

            var configurationIdList: MutableList<Long> = mutableListOf()
            val configurationList = HashMap<Long, ProxyEntity>()

            private fun getItem(profileId: Long): ProxyEntity {
                var profile = configurationList[profileId]
                if (profile == null) {
                    profile = ProfileManager.getProfile(profileId)
                    if (profile != null) {
                        configurationList[profileId] = profile
                    }
                }
                return profile!!
            }

            private fun getItemAt(index: Int) = getItem(configurationIdList[index])

            override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int,
            ): ConfigurationHolder {
                return ConfigurationHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_profile, parent, false)
                )
            }

            override fun getItemId(position: Int): Long {
                return configurationIdList[position]
            }

            override fun onBindViewHolder(holder: ConfigurationHolder, position: Int) {
                try {
                    holder.bind(getItemAt(position))
                } catch (ignored: NullPointerException) { // when group deleted
                }
            }

            override fun getItemCount(): Int {
                return configurationIdList.size
            }

            private val updated = HashSet<ProxyEntity>()

            fun filter(name: String) {
                if (name.isEmpty()) {
                    reloadProfiles()
                    return
                }
                configurationIdList.clear()
                val lower = name.lowercase()
                configurationIdList.addAll(configurationList.filter {
                    it.value.displayName().lowercase().contains(lower) ||
                            it.value.displayType().lowercase().contains(lower) ||
                            it.value.displayAddress().lowercase().contains(lower)
                }.keys)
                notifyDataSetChanged()
            }

            fun move(from: Int, to: Int) {
                if (from == to) return
                
                if (DataStore.groupLayoutMode == 1) {
                    moveDualColumn(from, to)
                } else {
                    moveLinear(from, to)
                }
            }
            
            private fun moveLinear(from: Int, to: Int) {
                val first = getItemAt(from)
                var previousOrder = first.userOrder
                val (step, range) = if (from < to) Pair(1, from until to) else Pair(
                    -1, to + 1 downTo from
                )
                for (i in range) {
                    val next = getItemAt(i + step)
                    val order = next.userOrder
                    next.userOrder = previousOrder
                    previousOrder = order
                    configurationIdList[i] = next.id
                    updated.add(next)
                }
                first.userOrder = previousOrder
                configurationIdList[to] = first.id
                updated.add(first)
                notifyItemMoved(from, to)
            }
            
            private fun moveDualColumn(from: Int, to: Int) {
                val draggedItemId = configurationIdList[from]

                configurationIdList.removeAt(from)
                configurationIdList.add(to, draggedItemId)
                
                for (i in configurationIdList.indices) {
                    val item = getItem(configurationIdList[i])
                    val newOrder = (i + 1).toLong()
                    if (item.userOrder != newOrder) {
                        item.userOrder = newOrder
                        updated.add(item)
                    }
                }
                
                notifyItemMoved(from, to)
            }

            fun commitMove() = runOnDefaultDispatcher {
                updated.forEach { SagerDatabase.proxyDao.updateProxy(it) }
                updated.clear()
            }

            fun remove(pos: Int) {
                if (pos < 0) return
                configurationIdList.removeAt(pos)
                notifyItemRemoved(pos)
            }

            override fun undo(actions: List<Pair<Int, ProxyEntity>>) {
                for ((index, item) in actions) {
                    configurationListView.post {
                        configurationList[item.id] = item
                        configurationIdList.add(index, item.id)
                        notifyItemInserted(index)
                    }
                }
            }

            override fun commit(actions: List<Pair<Int, ProxyEntity>>) {
                val profiles = actions.map { it.second }
                runOnDefaultDispatcher {
                    for (entity in profiles) {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    }
                }
            }

            override suspend fun onAdd(profile: ProxyEntity) {
                if (profile.groupId != proxyGroup.id) return

                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    val pos = itemCount
                    configurationList[profile.id] = profile
                    configurationIdList.add(profile.id)
                    notifyItemInserted(pos)
                }
            }

            override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {
                if (profile.groupId != proxyGroup.id) return
                val index = configurationIdList.indexOf(profile.id)
                if (index < 0) return
                configurationListView.post {
                    if (::undoManager.isInitialized) {
                        undoManager.flush()
                    }
                    configurationList[profile.id] = profile
                    notifyItemChanged(index)
                    //
                    val oldProfile = configurationList[profile.id]
                    if (noTraffic && oldProfile != null) {
                        runOnDefaultDispatcher {
                            onUpdated(
                                TrafficData(
                                    id = profile.id,
                                    rx = oldProfile.rx,
                                    tx = oldProfile.tx
                                )
                            )
                        }
                    }
                }
            }

            override suspend fun onUpdated(data: TrafficData) {
                try {
                    val index = configurationIdList.indexOf(data.id)
                    if (index != -1) {
                        val holder = layoutManager.findViewByPosition(index)
                            ?.let { configurationListView.getChildViewHolder(it) } as ConfigurationHolder?
                        if (holder != null) {
                            onMainDispatcher {
                                holder.bind(holder.entity, data)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                }
            }

            override suspend fun onRemoved(groupId: Long, profileId: Long) {
                if (groupId != proxyGroup.id) return
                val index = configurationIdList.indexOf(profileId)
                if (index < 0) return

                configurationListView.post {
                    configurationIdList.removeAt(index)
                    configurationList.remove(profileId)
                    notifyItemRemoved(index)
                }
            }

            override suspend fun groupAdd(group: ProxyGroup) = Unit
            override suspend fun groupRemoved(groupId: Long) = Unit

            override suspend fun groupUpdated(group: ProxyGroup) {
                if (group.id != proxyGroup.id) return
                proxyGroup = group
                reloadProfiles()
            }

            override suspend fun groupUpdated(groupId: Long) {
                if (groupId != proxyGroup.id) return
                proxyGroup = SagerDatabase.groupDao.getById(groupId)!!
                reloadProfiles()
            }

            fun reloadProfiles() {
                var newProfiles = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
                when (proxyGroup.order) {
                    GroupOrder.BY_NAME -> {
                        newProfiles = newProfiles.sortedBy { it.displayName() }

                    }

                    GroupOrder.BY_DELAY -> {
                        newProfiles =
                            newProfiles.sortedBy { if (it.status == 1) it.ping else 114514 }
                    }
                }

                configurationList.clear()
                configurationList.putAll(newProfiles.associateBy { it.id })
                val newProfileIds = newProfiles.map { it.id }

                var selectedProfileIndex = -1

                if (selected) {
                    val selectedProxy = selectedItem?.id ?: DataStore.selectedProxy
                    selectedProfileIndex = newProfileIds.indexOf(selectedProxy)
                }

                configurationListView.post {
                    configurationIdList.clear()
                    configurationIdList.addAll(newProfileIds)
                    notifyDataSetChanged()

                    if (selectedProfileIndex != -1) {
                        configurationListView.scrollTo(selectedProfileIndex, true)
                    } else if (newProfiles.isNotEmpty()) {
                        configurationListView.scrollTo(0, true)
                    }

                }
            }

        }

        val profileAccess = Mutex()
        val reloadAccess = Mutex()

        inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view),
            PopupMenu.OnMenuItemClickListener {

            lateinit var entity: ProxyEntity
            
            private fun showShareMenu(anchor: View, proxyEntity: ProxyEntity) {
                val popup = PopupMenu(requireContext(), anchor)
                popup.menuInflater.inflate(R.menu.profile_share_menu, popup.menu)

                when {
                    !proxyEntity.haveStandardLink() -> {
                        popup.menu.findItem(R.id.action_group_qr).subMenu?.removeItem(R.id.action_standard_qr)
                        popup.menu.findItem(R.id.action_group_clipboard).subMenu?.removeItem(
                            R.id.action_standard_clipboard
                        )
                    }

                    !proxyEntity.haveLink() -> {
                        popup.menu.removeItem(R.id.action_group_qr)
                        popup.menu.removeItem(R.id.action_group_clipboard)
                    }
                }

                if (proxyEntity.nekoBean != null) {
                    popup.menu.removeItem(R.id.action_group_configuration)
                }

                popup.setOnMenuItemClickListener(this)
                popup.show()
            }

            val profileName: TextView = view.findViewById(R.id.profile_name)
            val profileType: TextView = view.findViewById(R.id.profile_type)
            val profileAddress: TextView = view.findViewById(R.id.profile_address)
            val profileStatus: TextView = view.findViewById(R.id.profile_status)

            val trafficText: TextView = view.findViewById(R.id.traffic_text)
            val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
            val editButton: ImageView = view.findViewById(R.id.edit)
            val doubleColumnMenuButton: ImageView = view.findViewById(R.id.double_column_menu)
            val shareLayout: LinearLayout = view.findViewById(R.id.share)
            val shareLayer: LinearLayout = view.findViewById(R.id.share_layer)
            val shareButton: ImageView = view.findViewById(R.id.shareIcon)
            val removeButton: ImageView = view.findViewById(R.id.remove)

            fun bind(proxyEntity: ProxyEntity, trafficData: TrafficData? = null) {
                val pf = parentFragment as? ConfigurationFragment ?: return

                entity = proxyEntity

                if (select) {
                    view.setOnClickListener {
                        (requireActivity() as SelectCallback).returnProfile(proxyEntity.id)
                    }
                } else {
                    view.setOnClickListener {
                        runOnDefaultDispatcher {
                            var update: Boolean
                            var lastSelected: Long
                            profileAccess.withLock {
                                update = DataStore.selectedProxy != proxyEntity.id
                                lastSelected = DataStore.selectedProxy
                                DataStore.selectedProxy = proxyEntity.id
                                onMainDispatcher {
                                    selectedView.visibility = View.VISIBLE
                                }
                            }

                            if (update) {
                                ProfileManager.postUpdate(lastSelected)
                                if (DataStore.serviceState.canStop && reloadAccess.tryLock()) {
                                    SagerNet.reloadService()
                                    reloadAccess.unlock()
                                }
                            } else if (SagerNet.isTv) {
                                if (DataStore.serviceState.started) {
                                    SagerNet.stopService()
                                } else {
                                    SagerNet.startService()
                                }
                            }
                        }

                    }
                }

                profileName.text = proxyEntity.displayName()
                profileType.text = proxyEntity.displayType()
                profileType.setTextColor(requireContext().getProtocolColor(proxyEntity.type))

                var rx = proxyEntity.rx
                var tx = proxyEntity.tx
                if (trafficData != null) {
                    // use new data
                    tx = trafficData.tx
                    rx = trafficData.rx
                }

                val showTraffic = rx + tx != 0L
                trafficText.isVisible = showTraffic
                if (showTraffic) {
                    trafficText.text = view.context.getString(
                        R.string.traffic,
                        Formatter.formatFileSize(view.context, tx),
                        Formatter.formatFileSize(view.context, rx)
                    )
                }

                var address = proxyEntity.displayAddress()
                if (showTraffic && address.length >= 30) {
                    address = address.substring(0, 27) + "..."
                }

                if (proxyEntity.requireBean().name.isBlank() || !pf.alwaysShowAddress) {
                    address = ""
                }

                profileAddress.text = address
                (trafficText.parent as View).isGone =
                    (!showTraffic || proxyEntity.status <= 0) && address.isBlank()

                if (proxyEntity.status <= 0) {
                    if (showTraffic) {
                        profileStatus.text = trafficText.text
                        profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                        trafficText.text = ""
                    } else {
                        profileStatus.text = ""
                    }
                } else if (proxyEntity.status == 1) {
                    profileStatus.text = getString(R.string.available, proxyEntity.ping)
                    profileStatus.setTextColor(requireContext().getColour(R.color.material_green_500))
                } else {
                    profileStatus.setTextColor(requireContext().getColour(R.color.material_red_500))
                    if (proxyEntity.status == 2) {
                        profileStatus.text = proxyEntity.error
                    }
                }

                if (proxyEntity.status == 3) {
                    val err = proxyEntity.error ?: "<?>"
                    val msg = Protocols.genFriendlyMsg(err)
                    profileStatus.text = if (msg != err) msg else getString(R.string.unavailable)
                    profileStatus.setOnClickListener {
                        alert(err).tryToShow()
                    }
                } else {
                    profileStatus.setOnClickListener(null)
                }

                editButton.setOnClickListener {
                    it.context.startActivity(
                        proxyEntity.settingIntent(
                            it.context, proxyGroup.type == GroupType.SUBSCRIPTION
                        )
                    )
                }

                removeButton.setOnClickListener {
                    adapter?.let { adapter ->
                        val index = adapter.configurationIdList.indexOf(proxyEntity.id)
                        if (DataStore.confirmProfileDelete) {
                            AlertDialog.Builder(requireContext())
                                .setTitle(R.string.delete_confirm_prompt)
                                // .setMessage(getString(R.string.delete_confirm_prompt))
                                .setPositiveButton(R.string.yes) { dialog: DialogInterface, which: Int ->
                                    adapter.remove(index)
                                    undoManager.remove(index to proxyEntity)
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        } else {
                            adapter.remove(index)
                            undoManager.remove(index to proxyEntity)
                        }
                    }
                }
                
                doubleColumnMenuButton.setOnClickListener {
                    val popup = PopupMenu(requireContext(), it)
                    popup.menuInflater.inflate(R.menu.double_column_item_menu, popup.menu)
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit -> {
                                it.context.startActivity(
                                    proxyEntity.settingIntent(
                                        it.context, proxyGroup.type == GroupType.SUBSCRIPTION
                                    )
                                )
                                true
                            }
                            R.id.action_share -> {
                                showShareMenu(it, proxyEntity)
                                true
                            }
                            R.id.action_delete -> {
                                adapter?.let { adapter ->
                                    val index = adapter.configurationIdList.indexOf(proxyEntity.id)
                                    if (DataStore.confirmProfileDelete) {
                                        AlertDialog.Builder(requireContext())
                                            .setTitle(R.string.delete_confirm_prompt)
                                            .setPositiveButton(R.string.yes) { dialog: DialogInterface, which: Int ->
                                                adapter.remove(index)
                                                undoManager.remove(index to proxyEntity)
                                            }
                                            .setNegativeButton(R.string.no, null)
                                            .show()
                                    } else {
                                        adapter.remove(index)
                                        undoManager.remove(index to proxyEntity)
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }

                val selectOrChain = select || proxyEntity.type == ProxyEntity.TYPE_CHAIN
                val isDoubleColumn = DataStore.groupLayoutMode == 1
                
                if (isDoubleColumn) {
                    editButton.isGone = true
                    shareLayout.isGone = true
                    removeButton.isGone = true
                    doubleColumnMenuButton.isVisible = true
                } else {
                    shareLayout.isGone = selectOrChain
                    editButton.isGone = select
                    removeButton.isGone = select
                    doubleColumnMenuButton.isGone = true
                }

                proxyEntity.nekoBean?.apply {
                    if (!isDoubleColumn) {
                        shareLayout.isGone = true
                    }
                }

                runOnDefaultDispatcher {
                    val selected = (selectedItem?.id ?: DataStore.selectedProxy) == proxyEntity.id
                    val started =
                        selected && DataStore.serviceState.started && DataStore.currentProfile == proxyEntity.id
                    onMainDispatcher {
                        editButton.isEnabled = !started
                        removeButton.isEnabled = !started
                        selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    }

                    if (!(select || proxyEntity.type == ProxyEntity.TYPE_CHAIN)) {
                        onMainDispatcher {
                            shareLayer.setBackgroundColor(Color.TRANSPARENT)
                            shareButton.setImageResource(R.drawable.ic_social_share)
                            shareButton.setColorFilter(Color.GRAY)
                            shareButton.isVisible = true

                            shareLayout.setOnClickListener {
                                showShareMenu(it, proxyEntity)
                            }
                        }
                    }
                }

            }

            var currentName = ""
            fun showCode(link: String) {
                QRCodeDialog(link, currentName).showAllowingStateLoss(parentFragmentManager)
            }

            fun export(link: String) {
                val success = SagerNet.trySetPrimaryClip(link)
                (activity as MainActivity).snackbar(if (success) R.string.action_export_msg else R.string.action_export_err)
                    .show()
            }

            override fun onMenuItemClick(item: MenuItem): Boolean {
                try {
                    currentName = entity.displayName()!!
                    when (item.itemId) {
                        R.id.action_standard_qr -> showCode(entity.toStdLink())
                        R.id.action_standard_clipboard -> export(entity.toStdLink())
                        R.id.action_universal_qr -> showCode(entity.requireBean().toUniversalLink())
                        R.id.action_universal_clipboard -> export(
                            entity.requireBean().toUniversalLink()
                        )

                        R.id.action_config_export_clipboard -> export(entity.exportConfig().first)
                        R.id.action_config_export_file -> {
                            val cfg = entity.exportConfig()
                            DataStore.serverConfig = cfg.first
                            startFilesForResult(
                                (parentFragment as ConfigurationFragment).exportConfig, cfg.second
                            )
                        }
                    }
                } catch (e: Exception) {
                    Logs.w(e)
                    (activity as MainActivity).snackbar(e.readableMessage).show()
                    return true
                }
                return true
            }
        }

    }

    private val exportConfig =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(data)!!
                            .bufferedWriter()
                            .use {
                                it.write(DataStore.serverConfig)
                            }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

    private fun cancelSearch(searchView: SearchView) {
        searchView.onActionViewCollapsed()
        searchView.clearFocus()
    }

}
