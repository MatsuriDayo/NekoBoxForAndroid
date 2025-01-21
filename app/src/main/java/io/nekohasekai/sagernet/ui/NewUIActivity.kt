package io.nekohasekai.sagernet.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.SagerConnection
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import kotlin.math.roundToInt


class NewUIActivity : ComponentActivity(), SagerConnection.Callback {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33) {
            val checkPermission =
                ContextCompat.checkSelfPermission(this@NewUIActivity, POST_NOTIFICATIONS)
            if (checkPermission != PackageManager.PERMISSION_GRANTED) {
                //动态申请
                ActivityCompat.requestPermissions(
                    this@NewUIActivity, arrayOf(POST_NOTIFICATIONS), 0
                )
            }
        }

        setContent {

            val groupList = remember { mutableStateListOf<ProxyGroup>() }
            val configurationList = remember { mutableStateListOf<ProxyEntity>() }
            var selectedGroup by remember { mutableLongStateOf(0L) }

            val navController = rememberNavController()
            val scrollBehavior = BottomAppBarDefaults.exitAlwaysScrollBehavior()

            LaunchedEffect(Unit) {
                var newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                Log.d(TAG, "onCreate: $newGroupList")
                if (newGroupList.isEmpty()) {
                    // for first launch
                    SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                    newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }

                groupList.addAll(newGroupList)

                selectedGroup = DataStore.currentGroupId().takeIf { it > 0L }
                    ?: newGroupList.first().id.also { DataStore.selectedGroup = it }
                Log.d(TAG, "onCreate: $groupList")
            }

            LaunchedEffect(selectedGroup) {
                configurationList.clear()
                configurationList.addAll(SagerDatabase.proxyDao.getByGroup(selectedGroup))
            }

            MaterialTheme {
                val isCollapsed = scrollBehavior.state.collapsedFraction == 1.0f
                val listState = rememberLazyListState()

                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
                    bottomBar = {
                        AnimatedVisibility(!listState.canScrollBackward || !isCollapsed) {
                            BottomAppBar(
                                modifier = Modifier.layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    scrollBehavior.state.heightOffsetLimit =
                                        -placeable.height.toFloat()
                                    val height =
                                        placeable.height + scrollBehavior.state.heightOffset
                                    layout(placeable.width, height.roundToInt().coerceAtLeast(0)) {
                                        placeable.place(0, 0)
                                    }
                                },
                                scrollBehavior = scrollBehavior,
                            ) {
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_action_description),
                                            contentDescription = stringResource(R.string.menu_configuration)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.menu_configuration)) },
                                    selected = true,
                                    onClick = { }
                                )
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_maps_directions),
                                            contentDescription = stringResource(R.string.menu_route)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.menu_route)) },
                                    selected = false,
                                    onClick = { }
                                )
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painterResource(R.drawable.ic_action_settings),
                                            contentDescription = stringResource(R.string.settings)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.settings)) },
                                    selected = false,
                                    onClick = { }
                                )
                            }
                        }
                    }
                ) { pd ->
                    Column(
                        modifier = Modifier.padding(pd)
                    ) {

                        val scope = rememberCoroutineScope()

                        GroupSwitcher(
                            listState = listState,
                            groupList = groupList,
                            selectedGroup = selectedGroup,
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        )

                        LazyColumnScrollbar(
                            state = listState,
                            settings = ScrollbarSettings.Default
                        ) {
                            LazyColumn(
                                state = listState,
                            ) {
                                items(configurationList, key = {
                                    it.id
                                }) { item ->
                                    ConfigurationCard(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val connection = SagerConnection(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND, true)
    override fun onServiceConnected(service: ISagerNetService) {
        DataStore.serviceState = try {
            BaseService.State.entries[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        DataStore.serviceState = state
    }

    @Composable
    fun ConfigurationCard(proxyEntity: ProxyEntity) {
        Card(modifier = Modifier.padding(16.dp, 8.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        proxyEntity.displayName(),
                        fontWeight = FontWeight.Bold
                    )
                    Text(proxyEntity.displayType())
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        proxyEntity.displayAddress(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        painterResource(R.drawable.ic_image_edit),
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.padding(start = 64.dp)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    fun GroupSwitcher(
        listState: LazyListState,
        groupList: List<ProxyGroup>,
        selectedGroup: Long,
        onClick: () -> Unit = {}
    ) {
        SharedTransitionLayout {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { onClick() },
            ) {
                val isExpanded = listState.canScrollBackward
                val boundsTransform = { _: Rect, _: Rect -> tween<Rect>(550) }

                AnimatedContent(
                    targetState = isExpanded,
                    label = "topBar"
                ) { target ->
                    if (target) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    groupList.firstOrNull { it.id == selectedGroup }?.name
                                        ?: stringResource(R.string.group_default),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.sharedElement(
                                        state = rememberSharedContentState("group_name"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                    )
                                )
                                Text(
                                    "这里应该显示流量",
                                    modifier = Modifier.sharedElement(
                                        state = rememberSharedContentState("traffic"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                    )
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    groupList.firstOrNull { it.id == selectedGroup }?.name
                                        ?: stringResource(R.string.group_default),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.sharedElement(
                                        state = rememberSharedContentState("group_name"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                    )
                                )
                                Text(
                                    "这里应该显示流量",
                                    modifier = Modifier.sharedElement(
                                        state = rememberSharedContentState("traffic"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = boundsTransform,
                                    )
                                )
                            }
                            val surfaceContainer =
                                MaterialTheme.colorScheme.surfaceContainer
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.edit),
                                modifier = Modifier
                                    .drawBehind {
                                        drawCircle(
                                            surfaceContainer,
                                            radius = 24.dp.toPx(),
                                        )
                                    }
                                    .padding(12.dp, 0.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NewUIActivity"
    }
}