package com.duckmail.tempmail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class MailFilter { ALL, UNREAD, CODES }

data class UiState(
    val mailboxes: List<Mailbox> = emptyList(),
    val activeMailbox: Mailbox? = null,
    val messages: List<EmailMessage> = emptyList(),
    val domains: List<String> = listOf("duckmail.sbs"),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val lastUpdatedMs: Long? = null,
    val isOffline: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val filter: MailFilter = MailFilter.ALL,
    val searchQuery: String = "",
)

class MailViewModel(context: Context) : ViewModel() {

    private val store = SecureStore(context.applicationContext)
    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _newOtp = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val newOtp: SharedFlow<String> = _newOtp.asSharedFlow()

    private val _newMail = MutableSharedFlow<EmailMessage>(extraBufferCapacity = 8)
    val newMail: SharedFlow<EmailMessage> = _newMail.asSharedFlow()

    private var pollJob: Job? = null
    private var knownFingerprints = mutableSetOf<String>()
    private var messagesByMailbox: MutableMap<String, List<EmailMessage>> = mutableMapOf()

    init {
        val loaded = store.loadState()
        messagesByMailbox = loaded.messagesByMailbox.toMutableMap()
        val active = loaded.mailboxes.firstOrNull { it.id == loaded.activeId } ?: loaded.mailboxes.firstOrNull()
        _state.value = UiState(
            mailboxes = loaded.mailboxes,
            activeMailbox = active,
            messages = active?.let { messagesByMailbox[it.id] ?: emptyList() } ?: emptyList(),
            settings = loaded.settings,
        )
        if (active != null) {
            knownFingerprints = messagesByMailbox[active.id]?.map { it.fingerprint() }?.toMutableSet() ?: mutableSetOf()
        }
        refreshDomains()
        if (active != null) {
            refresh(silent = true)
            startPolling()
        }
    }

    private fun api(): DuckMailApi = DuckMailApi(_state.value.settings.apiKey)

    private fun persist() {
        val s = _state.value
        store.saveState(s.mailboxes, messagesByMailbox, s.settings, s.activeMailbox?.id)
    }

    fun updateSettings(newSettings: AppSettings) {
        _state.value = _state.value.copy(settings = newSettings)
        persist()
        restartPolling()
        if (newSettings.backgroundWatching) {
            MailWatchService.start(appContext)
        } else {
            MailWatchService.stop(appContext)
        }
    }

    fun setFilter(f: MailFilter) {
        _state.value = _state.value.copy(filter = f)
    }

    fun setSearch(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun refreshDomains() {
        viewModelScope.launch {
            val domains = api().fetchDomains()
            _state.value = _state.value.copy(domains = domains)
        }
    }

    fun startPolling() {
        pollJob?.cancel()
        val interval = _state.value.settings.pollSeconds.coerceIn(2, 10) * 1000L
        pollJob = viewModelScope.launch {
            while (true) {
                delay(interval)
                refresh(silent = true)
            }
        }
    }

    fun restartPolling() {
        if (_state.value.activeMailbox != null) startPolling() else pollJob?.cancel()
    }

    fun stopPolling() {
        pollJob?.cancel()
    }

    fun refresh(silent: Boolean = false) {
        val mailbox = _state.value.activeMailbox ?: return
        viewModelScope.launch {
            if (!silent) _state.value = _state.value.copy(isRefreshing = true)
            try {
                val fresh = try {
                    api().fetchMessages(mailbox)
                } catch (e: DuckMailException) {
                    if (e.status == 401 || e.status == 404) {
                        // One silent re-login, then one retry — never loop.
                        val relogged = api().login(mailbox.address, mailbox.password, mailbox.label, mailbox.id)
                        val updated = _state.value.mailboxes.map { if (it.id == mailbox.id) relogged else it }
                        _state.value = _state.value.copy(mailboxes = updated, activeMailbox = relogged)
                        persist()
                        api().fetchMessages(relogged)
                    } else throw e
                }
                val oldIds = _state.value.messages.map { it.id }.toSet()
                val newOnes = fresh.filter { it.id !in oldIds }
                messagesByMailbox[mailbox.id] = fresh
                _state.value = _state.value.copy(
                    messages = fresh,
                    isRefreshing = false,
                    isLoading = false,
                    isOffline = false,
                    lastUpdatedMs = System.currentTimeMillis(),
                    error = null,
                )
                persist()
                for (m in newOnes) {
                    _newMail.tryEmit(m)
                    m.otp?.let { _newOtp.tryEmit(it) }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRefreshing = false,
                    isLoading = false,
                    isOffline = true,
                    error = if (silent) _state.value.error else "Could not refresh inbox. Showing cached mail.",
                )
            }
        }
    }

    private fun randomUsername(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder()
        repeat(10) { sb.append(chars.random()) }
        return sb.toString()
    }

    private fun randomPassword(): String = UUID.randomUUID().toString().replace("-", "").take(20)

    fun createRandomAddress(onDone: (String?) -> Unit = {}) {
        val domain = _state.value.domains.firstOrNull() ?: "duckmail.sbs"
        createAddress("${randomUsername()}@$domain", onDone)
    }

    fun createAddress(address: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val mailbox = api().createAccount(address, randomPassword(), "Primary")
                val list = _state.value.mailboxes + mailbox
                messagesByMailbox[mailbox.id] = emptyList()
                _state.value = _state.value.copy(mailboxes = list, activeMailbox = mailbox, messages = emptyList(), isLoading = false)
                persist()
                startPolling()
                refresh(silent = true)
                onDone(null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Could not create address")
                onDone(e.message)
            }
        }
    }

    fun importMailbox(address: String, password: String, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val mailbox = api().login(address, password, "Imported")
                val list = _state.value.mailboxes + mailbox
                messagesByMailbox[mailbox.id] = emptyList()
                _state.value = _state.value.copy(mailboxes = list, activeMailbox = mailbox, messages = emptyList(), isLoading = false)
                persist()
                startPolling()
                refresh(silent = true)
                onDone(null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Login failed")
                onDone(e.message)
            }
        }
    }

    fun switchMailbox(mailbox: Mailbox) {
        _state.value = _state.value.copy(activeMailbox = mailbox, messages = messagesByMailbox[mailbox.id] ?: emptyList())
        persist()
        startPolling()
        refresh(silent = true)
    }

    fun renameMailbox(mailbox: Mailbox, newLabel: String) {
        val updated = mailbox.copy(label = newLabel)
        _state.value = _state.value.copy(
            mailboxes = _state.value.mailboxes.map { if (it.id == mailbox.id) updated else it },
            activeMailbox = if (_state.value.activeMailbox?.id == mailbox.id) updated else _state.value.activeMailbox,
        )
        persist()
    }

    fun deleteMailbox(mailbox: Mailbox) {
        viewModelScope.launch {
            try { api().deleteAccount(mailbox) } catch (_: Exception) {}
            messagesByMailbox.remove(mailbox.id)
            val remaining = _state.value.mailboxes.filter { it.id != mailbox.id }
            val next = remaining.firstOrNull()
            _state.value = _state.value.copy(mailboxes = remaining, activeMailbox = next, messages = next?.let { messagesByMailbox[it.id] ?: emptyList() } ?: emptyList())
            persist()
            if (next != null) {
                startPolling()
                refresh(silent = true)
            } else {
                pollJob?.cancel()
            }
        }
    }

    fun deleteAllMailboxes() {
        viewModelScope.launch {
            val current = _state.value.mailboxes
            for (m in current) {
                try { api().deleteAccount(m) } catch (_: Exception) {}
            }
            messagesByMailbox.clear()
            _state.value = _state.value.copy(mailboxes = emptyList(), activeMailbox = null, messages = emptyList())
            persist()
            pollJob?.cancel()
        }
    }

    fun deleteMessage(message: EmailMessage) {
        val mailbox = _state.value.activeMailbox ?: return
        viewModelScope.launch {
            try { api().deleteMessage(mailbox, message.id) } catch (_: Exception) {}
            val updated = _state.value.messages.filter { it.id != message.id }
            messagesByMailbox[mailbox.id] = updated
            _state.value = _state.value.copy(messages = updated)
            persist()
        }
    }

    fun openMessage(message: EmailMessage, onDetail: (EmailMessage) -> Unit) {
        val mailbox = _state.value.activeMailbox ?: return
        viewModelScope.launch {
            val detailed = api().fetchDetail(mailbox, message)
            val updated = _state.value.messages.map { if (it.id == message.id) detailed else it }
            messagesByMailbox[mailbox.id] = updated
            _state.value = _state.value.copy(messages = updated)
            persist()
            onDetail(detailed)
        }
    }

    fun markUnread(message: EmailMessage) {
        val mailbox = _state.value.activeMailbox ?: return
        val updated = _state.value.messages.map { if (it.id == message.id) it.copy(isRead = false) else it }
        messagesByMailbox[mailbox.id] = updated
        _state.value = _state.value.copy(messages = updated)
        persist()
    }

    fun filteredMessages(): List<EmailMessage> {
        val s = _state.value
        var list = s.messages
        if (s.searchQuery.isNotBlank()) {
            val q = s.searchQuery.lowercase()
            list = list.filter {
                it.subject.lowercase().contains(q) || it.fromName.lowercase().contains(q) ||
                    it.fromAddress.lowercase().contains(q) || it.intro.lowercase().contains(q)
            }
        }
        list = when (s.filter) {
            MailFilter.ALL -> list
            MailFilter.UNREAD -> list.filter { !it.isRead }
            MailFilter.CODES -> list.filter { it.otp != null }
        }
        return list.sortedByDescending { it.receivedAtMs }
    }

    fun unreadCount(mailboxId: String): Int =
        (messagesByMailbox[mailboxId] ?: emptyList()).count { !it.isRead }
}
