package com.mufcabot.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.mufcabot.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.settings_title)

        if (Prefs.isConfigured(this)) {
            binding.inputServerUrl.setText(Prefs.getServerUrl(this))
            binding.inputUsername.setText(Prefs.getUsername(this))
            binding.inputPassword.setText(Prefs.getPassword(this))
        }

        binding.btnSave.setOnClickListener { onSaveClicked() }
    }

    private fun onSaveClicked() {
        val serverUrl = binding.inputServerUrl.text.toString().trim()
        val username = binding.inputUsername.text.toString().trim()
        val password = binding.inputPassword.text.toString()

        val lower = serverUrl.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            showStatus("Server URL must start with https://", isError = true)
            return
        }
        // 🆕 FIX: the manifest/network config forbid cleartext, so http:// always failed
        // with the cryptic "Cleartext HTTP traffic not permitted". Say so up front.
        if (lower.startsWith("http://")) {
            showStatus("Plain http:// is blocked by the app's security config — use an https:// address", isError = true)
            return
        }

        binding.btnSave.isEnabled = false
        showStatus("Testing connection…", isError = false)

        // testConnection делает блокирующий сетевой вызов — обязательно в фоновом потоке
        Thread {
            val error = ApiClient.testConnection(serverUrl, username, password)
            runOnUiThread {
                // 🆕 FIX: the user may have left the screen while the test was running
                if (isFinishing || isDestroyed) return@runOnUiThread
                binding.btnSave.isEnabled = true
                if (error != null) {
                    showStatus(error, isError = true)
                    return@runOnUiThread
                }

                Prefs.save(this, serverUrl, username, password)
                showStatus("Connected ✓", isError = false)

                // 🆕 FIX: FirebaseMessaging может бросить исключение синхронно, если Firebase
                // не смог инициализироваться (например образ эмулятора без Google Play Services).
                // Push-уведомления — не критичная часть приложения, дашборд должен открыться
                // в любом случае.
                try {
                    FirebaseMessaging.getInstance().token
                        .addOnSuccessListener { token -> ApiClient.registerDevice(applicationContext, token) }
                        .addOnFailureListener { e -> android.util.Log.w("MufcaSettings", "FCM token fetch failed", e) }
                } catch (e: Exception) {
                    android.util.Log.w("MufcaSettings", "Firebase unavailable, push notifications disabled", e)
                }

                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    },
                )
                finish()
            }
        }.start()
    }

    private fun showStatus(text: String, isError: Boolean) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = text
        binding.statusText.setTextColor(
            if (isError) 0xFFF2637A.toInt() else 0xFF45D0A5.toInt()
        )
    }
}
