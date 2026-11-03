package com.duckmail.tempmail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* declined = no notifications; inbox still works */ }

    // Held as a field so onResume/onPause can pause foreground polling when
    // the app is not visible (the background service, if enabled, owns
    // background watching separately).
    private val vm: MailViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MailViewModel(applicationContext) as T
                }
            },
        )[MailViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            val state by vm.state.collectAsState()
            TempMailTheme(
                darkThemeSetting = state.settings.darkTheme,
                dynamicColor = state.settings.dynamicColor,
            ) {
                TempMailApp(
                    vm = vm,
                    copyText = { text, message -> copyToClipboard(text, message) },
                    shareText = { text -> shareText(text) },
                    openLink = { url -> openLink(url) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.startPolling()
        vm.refresh(silent = true)
    }

    override fun onPause() {
        super.onPause()
        // Foreground polling stops when the app is not visible. Background
        // watching is owned by MailWatchService / WorkManager, not by this.
        vm.stopPolling()
    }

    private fun copyToClipboard(text: String, message: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("TempMail", text))
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }
}
