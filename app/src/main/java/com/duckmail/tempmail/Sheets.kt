package com.duckmail.tempmail

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailSheet(
    message: EmailMessage,
    onClose: () -> Unit,
    onCopy: (String, String) -> Unit,
    onShare: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showHtml by remember { mutableStateOf(message.htmlBody.isNotBlank()) }
    var loadImages by remember { mutableStateOf(false) }
    val otp = message.otp
    val link = message.verificationLink
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(message.subject, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Text("${message.fromName} <${message.fromAddress}>", style = MaterialTheme.typography.bodyMedium)
            Text(relativeTime(message.receivedAtMs), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            if (otp != null) {
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Verification code", style = MaterialTheme.typography.labelLarge)
                        Text(otp, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { onCopy(otp, "Verification code copied") }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Copy code $otp")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (link != null) {
                Button(onClick = { onOpenLink(link) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Open verification link")
                }
                Text(
                    "This link was checked: it contains a verification keyword and a security parameter. It opens in your browser — it is never auto-clicked.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showHtml = true }) { Text("HTML") }
                TextButton(onClick = { showHtml = false }) { Text("Text") }
                Spacer(modifier = Modifier.weight(1f))
                if (showHtml) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Images", style = MaterialTheme.typography.labelLarge)
                        Switch(checked = loadImages, onCheckedChange = { loadImages = it })
                    }
                }
            }
            if (showHtml && message.htmlBody.isNotBlank()) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = false
                            settings.blockNetworkImage = !loadImages
                            settings.loadsImagesAutomatically = loadImages
                            webViewClient = WebViewClient()
                        }
                    },
                    update = { webView ->
                        webView.settings.blockNetworkImage = !loadImages
                        webView.loadDataWithBaseURL(null, message.htmlBody, "text/html", "UTF-8", null)
                    },
                )
            } else {
                Text(message.textBody.ifBlank { message.intro.ifBlank { "(Empty message)" } }, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCopy(message.textBody.ifBlank { message.intro }, "Message copied") }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text("Copy text")
                }
                OutlinedButton(onClick = { onShare("${message.subject}\n\n${message.textBody}") }) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text("Share")
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text("Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSheet(
    mailboxes: List<Mailbox>,
    activeId: String?,
    unreadFor: (String) -> Int,
    onClose: () -> Unit,
    onSwitch: (Mailbox) -> Unit,
    onRename: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Mailboxes", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(12.dp))
            mailboxes.forEach { m ->
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = { onSwitch(m) },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(m.label, style = MaterialTheme.typography.titleMedium)
                        Text(m.address, style = MaterialTheme.typography.bodyMedium)
                        val unread = unreadFor(m.id)
                        Text(
                            if (m.id == activeId) "Active" + if (unread > 0) " · $unread unread" else "" else if (unread > 0) "$unread unread" else "Saved",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRename) { Text("Rename active") }
                OutlinedButton(onClick = onDeleteAll) { Text("Delete all") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAddressSheet(domains: List<String>, onClose: () -> Unit, onCreate: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf(domains.firstOrNull() ?: "duckmail.sbs") }
    var expanded by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Custom address", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = username, onValueChange = { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '.' || c == '_' || c == '-' } }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(value = domain, onValueChange = {}, readOnly = true, label = { Text("Domain") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    domains.forEach { d ->
                        DropdownMenuItem(text = { Text(d) }, onClick = { domain = d; expanded = false })
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(enabled = username.length >= 3, onClick = { onCreate("$username@$domain") }, modifier = Modifier.fillMaxWidth()) {
                Text("Create $username@$domain")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(onClose: () -> Unit, onImport: (String, String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Import mailbox", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(16.dp))
            Button(enabled = address.contains("@") && password.isNotBlank(), onClick = { onImport(address.trim(), password) }, modifier = Modifier.fillMaxWidth()) {
                Text("Import")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: AppSettings, onClose: () -> Unit, onSave: (AppSettings) -> Unit, onDeleteAll: () -> Unit) {
    var s by remember { mutableStateOf(settings) }
    var showApiKey by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Poll interval (seconds): ${s.pollSeconds}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2, 3, 5, 10).forEach { v ->
                    OutlinedButton(onClick = { s = s.copy(pollSeconds = v) }) { Text("$v") }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow("Auto-copy OTP", "Copy a new verification code as soon as it arrives.", s.autoCopyOtp) { s = s.copy(autoCopyOtp = it) }
            SettingRow("Background watching", "Foreground service + notification. WorkManager alone is only every 15 minutes.", s.backgroundWatching) { s = s.copy(backgroundWatching = it) }
            SettingRow("Hide OTP on lock screen", "Notifications show 'New verification code' without the digits.", s.hideOtpOnLockScreen) { s = s.copy(hideOtpOnLockScreen = it) }
            SettingRow("Dynamic color (Material You)", "Use your wallpaper colors on Android 12+.", s.dynamicColor) { s = s.copy(dynamicColor = it) }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { s = s.copy(darkTheme = 0) }) { Text("System") }
                OutlinedButton(onClick = { s = s.copy(darkTheme = 1) }) { Text("Light") }
                OutlinedButton(onClick = { s = s.copy(darkTheme = 2) }) { Text("Dark") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("Language", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { s = s.copy(language = "en") }) { Text("English") }
                OutlinedButton(onClick = { s = s.copy(language = "ar") }) { Text("العربية") }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = if (showApiKey) s.apiKey else "*".repeat(s.apiKey.length.coerceAtMost(12)),
                onValueChange = { if (showApiKey) s = s.copy(apiKey = it) },
                label = { Text("Private API key (dk_…) — optional") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Text(if (showApiKey) "Hide" else "Show")
                    }
                },
            )
            Text("Public domains need no key. The key is stored encrypted on this device only.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "About: TempMail is an UNOFFICIAL client for duckmail.sbs. No ads, no analytics, no tracking. Data stays on this device except calls to api.duckmail.sbs.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onSave(s) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onDeleteAll, modifier = Modifier.fillMaxWidth()) { Text("Delete all mailboxes") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrSheet(address: String, onClose: () -> Unit, onCopy: () -> Unit) {
    val bitmap = remember(address) { qrBitmap(address) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("QR code", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))
            if (bitmap != null) {
                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR code for $address", modifier = Modifier.size(240.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(address, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onCopy) { Text("Copy address") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun qrBitmap(text: String): Bitmap? {
    return try {
        val size = 512
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bmp
    } catch (_: Exception) {
        null
    }
}

@Composable
fun CredentialsDialog(mailbox: Mailbox, onClose: () -> Unit, onCopy: (String, String) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Credentials") },
        text = {
            Column {
                Text("Address: ${mailbox.address}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Password: ${mailbox.password}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Save these — clearing app data without them loses this inbox.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(mailbox.address, "Address copied") }) { Text("Copy address") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Close") }
        },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onClose: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
fun RenameDialog(current: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    var label by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Rename mailbox") },
        text = {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true)
        },
        confirmButton = { TextButton(enabled = label.isNotBlank(), onClick = { onSave(label.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
