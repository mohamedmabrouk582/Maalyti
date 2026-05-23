package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import com.example.ui.viewmodel.MaalytiViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    private val viewModel: MaalytiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppLayout(vm = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppLayout(vm: MaalytiViewModel) {
    var isArabicFirst by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(0) } // 0: Home, 1: History, 2: Analytics, 3: Accounts

    val username by vm.userName.collectAsState()
    val isSyncing by vm.isSyncing.collectAsState()
    val lastSyncStr by vm.lastSyncedStr.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        // Trigger SMS sync sandbox flow inside VM safely
        vm.syncLocalDeviceAndSandboxSms(context)
    }

    LaunchedEffect(Unit) {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val hasReceivePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasReadPermission || !hasReceivePermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                )
            )
        } else {
            vm.syncLocalDeviceAndSandboxSms(context)
        }
    }

    // Collect Viewmodel Events to show beautiful SnackBar notifications
    LaunchedEffect(key1 = true) {
        vm.eventFlow.collectLatest { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Direct RTL dynamic wrapper
    val layoutDirection = if (isArabicFirst) LayoutDirection.Rtl else LayoutDirection.Ltr
    
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = ArabicGlossary.get("app_title", isArabicFirst),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = CosmicTextWhite
                            )
                            if (username != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(CosmicPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${ArabicGlossary.get("logged_in_as", isArabicFirst)}: Mohamed Mabrouk",
                                        fontSize = 11.sp,
                                        color = CosmicPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // 1. Google Account and Firestore syncer
                        if (username == null) {
                            IconButton(
                                onClick = { vm.performGoogleSignIn() },
                                modifier = Modifier.testTag("signin_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Login,
                                    contentDescription = "Sign in with Google",
                                    tint = CosmicPrimary
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        color = CosmicPrimary,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = { vm.triggerFirestoreSync() },
                                        modifier = Modifier.testTag("sync_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = "Firestore Synchronization",
                                            tint = CosmicPrimary
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.performSignOut() }) {
                                    Icon(
                                        imageVector = Icons.Default.Logout,
                                        contentDescription = "Log out",
                                        tint = CosmicCrimson
                                    )
                                }
                            }
                        }

                        // 2. Language localization RTL / LTR toggle
                        IconButton(
                            onClick = { isArabicFirst = !isArabicFirst },
                            modifier = Modifier.testTag("lang_toggle_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Toggle layout language direction",
                                tint = CosmicSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CosmicSlateBg,
                        titleContentColor = CosmicTextWhite
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = CosmicSurface,
                    contentColor = CosmicTextWhite,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val tabs = listOf(
                        Triple(0, Icons.Default.Dashboard, ArabicGlossary.get("accounts", isArabicFirst)),
                        Triple(1, Icons.Default.ReceiptLong, ArabicGlossary.get("transactions", isArabicFirst)),
                        Triple(2, Icons.Default.Analytics, ArabicGlossary.get("reports", isArabicFirst)),
                        Triple(3, Icons.Default.CreditCard, ArabicGlossary.get("budget", isArabicFirst))
                    )

                    tabs.forEach { (index, icon, label) ->
                        val selected = currentTab == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = index },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) CosmicPrimary else CosmicTextMuted
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) CosmicPrimary else CosmicTextMuted
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CosmicPrimary,
                                unselectedIconColor = CosmicTextMuted,
                                indicatorColor = CosmicSurfaceElevated
                            )
                        )
                    }
                }
            },
            containerColor = CosmicSlateBg
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CosmicSlateBg)
            ) {
                // Render current active layout tab
                when (currentTab) {
                    0 -> HomeDashboard(vm = vm, isArabic = isArabicFirst)
                    1 -> TransactionsHistory(vm = vm, isArabic = isArabicFirst)
                    2 -> ReportBars(vm = vm, isArabic = isArabicFirst)
                    3 -> CardsAlerts(vm = vm, isArabic = isArabicFirst)
                }
            }
        }
    }
}
