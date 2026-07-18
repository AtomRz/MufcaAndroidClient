package com.mufcabot.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.mufcabot.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var pageLoaded = false

    // tab / ticker / tf — куда перейти внутри дашборда после тапа по push-уведомлению
    private data class DeepLink(val tab: String, val ticker: String?, val tf: String?)
    private var pendingDeepLink: DeepLink? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* результат не критичен — просто лучше UX если разрешит */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Prefs.isConfigured(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        loadDashboard()
        handleIntentExtras(intent)
        ensureFcmTokenRegistered()
        setupBackNavigation()
        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webview
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true // нужен для localStorage — там живёт auth-токен дашборда

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefresh.isRefreshing = false
                pageLoaded = true
                attemptPendingDeepLink()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                binding.swipeRefresh.isRefreshing = false
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                // В debug-сборке разрешаем самоподписанный сертификат (удобно тестировать
                // локально до того, как настроен нормальный TLS через туннель). В release —
                // штатное поведение WebView, самоподписанные сертификаты не принимаются.
                if (BuildConfig.DEBUG) {
                    handler?.proceed()
                } else {
                    super.onReceivedSslError(view, handler, error)
                }
            }
        }

        binding.swipeRefresh.setOnRefreshListener { webView.reload() }
    }

    private fun loadDashboard() {
        binding.swipeRefresh.isRefreshing = true
        binding.webview.loadUrl(Prefs.getServerUrl(this))
    }

    private fun ensureFcmTokenRegistered() {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> ApiClient.registerDevice(applicationContext, token) }
                .addOnFailureListener { e -> android.util.Log.w("MufcaMain", "FCM token fetch failed", e) }
        } catch (e: Exception) {
            android.util.Log.w("MufcaMain", "Firebase unavailable, push notifications disabled", e)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        // Android 13+ (API 33) требует явного runtime-разрешения — без этого запроса
        // push будут доходить (onMessageReceived вызовется), но система молча не
        // покажет уведомление в шторке.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webview.canGoBack()) {
                    binding.webview.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun handleIntentExtras(intent: Intent) {
        val tab = intent.getStringExtra("notif_tab") ?: return
        pendingDeepLink = DeepLink(
            tab = tab,
            ticker = intent.getStringExtra("notif_ticker"),
            tf = intent.getStringExtra("notif_tf"),
        )
        if (pageLoaded) attemptPendingDeepLink()
    }

    /**
     * window.mufcaOpenSignal — маленький глобальный хук в App.jsx (веб-дашборд),
     * переключает вкладку и выбранную пару/tf. Best-effort: если дашборд ещё не
     * прошёл логин (JS-сессия) и функция не определена, вызов молча не сработает —
     * ничего не ломается, просто откроется обычный дашборд без автопрыжка.
     */
    private fun attemptPendingDeepLink() {
        val link = pendingDeepLink ?: return
        pendingDeepLink = null
        val tickerArg = link.ticker?.let { "\"${it.replace("\"", "")}\"" } ?: "null"
        val tfArg = link.tf?.let { "\"${it.replace("\"", "")}\"" } ?: "null"
        val tabArg = "\"${link.tab.replace("\"", "")}\""
        val js = "window.mufcaOpenSignal && window.mufcaOpenSignal($tickerArg, $tfArg, $tabArg);"
        binding.webview.evaluateJavascript(js, null)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reload -> {
                binding.webview.reload()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.webview.destroy()
        }
        super.onDestroy()
    }
}
