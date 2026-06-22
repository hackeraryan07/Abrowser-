package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.isSystemInDarkTheme

enum class ThemeMode { System, Light, Dark }

class TabState(
    val id: String = java.util.UUID.randomUUID().toString(),
    var url: androidx.compose.runtime.MutableState<String> = androidx.compose.runtime.mutableStateOf(""),
    var title: androidx.compose.runtime.MutableState<String> = androidx.compose.runtime.mutableStateOf("New Tab"),
    var webView: WebView? = null,
    var canGoBack: androidx.compose.runtime.MutableState<Boolean> = androidx.compose.runtime.mutableStateOf(false),
    var canGoForward: androidx.compose.runtime.MutableState<Boolean> = androidx.compose.runtime.mutableStateOf(false),
    var isLoading: androidx.compose.runtime.MutableState<Boolean> = androidx.compose.runtime.mutableStateOf(false)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.System) }
            var immersiveMode by remember { mutableStateOf(false) }

            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            LaunchedEffect(immersiveMode) {
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (immersiveMode) {
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                BrowserApp(
                    themeMode = themeMode,
                    onThemeChange = { themeMode = it },
                    immersiveMode = immersiveMode,
                    onImmersiveChange = { immersiveMode = it }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserApp(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    immersiveMode: Boolean,
    onImmersiveChange: (Boolean) -> Unit
) {
    val tabs = remember { androidx.compose.runtime.mutableStateListOf(TabState()) }
    var currentTabIndex by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    val currentTab = tabs.getOrNull(currentTabIndex) ?: return
    val currentUrl = currentTab.url.value
    var inputUrl by remember(currentTabIndex) { mutableStateOf(currentUrl) }
    val canGoBack = currentTab.canGoBack.value
    val canGoForward = currentTab.canGoForward.value
    val isLoading = currentTab.isLoading.value

    var showSettings by remember { mutableStateOf(false) }
    var showTabManagement by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    val history = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    val isHome = currentUrl.isEmpty() || currentUrl == "about:blank"
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = canGoBack || !isHome || showSettings || showTabManagement || showHistory) {
        if (showSettings) {
            showSettings = false
        } else if (showTabManagement) {
            showTabManagement = false
        } else if (showHistory) {
            showHistory = false
        } else if (canGoBack) {
            currentTab.webView?.goBack()
        } else {
            currentTab.url.value = ""
            inputUrl = ""
            currentTab.webView?.loadUrl("about:blank")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            currentTab.url.value = ""
                            inputUrl = ""
                            currentTab.webView?.loadUrl("about:blank")
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val url = inputUrl.trim()
                                    if (url.isNotEmpty()) {
                                        keyboardController?.hide()
                                        val loadUrl = if (android.util.Patterns.WEB_URL.matcher(url).matches() || url.startsWith("http://") || url.startsWith("https://")) {
                                            if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                                        } else {
                                            "https://www.google.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}"
                                        }
                                        currentTab.url.value = loadUrl
                                    }
                                }
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { 
                            tabs.add(TabState())
                            currentTabIndex = tabs.lastIndex
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "New Tab", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(24.dp)
                                .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .clickable { showTabManagement = true }
                        ) {
                            Text("${tabs.size}", style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box(contentAlignment = Alignment.Center) {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.defaultMinSize(minWidth = 200.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    IconButton(
                                        onClick = { 
                                            currentTab.webView?.goBack()
                                            menuExpanded = false
                                        },
                                        enabled = canGoBack
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                    IconButton(
                                        onClick = { 
                                            currentTab.webView?.goForward()
                                            menuExpanded = false
                                        },
                                        enabled = canGoForward
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                                    }
                                    IconButton(
                                        onClick = { 
                                            // Handle favorite
                                            menuExpanded = false
                                        }
                                    ) {
                                        Icon(Icons.Default.StarBorder, contentDescription = "Favorite")
                                    }
                                    IconButton(
                                        onClick = { 
                                            currentTab.webView?.reload()
                                            menuExpanded = false
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("New tab") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = { 
                                        menuExpanded = false
                                        tabs.add(TabState())
                                        currentTabIndex = tabs.lastIndex
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = { 
                                        menuExpanded = false
                                        showHistory = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = { 
                                        menuExpanded = false
                                        showSettings = true
                                    }
                                )
                            }
                        }
                    }
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            tabs.forEachIndexed { index, tab ->
                val isTabVisible = (index == currentTabIndex) && !isHome
                
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        tab.webView ?: WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            
                            val hostWebView = this
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    tab.isLoading.value = true
                                    url?.let { 
                                        tab.url.value = it 
                                        if (currentTabIndex == index) inputUrl = it
                                        if (it.isNotEmpty() && it != "about:blank") {
                                            if (history.contains(it)) {
                                                history.remove(it)
                                            }
                                            history.add(0, it)
                                        }
                                    }
                                    tab.canGoBack.value = view?.canGoBack() == true
                                    tab.canGoForward.value = view?.canGoForward() == true
                                }
        
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    tab.isLoading.value = false
                                    tab.canGoBack.value = view?.canGoBack() == true
                                    tab.canGoForward.value = view?.canGoForward() == true
                                    view?.title?.let { t -> tab.title.value = t }
                                    url?.let { 
                                        tab.url.value = it 
                                        if (currentTabIndex == index) inputUrl = it
                                    }
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    val newWebView = WebView(context).apply {
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                hostWebView.loadUrl(request?.url.toString())
                                                return true
                                            }
                                        }
                                    }
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = newWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }
                            }
                            
                            if (tab.url.value.isNotEmpty() && tab.url.value != "about:blank") {
                                loadUrl(tab.url.value)
                            }
                            tab.webView = this
                        }
                    },
                    update = {
                        it.visibility = if (isTabVisible) android.view.View.VISIBLE else android.view.View.GONE
                        if (isTabVisible && tab.url.value.isNotEmpty() && tab.url.value != "about:blank" && it.url != tab.url.value) {
                            it.loadUrl(tab.url.value)
                        }
                    }
                )
            }

            if (isHome) {
                BrowserHomeScreen(
                    onSearch = { query ->
                        keyboardController?.hide()
                        inputUrl = query
                        val loadUrl = if (android.util.Patterns.WEB_URL.matcher(query).matches() || query.startsWith("http://") || query.startsWith("https://")) {
                            if (query.startsWith("http://") || query.startsWith("https://")) query else "https://$query"
                        } else {
                            "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                        }
                        currentTab.url.value = loadUrl
                    }
                )
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            themeMode = themeMode,
            onThemeChange = onThemeChange,
            immersiveMode = immersiveMode,
            onImmersiveChange = onImmersiveChange,
            onBack = { showSettings = false }
        )
    }

    if (showTabManagement) {
        TabManagementScreen(
            tabs = tabs,
            currentTabIndex = currentTabIndex,
            onTabSelected = { index ->
                currentTabIndex = index
                showTabManagement = false
            },
            onTabClosed = { index ->
                tabs.removeAt(index)
                if (tabs.isEmpty()) {
                    tabs.add(TabState())
                    currentTabIndex = 0
                } else if (currentTabIndex >= tabs.size) {
                    currentTabIndex = tabs.size - 1
                } else if (currentTabIndex > index) {
                    currentTabIndex--
                }
            },
            onNewTab = {
                tabs.add(TabState())
                currentTabIndex = tabs.lastIndex
                showTabManagement = false
            },
            onBack = { showTabManagement = false }
        )
    }

    if (showHistory) {
        HistoryScreen(
            history = history,
            onClose = { showHistory = false },
            onUrlClick = { url ->
                showHistory = false
                currentTab.url.value = url
                currentTab.webView?.loadUrl(url)
            },
            onClearHistory = { history.clear() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    history: List<String>,
    onClose: () -> Unit,
    onUrlClick: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = onClearHistory) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No history yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                items(history.size) { index ->
                    val url = history[index]
                    ListItem(
                        headlineContent = { Text(url, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                        modifier = Modifier.clickable { onUrlClick(url) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabManagementScreen(
    tabs: List<TabState>,
    currentTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onTabClosed: (Int) -> Unit,
    onNewTab: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${tabs.size} open tabs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNewTab) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab")
                    }
                }
            )
        }
    ) { innerPadding ->
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
        ) {
            items(tabs.size) { index ->
                val tab = tabs[index]
                val isSelected = index == currentTabIndex
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .height(160.dp)
                        .clickable { onTabSelected(index) },
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tab.title.value, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onTabClosed(index) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close Tab", modifier = Modifier.size(16.dp))
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    immersiveMode: Boolean,
    onImmersiveChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
            
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = { Text(themeMode.name) },
                trailingContent = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text("Change")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("System Default") }, onClick = { onThemeChange(ThemeMode.System); expanded = false })
                            DropdownMenuItem(text = { Text("Light") }, onClick = { onThemeChange(ThemeMode.Light); expanded = false })
                            DropdownMenuItem(text = { Text("Dark") }, onClick = { onThemeChange(ThemeMode.Dark); expanded = false })
                        }
                    }
                }
            )
            
            ListItem(
                headlineContent = { Text("Immersive Mode") },
                supportingContent = { Text("Hides navigation and status bars") },
                trailingContent = {
                    Switch(
                        checked = immersiveMode,
                        onCheckedChange = { onImmersiveChange(it) }
                    )
                }
            )
        }
    }
}

@Composable
fun BrowserHomeScreen(
    onSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("SwiftBrowser", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Private • Secure • Lightweight", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(48.dp))
        var inputQuery by remember { mutableStateOf("") }
        OutlinedTextField(
            value = inputQuery,
            onValueChange = { inputQuery = it },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(56.dp),
            placeholder = { Text("Search or type URL") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { 
                if (inputQuery.trim().isNotEmpty()) {
                    onSearch(inputQuery.trim())
                }
            })
        )
    }
}
