package com.duckmail.tempmail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TempMailApp(
    vm: MailViewModel,
    copyText: (String, String) -> Unit,
    shareText: (String) -> Unit,
    openLink: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var detail by remember { mutableStateOf<EmailMessage?>(null) }
    var showVault by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showCredentials by remember { mutableStateOf(false) }
    var showDeleteMailbox by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    // Auto-copy a newly arrived OTP (toggleable in Settings).
    LaunchedEffect(Unit) {
        vm.newOtp.collect { otp ->
            if (state.settings.autoCopyOtp) {
                copyText(otp, "Verification code copied")
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { scope.launch { snackbar.showSnackbar(it) } }
    }

    val active = state.activeMailbox
    val filtered = vm.filteredMessages()
    val otpCount = state.messages.count { it.otp != null }
    val unread = state.messages.count { !it.isRead }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("TempMail", style = MaterialTheme.typography.headlineLarge) },
                subtitle = {
                    Text(
                        if (active != null) {
                            if (unread > 0) "$unread unread · $otpCount codes" else "Ready · $otpCount codes"
                        } else {
                            "Unofficial DuckMail client"
                        },
                    )
                },
                actions = {
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    SmallFloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                        Icon(if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add, contentDescription = "New")
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; vm.createRandomAddress() },
                    text = { Text("New random address") },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; showCustom = true },
                    text = { Text("Custom address") },
                    icon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                )
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; showImport = true },
                    text = { Text("Import mailbox") },
                    icon = { Icon(Icons.Outlined.Password, contentDescription = null) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                if (active == null) {
                    EmptyHero(
                        isLoading = state.isLoading,
                        onCreate = { vm.createRandomAddress() },
                        onCustom = { showCustom = true },
                        onImport = { showImport = true },
                    )
                } else {
                    AddressHero(
                        mailbox = active,
                        mailboxCount = state.mailboxes.size,
                        liveSeconds = state.settings.pollSeconds,
                        watching = state.settings.backgroundWatching,
                        onCopy = { copyText(active.address, "Address copied") },
                        onQr = { showQr = true },
                        onShare = { shareText(active.address) },
                        onVault = { showVault = true },
                        onNew = { vm.createRandomAddress() },
                        onCredentials = { showCredentials = true },
                        onDelete = { showDeleteMailbox = true },
                    )
                }
            }

            if (searchOpen) {
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { vm.setSearch(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search mail") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { vm.setSearch("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                    )
                }
            }

            if (active != null) {
                item {
                    ButtonGroup(
                        overflowIndicator = {},
                    ) {
                        toggleableItem(
                            checked = state.filter == MailFilter.ALL,
                            label = "All",
                            onCheckedChange = { if (it) vm.setFilter(MailFilter.ALL) },
                            icon = { Icon(Icons.Outlined.Inbox, contentDescription = null) },
                        )
                        toggleableItem(
                            checked = state.filter == MailFilter.UNREAD,
                            label = "Unread",
                            onCheckedChange = { if (it) vm.setFilter(MailFilter.UNREAD) },
                            icon = { Icon(Icons.Outlined.MarkEmailUnread, contentDescription = null) },
                        )
                        toggleableItem(
                            checked = state.filter == MailFilter.CODES,
                            label = "Codes ($otpCount)",
                            onCheckedChange = { if (it) vm.setFilter(MailFilter.CODES) },
                            icon = { Icon(Icons.Outlined.Password, contentDescription = null) },
                        )
                    }
                }

                if (state.isRefreshing) {
                    item {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                if (state.isOffline) {
                    item {
                        Text(
                            "Offline — showing cached mail" +
                                (state.lastUpdatedMs?.let { " · last updated ${relativeTime(it)}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (filtered.isEmpty() && !state.isRefreshing) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("No mail yet", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Your address is ready. New mail appears here automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                SplitButtonLayout(
                                    leadingButton = {
                                        SplitButtonDefaults.LeadingButton(onClick = { copyText(active.address, "Address copied") }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Copy address")
                                        }
                                    },
                                    trailingButton = {
                                        SplitButtonDefaults.TrailingButton(onClick = { showQr = true }) {
                                            Icon(Icons.Filled.QrCode2, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { msg ->
                    MessageRow(
                        message = msg,
                        onOpen = { vm.openMessage(msg) { detail = it } },
                        onCopyOtp = { otp -> copyText(otp, "Verification code copied") },
                        onDelete = { vm.deleteMessage(msg) },
                        onMarkUnread = { vm.markUnread(msg) },
                    )
                }
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }
        }
    }

    if (detail != null) {
        MessageDetailSheet(
            message = detail!!,
            onClose = { detail = null },
            onCopy = { t, m -> copyText(t, m) },
            onShare = shareText,
            onOpenLink = openLink,
            onDelete = { vm.deleteMessage(detail!!); detail = null },
        )
    }
    if (showVault) {
        VaultSheet(
            mailboxes = state.mailboxes,
            activeId = active?.id,
            unreadFor = { vm.unreadCount(it) },
            onClose = { showVault = false },
            onSwitch = { vm.switchMailbox(it); showVault = false },
            onRename = { showVault = false; showRename = true },
            onDeleteAll = { showVault = false; showDeleteAll = true },
        )
    }
    if (showCustom) {
        CustomAddressSheet(
            domains = state.domains,
            onClose = { showCustom = false },
            onCreate = { address -> showCustom = false; vm.createAddress(address) },
        )
    }
    if (showImport) {
        ImportSheet(
            onClose = { showImport = false },
            onImport = { a, p -> showImport = false; vm.importMailbox(a, p) },
        )
    }
    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            onClose = { showSettings = false },
            onSave = { vm.updateSettings(it); showSettings = false },
            onDeleteAll = { showSettings = false; showDeleteAll = true },
        )
    }
    if (showQr && active != null) {
        QrSheet(address = active.address, onClose = { showQr = false }, onCopy = { copyText(active.address, "Address copied") })
    }
    if (showCredentials && active != null) {
        CredentialsDialog(
            mailbox = active,
            onClose = { showCredentials = false },
            onCopy = { t, m -> copyText(t, m) },
        )
    }
    if (showDeleteMailbox && active != null) {
        ConfirmDialog(
            title = "Delete this mailbox?",
            body = "This deletes ${active.address} from the server and from this app. This cannot be undone.",
            confirm = "Delete",
            onClose = { showDeleteMailbox = false },
            onConfirm = { showDeleteMailbox = false; vm.deleteMailbox(active) },
        )
    }
    if (showDeleteAll) {
        ConfirmDialog(
            title = "Delete all mailboxes?",
            body = "This deletes every saved mailbox from the server and from this app.",
            confirm = "Delete all",
            onClose = { showDeleteAll = false },
            onConfirm = { showDeleteAll = false; vm.deleteAllMailboxes() },
        )
    }
    if (showRename && active != null) {
        RenameDialog(
            current = active.label,
            onClose = { showRename = false },
            onSave = { vm.renameMailbox(active, it); showRename = false },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyHero(isLoading: Boolean, onCreate: () -> Unit, onCustom: () -> Unit, onImport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Filled.Inbox, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Get a disposable address", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "One tap, no sign-up. Mail arrives here automatically, and codes are copied in one tap.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(20.dp))
            if (isLoading) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
            } else {
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.LeadingButton(onClick = onCreate) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("New address")
                        }
                    },
                    trailingButton = {
                        SplitButtonDefaults.TrailingButton(onClick = onCustom) {
                            Text("Custom")
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Already have one? Import it.",
                    modifier = Modifier.clickable { onImport() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun AddressHero(
    mailbox: Mailbox,
    mailboxCount: Int,
    liveSeconds: Int,
    watching: Boolean,
    onCopy: () -> Unit,
    onQr: () -> Unit,
    onShare: () -> Unit,
    onVault: () -> Unit,
    onNew: () -> Unit,
    onCredentials: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (watching) "Live ($liveSeconds s) · Watching in background" else "Live ($liveSeconds s)",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.Share, contentDescription = "More actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Share address") }, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text("Show credentials") }, onClick = { menu = false; onCredentials() })
                    DropdownMenuItem(text = { Text("Delete mailbox") }, onClick = { menu = false; onDelete() })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(mailbox.label, style = MaterialTheme.typography.titleMedium)
            Text(
                mailbox.address,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(16.dp))
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy address")
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(onClick = onQr) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null)
                    }
                },
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Text(
                    "Mailboxes ($mailboxCount) · Switch",
                    modifier = Modifier.clickable { onVault() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "New address",
                    modifier = Modifier.clickable { onNew() },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageRow(
    message: EmailMessage,
    onOpen: () -> Unit,
    onCopyOtp: (String) -> Unit,
    onDelete: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(16.dp))
            }
        },
        content = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen() },
                shape = MaterialTheme.shapes.large,
                color = if (message.isRead) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                message.fromName.firstOrNull()?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                message.fromName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(message.fromAddress, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(relativeTime(message.receivedAtMs), style = MaterialTheme.typography.bodyMedium)
                        if (!message.isRead) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(message.subject, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (message.intro.isNotBlank()) {
                        Text(message.intro, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val otp = message.otp
                    if (otp != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { onCopyOtp(otp) },
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(otp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    Row {
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onMarkUnread) {
                            Icon(Icons.Filled.MarkEmailRead, contentDescription = "Mark unread")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete message")
                        }
                    }
                }
            }
        },
    )
}
