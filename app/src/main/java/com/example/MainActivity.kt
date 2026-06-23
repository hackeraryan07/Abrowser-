package com.example

import android.annotation.SuppressLint
import kotlinx.coroutines.launch
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import com.example.ui.theme.MyApplicationTheme

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

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

@androidx.compose.foundation.layout.ExperimentalLayoutApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val sharedPreferences = remember { context.getSharedPreferences("browser_prefs", android.content.Context.MODE_PRIVATE) }
            
            var themeMode by remember { 
                mutableStateOf(ThemeMode.valueOf(sharedPreferences.getString("theme", ThemeMode.System.name) ?: ThemeMode.System.name)) 
            }
            var immersiveMode by remember { 
                mutableStateOf(sharedPreferences.getBoolean("immersive", false)) 
            }
            var showFullScreenButton by remember {
                mutableStateOf(sharedPreferences.getBoolean("showFullScreenButton", false))
            }

            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible

            LaunchedEffect(immersiveMode, isImeVisible) {
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
                if (immersiveMode) {
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (!isImeVisible) {
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    }
                } else {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                BrowserApp(
                    themeMode = themeMode,
                    onThemeChange = { 
                        themeMode = it
                        sharedPreferences.edit().putString("theme", it.name).apply()
                    },
                    immersiveMode = immersiveMode,
                    onImmersiveChange = { 
                        immersiveMode = it
                        sharedPreferences.edit().putBoolean("immersive", it).apply()
                    },
                    showFullScreenButton = showFullScreenButton,
                    onShowFullScreenButtonChange = {
                        showFullScreenButton = it
                        sharedPreferences.edit().putBoolean("showFullScreenButton", it).apply()
                    }
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
    onImmersiveChange: (Boolean) -> Unit,
    showFullScreenButton: Boolean,
    onShowFullScreenButtonChange: (Boolean) -> Unit
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("browser_prefs", android.content.Context.MODE_PRIVATE) }
    
    val tabs = remember { 
        val savedTabsJson = sharedPreferences.getString("saved_tabs", null)
        val initialTabs = androidx.compose.runtime.mutableStateListOf<TabState>()
        if (savedTabsJson != null) {
            try {
                val jsonArray = org.json.JSONArray(savedTabsJson)
                for (i in 0 until jsonArray.length()) {
                    val url = jsonArray.getString(i)
                    initialTabs.add(TabState(url = androidx.compose.runtime.mutableStateOf(url)))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (initialTabs.isEmpty()) {
            initialTabs.add(TabState())
        }
        initialTabs
    }
    var currentTabIndex by remember { 
        androidx.compose.runtime.mutableIntStateOf(
            sharedPreferences.getInt("current_tab_index", 0).coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        )
    }

    var filePathCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<android.net.Uri>>?>(null) }
    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val uris = if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    var pendingWebViewPermissionRequest by remember { mutableStateOf<android.webkit.PermissionRequest?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        pendingWebViewPermissionRequest?.let { req ->
            val grantedResources = mutableListOf<String>()
            req.resources.forEach { res ->
                val androidPerm = when (res) {
                    android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
                    android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
                    else -> null
                }
                if (androidPerm == null || permissions.getOrDefault(androidPerm, false) || androidx.core.content.ContextCompat.checkSelfPermission(context, androidPerm) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    grantedResources.add(res)
                }
            }
            if (grantedResources.isNotEmpty()) {
                req.grant(grantedResources.toTypedArray())
            } else {
                req.deny()
            }
            pendingWebViewPermissionRequest = null
        }
    }

    var pendingGeoCallback by remember { mutableStateOf<android.webkit.GeolocationPermissions.Callback?>(null) }
    var pendingGeoOrigin by remember { mutableStateOf<String?>(null) }
    val geoPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFine = permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        pendingGeoCallback?.invoke(pendingGeoOrigin, hasFine || hasCoarse, false)
        pendingGeoCallback = null
        pendingGeoOrigin = null
    }

    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val downloadPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingDownloadAction?.invoke()
        pendingDownloadAction = null
    }

    val popupWebViews = remember { androidx.compose.runtime.mutableStateListOf<WebView>() }

    LaunchedEffect(tabs.map { it.url.value }, currentTabIndex) {
        val jsonArray = org.json.JSONArray()
        for (tab in tabs) {
            jsonArray.put(tab.url.value)
        }
        sharedPreferences.edit()
            .putString("saved_tabs", jsonArray.toString())
            .putInt("current_tab_index", currentTabIndex)
            .apply()
    }

    val currentTab = tabs.getOrNull(currentTabIndex) ?: return
    val currentUrl = currentTab.url.value
    var inputUrl by remember(currentTabIndex) { mutableStateOf(currentUrl) }
    val canGoBack = currentTab.canGoBack.value
    val canGoForward = currentTab.canGoForward.value
    val isLoading = currentTab.isLoading.value

    var showSettings by remember { mutableStateOf(false) }
    var showTabManagement by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var fullScreenMode by remember { mutableStateOf(false) }
    val history = remember { androidx.compose.runtime.mutableStateListOf<String>() }

    val isHome = currentUrl.isEmpty() || currentUrl == "about:blank"
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var isInputUrlFocused by remember { mutableStateOf(false) }

    LaunchedEffect(currentUrl, isInputUrlFocused) {
        if (!isInputUrlFocused) {
            inputUrl = currentUrl
        }
    }
    var appBarSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(inputUrl, isInputUrlFocused) {
        if (isInputUrlFocused && inputUrl.trim().isNotEmpty() && inputUrl != currentUrl) {
            appBarSuggestions = fetchSuggestions(inputUrl.trim())
        } else {
            appBarSuggestions = emptyList()
        }
    }

    BackHandler(enabled = isMenuOpen || canGoBack || !isHome || showSettings || showTabManagement || showHistory || showDownloads || appBarSuggestions.isNotEmpty() || isInputUrlFocused) {
        if (isMenuOpen) {
            isMenuOpen = false
        } else if (appBarSuggestions.isNotEmpty() || isInputUrlFocused) {
            focusManager.clearFocus()
            appBarSuggestions = emptyList()
            isInputUrlFocused = false
            inputUrl = currentUrl
        } else if (showSettings) {
            showSettings = false
        } else if (showTabManagement) {
            showTabManagement = false
        } else if (showHistory) {
            showHistory = false
        } else if (showDownloads) {
            showDownloads = false
        } else if (canGoBack) {
            currentTab.webView?.goBack()
        } else {
            currentTab.url.value = ""
            inputUrl = ""
            currentTab.webView?.loadUrl("about:blank")
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = with(androidx.compose.ui.platform.LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val animatedOffsetPx by animateFloatAsState(targetValue = if (isMenuOpen) -screenWidth * 0.65f else 0f, label = "offset")
    val animatedScale by animateFloatAsState(targetValue = if (isMenuOpen) 0.85f else 1f, label = "scale")
    val cornerRadius by animateDpAsState(targetValue = if (isMenuOpen) 32.dp else 0.dp, label = "corner")

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        // Menu Content on the right
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.65f)
                .align(Alignment.CenterEnd)
                .padding(vertical = 48.dp, horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text("Menu", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { currentTab.webView?.goBack(); isMenuOpen = false }, enabled = canGoBack, modifier = Modifier.background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canGoBack) 1f else 0.38f))
                    }
                    IconButton(onClick = { currentTab.webView?.goForward(); isMenuOpen = false }, enabled = canGoForward, modifier = Modifier.background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (canGoForward) 1f else 0.38f))
                    }
                    IconButton(onClick = { isMenuOpen = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)) {
                        Icon(Icons.Default.StarBorder, contentDescription = "Favorite", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { currentTab.webView?.reload(); isMenuOpen = false }, modifier = Modifier.background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("New tab", style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold)) },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.clickable { isMenuOpen = false; tabs.add(TabState()); currentTabIndex = tabs.lastIndex },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("History", style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold)) },
                    leadingContent = { Icon(Icons.Default.History, contentDescription = null) },
                    modifier = Modifier.clickable { isMenuOpen = false; showHistory = true },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Downloads", style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold)) },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    modifier = Modifier.clickable { isMenuOpen = false; showDownloads = true },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Settings", style = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold)) },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                    modifier = Modifier.clickable { isMenuOpen = false; showSettings = true },
                    colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }

        // Main App View
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = animatedOffsetPx
                    scaleX = animatedScale
                    scaleY = animatedScale
                    clip = true
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
                }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    if (!fullScreenMode) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                Column {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            currentTab.url.value = ""
                            inputUrl = ""
                            currentTab.webView?.loadUrl("about:blank")
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Home, contentDescription = "Home", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        if (!isHome) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val displayUrl = if (isInputUrlFocused) inputUrl else inputUrl.replace(Regex("^https?://(www\\.)?"), "")
                            androidx.compose.foundation.text.BasicTextField(
                                value = displayUrl,
                                onValueChange = { inputUrl = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .onFocusChanged { isInputUrlFocused = it.isFocused },
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
                                            focusManager.clearFocus()
                                            val loadUrl = if (android.util.Patterns.WEB_URL.matcher(url).matches() || url.startsWith("http://") || url.startsWith("https://")) {
                                                if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                                            } else {
                                                "https://www.google.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}"
                                            }
                                            currentTab.url.value = loadUrl
                                            currentTab.webView?.visibility = android.view.View.VISIBLE
                                            currentTab.webView?.loadUrl(loadUrl)
                                        }
                                    }
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                                ),
                                decorationBox = { innerTextField ->
                                    androidx.compose.material3.Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                                if (displayUrl.isEmpty()) {
                                                    Text("Search or type URL", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 16.sp)
                                                }
                                                innerTextField()
                                            }
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = { 
                                tabs.add(TabState())
                                currentTabIndex = tabs.lastIndex
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Add, contentDescription = "New Tab", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(22.dp)
                                .border(3.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .clickable { showTabManagement = true }
                        ) {
                            Text("${tabs.size}", style = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = { isMenuOpen = !isMenuOpen }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
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
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            tabs.forEachIndexed { index, tab ->
                val isTabVisible = (index == currentTabIndex) && !isHome
                
                androidx.compose.runtime.key(tab.id) {
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
                            settings.setGeolocationEnabled(true)
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            settings.setSupportMultipleWindows(true)
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            
                            val hostWebView = this
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                        return false
                                    } else {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                            view?.context?.startActivity(intent)
                                            return true
                                        } catch (e: Exception) {
                                            return true
                                        }
                                    }
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    tab.isLoading.value = true
                                    url?.let { 
                                        tab.url.value = it 
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
        
                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    url?.let {
                                        tab.url.value = it
                                        if (it.isNotEmpty() && it != "about:blank") {
                                            if (history.contains(it)) {
                                                history.remove(it)
                                            }
                                            history.add(0, it)
                                        }
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    tab.isLoading.value = false
                                    tab.canGoBack.value = view?.canGoBack() == true
                                    tab.canGoForward.value = view?.canGoForward() == true
                                    view?.title?.let { t -> tab.title.value = t }
                                    url?.let { 
                                        tab.url.value = it 
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
                                        this.layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            setSupportMultipleWindows(true)
                                            javaScriptCanOpenWindowsAutomatically = true
                                            userAgentString = hostWebView.settings.userAgentString
                                        }
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onCloseWindow(window: WebView?) {
                                                window?.let { popupWebViews.remove(it) }
                                            }
                                        }
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                                val url = request?.url?.toString() ?: return false
                                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                                    return false
                                                } else {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                        context.startActivity(intent)
                                                        return true
                                                    } catch (e: Exception) {
                                                        return true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    popupWebViews.add(newWebView)
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = newWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCb: android.webkit.ValueCallback<Array<android.net.Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    filePathCallback = filePathCb
                                    val intent = fileChooserParams?.createIntent()
                                    if (intent != null) {
                                        fileChooserLauncher.launch(intent)
                                        return true
                                    }
                                    return false
                                }

                                override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                                    if (request == null) return
                                    val androidPermissions = mutableListOf<String>()
                                    val alreadyGrantedResources = mutableListOf<String>()
                                    request.resources.forEach { res ->
                                        when (res) {
                                            android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    androidPermissions.add(android.Manifest.permission.CAMERA)
                                                } else {
                                                    alreadyGrantedResources.add(res)
                                                }
                                            }
                                            android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    androidPermissions.add(android.Manifest.permission.RECORD_AUDIO)
                                                } else {
                                                    alreadyGrantedResources.add(res)
                                                }
                                            }
                                            else -> alreadyGrantedResources.add(res)
                                        }
                                    }
                                    if (androidPermissions.isNotEmpty()) {
                                        pendingWebViewPermissionRequest = request
                                        permissionLauncher.launch(androidPermissions.toTypedArray())
                                    } else {
                                        request.grant(alreadyGrantedResources.toTypedArray())
                                    }
                                }

                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?,
                                    callback: android.webkit.GeolocationPermissions.Callback?
                                ) {
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        callback?.invoke(origin, true, false)
                                    } else {
                                        pendingGeoOrigin = origin
                                        pendingGeoCallback = callback
                                        geoPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            }
                            
                            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, contentLength ->
                                val downloadAction = {
                                    try {
                                        if (downloadUrl.startsWith("blob:") || downloadUrl.startsWith("data:")) {
                                            android.widget.Toast.makeText(context, "Cannot download this type of file directly. Try long pressing the link.", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl))
                                            request.setMimeType(mimetype)
                                            val cookies = android.webkit.CookieManager.getInstance().getCookie(downloadUrl)
                                            request.addRequestHeader("cookie", cookies)
                                            request.addRequestHeader("User-Agent", userAgent)
                                            request.setDescription("Downloading file...")
                                            request.setTitle(android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype))
                                            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                            request.setDestinationInExternalPublicDir(
                                                android.os.Environment.DIRECTORY_DOWNLOADS,
                                                android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                                            )
                                            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            dm.enqueue(request)
                                            android.widget.Toast.makeText(context, "Downloading File...", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Download failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }

                                val perms = mutableListOf<String>()
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU && 
                                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q && 
                                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                }
                                
                                if (perms.isNotEmpty()) {
                                    pendingDownloadAction = downloadAction
                                    downloadPermissionLauncher.launch(perms.toTypedArray())
                                } else {
                                    downloadAction()
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
                    }
                )
                }
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
                        currentTab.webView?.visibility = android.view.View.VISIBLE
                        currentTab.webView?.loadUrl(loadUrl)
                    }
                )
            }
            
            if (appBarSuggestions.isNotEmpty()) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(appBarSuggestions.size) { index ->
                            val suggestion = appBarSuggestions[index]
                            androidx.compose.material3.ListItem(
                                headlineContent = { Text(suggestion) },
                                leadingContent = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.clickable {
                                    focusManager.clearFocus()
                                    isInputUrlFocused = false
                                    keyboardController?.hide()
                                    inputUrl = suggestion
                                    val loadUrl = "https://www.google.com/search?q=${java.net.URLEncoder.encode(suggestion, "UTF-8")}"
                                    currentTab.url.value = loadUrl
                                    currentTab.webView?.visibility = android.view.View.VISIBLE
                                    currentTab.webView?.loadUrl(loadUrl)
                                }
                            )
                            if (index < appBarSuggestions.lastIndex) {
                                androidx.compose.material3.HorizontalDivider()
                            }
                        }
                    }
                }
            }
            
            if (showFullScreenButton) {
                var buttonOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                var isDragging by remember { mutableStateOf(false) }
                var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                var isDimmed by remember { mutableStateOf(true) }
                var parentSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                var buttonSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                
                LaunchedEffect(isDragging, lastInteractionTime) {
                    if (!isDragging) {
                        isDimmed = false
                        kotlinx.coroutines.delay(3000)
                        isDimmed = true
                    } else {
                        isDimmed = false
                    }
                }
                
                val animatedAlpha by animateFloatAsState(targetValue = if (isDimmed) 0.5f else 1f, label = "alpha")
                val simulatedOffsetX = if (buttonOffset == androidx.compose.ui.geometry.Offset.Zero && parentSize.width > 0) parentSize.width - buttonSize.width - 48f else buttonOffset.x
                val simulatedOffsetY = if (buttonOffset == androidx.compose.ui.geometry.Offset.Zero && parentSize.height > 0) parentSize.height - buttonSize.height - 48f else buttonOffset.y
                
                val animatedOffsetX by animateFloatAsState(targetValue = if (isDimmed && !isDragging) {
                    if (simulatedOffsetX > parentSize.width / 2f) (parentSize.width - (buttonSize.width / 2.5f)) else -(buttonSize.width * 1.5f / 2.5f)
                } else simulatedOffsetX, label = "offsetX")
                
                val animatedOffsetY by animateFloatAsState(targetValue = simulatedOffsetY, label = "offsetY")
                
                // Invisible full screen tracker to get size
                Box(Modifier.fillMaxSize().onSizeChanged { parentSize = it })
                
                Box(
                    modifier = Modifier
                        .offset { androidx.compose.ui.unit.IntOffset(kotlin.math.round(animatedOffsetX).toInt(), kotlin.math.round(animatedOffsetY).toInt()) }
                        .onSizeChanged { buttonSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isDragging = true; lastInteractionTime = System.currentTimeMillis() },
                                onDragEnd = {
                                    isDragging = false
                                    lastInteractionTime = System.currentTimeMillis()
                                    val safeX = simulatedOffsetX.coerceIn(0f, (parentSize.width - buttonSize.width).toFloat())
                                    val safeY = simulatedOffsetY.coerceIn(0f, (parentSize.height - buttonSize.height).toFloat())
                                    val snapX = if (safeX > (parentSize.width - buttonSize.width) / 2f) (parentSize.width - buttonSize.width).toFloat() else 0f
                                    buttonOffset = androidx.compose.ui.geometry.Offset(snapX, safeY)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (buttonOffset == androidx.compose.ui.geometry.Offset.Zero) {
                                        buttonOffset = androidx.compose.ui.geometry.Offset(simulatedOffsetX, simulatedOffsetY)
                                    }
                                    buttonOffset += dragAmount
                                }
                            )
                        }
                        .graphicsLayer { alpha = animatedAlpha }
                ) {
                    Surface(
                        onClick = { 
                            lastInteractionTime = System.currentTimeMillis()
                            fullScreenMode = !fullScreenMode 
                        },
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (fullScreenMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, 
                                contentDescription = "Toggle Fullscreen", 
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        }

        if (popupWebViews.isNotEmpty()) {
            popupWebViews.forEach { popupWebView ->
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { popupWebViews.remove(popupWebView) },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { popupWebView },
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { popupWebViews.remove(popupWebView) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(MaterialTheme.colorScheme.surface.copy(alpha=0.7f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Popup", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { isMenuOpen = false }
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
            showFullScreenButton = showFullScreenButton,
            onShowFullScreenButtonChange = { 
                onShowFullScreenButtonChange(it)
                if (!it) fullScreenMode = false
            },
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
                if (tabs.size == 1) {
                    tabs.clear()
                    tabs.add(TabState())
                    currentTabIndex = 0
                    showTabManagement = false
                } else {
                    tabs.removeAt(index)
                    if (currentTabIndex >= tabs.size) {
                        currentTabIndex = tabs.size - 1
                    } else if (currentTabIndex > index) {
                        currentTabIndex--
                    }
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

    if (showDownloads) {
        DownloadsScreen(
            onClose = { showDownloads = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var downloadedFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }

    LaunchedEffect(Unit) {
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir.exists() && downloadDir.isDirectory) {
            downloadedFiles = downloadDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (downloadedFiles.isEmpty()) {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.padding(innerPadding).fillMaxSize()
            ) {
                items(downloadedFiles.size) { index ->
                    val file = downloadedFiles[index]
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        supportingContent = { Text("${file.length() / 1024} KB") },
                        leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                            intent.setDataAndType(uri, "*/*")
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Cannot open file", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
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
    showFullScreenButton: Boolean,
    onShowFullScreenButtonChange: (Boolean) -> Unit,
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
            
            ListItem(
                headlineContent = { Text("Show Full Screen Button") },
                supportingContent = { Text("Shows a floating button to quickly toggle full screen") },
                trailingContent = {
                    Switch(
                        checked = showFullScreenButton,
                        onCheckedChange = { onShowFullScreenButtonChange(it) }
                    )
                }
            )
        }
    }
}

data class Shortcut(val title: String, val url: String)

@Composable
fun BrowserHomeScreen(
    onSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp) // shift content drastically up
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
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
        var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        
        LaunchedEffect(inputQuery) {
            if (inputQuery.trim().isNotEmpty()) {
                val results = fetchSuggestions(inputQuery.trim())
                suggestions = results
            } else {
                suggestions = emptyList()
            }
        }
        
        Box(modifier = Modifier.fillMaxWidth(0.9f)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search or type URL") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = if (suggestions.isNotEmpty()) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else CircleShape,
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
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onSearch(inputQuery.trim())
                            inputQuery = ""
                        }
                    })
                )
                
                if (suggestions.isNotEmpty()) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column {
                            suggestions.forEach { suggestion ->
                                androidx.compose.material3.ListItem(
                                    headlineContent = { Text(suggestion) },
                                    leadingContent = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    modifier = Modifier.clickable {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        onSearch(suggestion)
                                        inputQuery = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        var showAddShortcutDialog by remember { mutableStateOf(false) }
        var shortcutTitle by remember { mutableStateOf("") }
        var shortcutUrl by remember { mutableStateOf("") }
        var shortcuts by remember { mutableStateOf(listOf<Shortcut>()) }

        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                shortcuts.forEach { shortcut ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp).clickable { onSearch(shortcut.url) }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                coil.compose.AsyncImage(
                                    model = "https://www.google.com/s2/favicons?sz=64&domain_url=${java.net.URLEncoder.encode(shortcut.url, "UTF-8")}",
                                    contentDescription = shortcut.title,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = shortcut.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).clickable { showAddShortcutDialog = true }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Add shortcut", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showAddShortcutDialog) {
            AlertDialog(
                onDismissRequest = { showAddShortcutDialog = false },
                title = { Text("Add shortcut") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = shortcutTitle,
                            onValueChange = { shortcutTitle = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shortcutUrl,
                            onValueChange = { shortcutUrl = it },
                            label = { Text("URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (shortcutTitle.isNotBlank() && shortcutUrl.isNotBlank()) {
                                val url = if (!shortcutUrl.startsWith("http://") && !shortcutUrl.startsWith("https://")) {
                                    "https://$shortcutUrl"
                                } else {
                                    shortcutUrl
                                }
                                shortcuts = shortcuts + Shortcut(shortcutTitle.trim(), url)
                                showAddShortcutDialog = false
                                shortcutTitle = ""
                                shortcutUrl = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddShortcutDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

suspend fun fetchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    try {
        val url = URL("https://suggestqueries.google.com/complete/search?client=chrome&q=${java.net.URLEncoder.encode(query, "UTF-8")}")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)
        val suggestionsArray = jsonArray.getJSONArray(1)
        val suggestions = mutableListOf<String>()
        val maxSuggestions = minOf(suggestionsArray.length(), 6)
        for (i in 0 until maxSuggestions) {
            suggestions.add(suggestionsArray.getString(i))
        }
        suggestions
    } catch (e: Exception) {
        emptyList()
    }
}