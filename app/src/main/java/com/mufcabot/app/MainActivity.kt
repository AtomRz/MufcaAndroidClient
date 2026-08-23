package com.mufcabot.app

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
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

        val dashboardHost = try {
            android.net.Uri.parse(Prefs.getServerUrl(this)).host
        } catch (e: Exception) {
            null
        }

        webView.webViewClient = object : WebViewClient() {
            // Держим WebView привязанным к домену дашборда, который сам пользователь
            // указал в настройках. Любая другая ссылка (например, если однажды на
            // странице появится внешний URL) уходит во внешний браузер, а не
            // открывается внутри WebView — так WebView не превращается в универсальный
            // браузер с доступом к JS-мосту mufcaOpenSignal.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val requestedHost = request?.url?.host
                if (dashboardHost != null && requestedHost != null && requestedHost != dashboardHost) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (e: Exception) {
                        android.util.Log.w("MufcaMain", "No app to handle external link", e)
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefresh.isRefreshing = false
                binding.swipeRefresh.isEnabled = true
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
                binding.swipeRefresh.isEnabled = true
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

        // 🆕 FIX: SwipeRefreshLayout оборачивает WebView и перехватывает
        // вертикальные свайпы для pull-to-refresh — это конкурирует с
        // touch-хендлингом графика (lightweight-charts) внутри WebView за один
        // и тот же жест. Результат — свайп по графику то панит его, то
        // случайно триггерит reload страницы, непредсказуемо. ChartPanel.jsx
        // сообщает через этот мост, когда палец реально на графике
        // (touchstart/touchend на контейнере чарта), и мы на это время
        // отключаем SwipeRefreshLayout. В обычном браузере (веб-версия
        // дашборда) window.AndroidChartBridge просто не существует — вызовы
        // там no-op по feature-detection в JS, эта правка ничего не ломает
        // вне Android-клиента.
        webView.addJavascriptInterface(ChartTouchBridge(), "AndroidChartBridge")
    }

    private inner class ChartTouchBridge {
        @JavascriptInterface
        fun setChartTouching(active: Boolean) {
            runOnUiThread { binding.swipeRefresh.isEnabled = !active }
        }
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

    override fun onPause() {
        super.onPause()
        // Safety net: если жест прервался фоном/сворачиванием до touchend, не
        // оставляем pull-to-refresh навсегда заблокированным (см. ChartTouchBridge).
        if (::binding.isInitialized) {
            binding.swipeRefresh.isEnabled = true
        }
    }

    override fun onDestroy() {
        if (::binding.isInitialized) {
            binding.webview.destroy()
        }
        super.onDestroy()
    }
}
