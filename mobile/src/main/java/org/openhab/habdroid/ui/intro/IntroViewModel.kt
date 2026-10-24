/*
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.habdroid.ui.intro

import android.app.Application
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.UnknownHostException
import javax.jmdns.ServiceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.model.ServerConfiguration
import org.openhab.habdroid.model.ServerPath
import org.openhab.habdroid.util.AsyncServiceResolver
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.ServerUrlCandidates
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getHumanReadableErrorMessage
import org.openhab.habdroid.util.getNextAvailableServerId
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.getPrimaryServerId
import org.openhab.habdroid.util.getSecretPrefs
import org.openhab.habdroid.util.hasCause

class IntroViewModel(application: Application) : AndroidViewModel(application) {
    data class DiscoveredServer(val name: String, val host: String, val urls: List<String>)

    sealed class DiscoveryState {
        data object NotStarted : DiscoveryState()
        data object Running : DiscoveryState()
        data class Finished(val servers: List<DiscoveredServer>) : DiscoveryState()
    }

    enum class AttemptStatus { PENDING, RUNNING, SUCCESS, FAILED, SKIPPED }

    data class Attempt(val url: String, val status: AttemptStatus, val message: CharSequence? = null)

    data class ProbeState(
        val attempts: List<Attempt> = emptyList(),
        val isRunning: Boolean = false,
        val result: ServerProber.Result? = null,
        val usedCredentials: Boolean = false
    ) {
        val credentialsRejected get() = !isRunning && usedCredentials && result is ServerProber.Result.AuthRequired
    }

    sealed class Event {
        data object Connected : Event()
        data object AuthRequired : Event()
    }

    private val context get() = getApplication<Application>()
    private val prober = ServerProber(context.getConnectionFactory())

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.NotStarted)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val _probeState = MutableStateFlow(ProbeState())
    val probeState: StateFlow<ProbeState> = _probeState.asStateFlow()

    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var probeJob: Job? = null
    private var candidates: List<String> = emptyList()

    /** Name of the server, e.g. from mDNS */
    var serverName: String? = null
        private set

    /** Whether the server is set up via myopenHAB */
    var isMyOpenHab = false
        private set

    var connectedUrl: String? = null
        private set
    var connectedVersion: String? = null
        private set
    private var username: String? = null
    private var password: String? = null

    /** Server configuration restored from a backup, which lacks the credentials */
    val restoredServer: ServerConfiguration? = if (context.getPrefs().getBoolean(PrefKeys.RECENTLY_RESTORED, false)) {
        ServerConfiguration.load(context.getPrefs(), context.getSecretPrefs(), context.getPrefs().getPrimaryServerId())
    } else {
        null
    }

    fun startDiscovery() {
        if (_discoveryState.value != DiscoveryState.NotStarted) {
            return
        }
        _discoveryState.value = DiscoveryState.Running
        viewModelScope.launch {
            val resolver = AsyncServiceResolver(
                context,
                listOf(AsyncServiceResolver.OPENHAB_SERVICE_TYPE, AsyncServiceResolver.OPENHAB_SERVICE_TYPE_HTTP),
                this
            )
            val servers = resolver.resolveAll().toDiscoveredServers()
            Log.d(TAG, "Discovery finished, found ${servers.size} server(s)")
            _discoveryState.value = DiscoveryState.Finished(servers)
        }
    }

    fun connectToDiscoveredServer(server: DiscoveredServer) {
        serverName = server.name
        isMyOpenHab = false
        startProbe(server.urls)
    }

    /**
     * @return false if the input isn't a valid address
     */
    fun connectToAddress(address: String): Boolean {
        val urls = ServerUrlCandidates.fromUserInput(address)
        if (urls.isEmpty()) {
            return false
        }
        serverName = null
        isMyOpenHab = urls.size == 1 && urls[0].contains(MY_OPENHAB_REGEX)
        startProbe(urls)
        return true
    }

    fun connectToMyOpenHab() {
        serverName = context.getString(R.string.intro_myopenhab)
        isMyOpenHab = true
        startProbe(listOf(MY_OPENHAB_URL))
    }

    fun cancelProbe() {
        probeJob?.cancel()
        _probeState.update { it.copy(isRunning = false) }
    }

    fun retry() {
        startProbe(candidates, username, password)
    }

    fun signIn(username: String, password: String?) {
        val url = connectedUrl ?: return
        startProbe(listOf(url), username, password)
    }

    private fun startProbe(urls: List<String>, username: String? = null, password: String? = null) {
        candidates = urls
        this.username = username
        this.password = password
        if (username == null) {
            connectedUrl = null
            connectedVersion = null
        }

        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            val attempts = urls.map { Attempt(it, AttemptStatus.PENDING) }.toMutableList()
            val usedCredentials = username != null
            _probeState.value = ProbeState(attempts.toList(), isRunning = true, usedCredentials = usedCredentials)

            var lastResult: ServerProber.Result? = null
            for ((index, url) in urls.withIndex()) {
                attempts[index] = attempts[index].copy(status = AttemptStatus.RUNNING)
                _probeState.update { it.copy(attempts = attempts.toList()) }

                val result = prober.probe(url, username, password)
                lastResult = result
                Log.d(TAG, "Probe result for $url: $result")

                when (result) {
                    is ServerProber.Result.Reachable -> {
                        attempts[index] = attempts[index].copy(status = AttemptStatus.SUCCESS)
                        connectedUrl = url
                        connectedVersion = result.version
                        _probeState.value = ProbeState(attempts.toList(), false, result, usedCredentials)
                        eventChannel.send(Event.Connected)
                        return@launch
                    }

                    is ServerProber.Result.AuthRequired -> {
                        val message = context.getString(R.string.intro_attempt_auth_required)
                        attempts[index] = attempts[index].copy(status = AttemptStatus.SUCCESS, message = message)
                        connectedUrl = url
                        connectedVersion = result.version ?: connectedVersion
                        _probeState.value = ProbeState(attempts.toList(), false, result, usedCredentials)
                        eventChannel.send(Event.AuthRequired)
                        return@launch
                    }

                    is ServerProber.Result.NotOpenHab -> {
                        val message = context.getString(R.string.intro_attempt_not_openhab)
                        attempts[index] = attempts[index].copy(status = AttemptStatus.FAILED, message = message)
                    }

                    is ServerProber.Result.Failed -> {
                        val message = context.getHumanReadableErrorMessage(url, result.statusCode, result.error, true)
                        attempts[index] = attempts[index].copy(status = AttemptStatus.FAILED, message = message)
                        if (result.error.hasCause(UnknownHostException::class.java)) {
                            // All remaining candidates use the same host, so there's no point in trying them
                            for (i in index + 1 until attempts.size) {
                                attempts[i] = attempts[i].copy(status = AttemptStatus.SKIPPED)
                            }
                            break
                        }
                    }
                }
                _probeState.update { it.copy(attempts = attempts.toList()) }
            }

            _probeState.value = ProbeState(attempts.toList(), false, lastResult, usedCredentials)
        }
    }

    fun saveServer(name: String) {
        val url = connectedUrl ?: return
        val path = ServerPath(url, username, password)
        val prefs = context.getPrefs()
        val restored = restoredServer
        val config = if (restored != null) {
            if (restored.remotePath?.url == url) {
                restored.copy(name = name, remotePath = path)
            } else {
                restored.copy(name = name, localPath = path)
            }
        } else {
            ServerConfiguration(
                id = prefs.getNextAvailableServerId(),
                name = name,
                localPath = if (isMyOpenHab) null else path,
                remotePath = if (isMyOpenHab) path else null,
                sslClientCert = null,
                defaultSitemap = null,
                wifiSsids = null,
                restrictToWifiSsids = false,
                frontailUrl = null,
                mainUiStartPage = null
            )
        }
        config.saveToPrefs(prefs, context.getSecretPrefs())
        prefs.edit { putBoolean(PrefKeys.DEMO_MODE, false) }
    }

    fun enableDemoMode() {
        context.getPrefs().edit { putBoolean(PrefKeys.DEMO_MODE, true) }
    }

    private fun List<ServiceInfo>.toDiscoveredServers(): List<DiscoveredServer> {
        data class Entry(val name: String, var httpsPort: Int? = null, var httpPort: Int? = null)

        val entries = linkedMapOf<String, Entry>()
        forEach { info ->
            val host = info.inet4Addresses.firstOrNull()?.hostAddress
                ?: info.hostAddresses.firstOrNull()
                ?: return@forEach
            val entry = entries.getOrPut(host) { Entry(info.name.removeSuffix("-ssl")) }
            if (info.type.startsWith("_openhab-server-ssl.")) {
                entry.httpsPort = info.port
            } else {
                entry.httpPort = info.port
            }
        }
        return entries.map { (host, entry) ->
            DiscoveredServer(entry.name, host, ServerUrlCandidates.fromDiscovery(host, entry.httpsPort, entry.httpPort))
        }
    }

    companion object {
        private val TAG = IntroViewModel::class.java.simpleName
        private const val MY_OPENHAB_URL = "https://home.myopenhab.org/"
        private val MY_OPENHAB_REGEX = "^https://(home\\.)?myopenhab\\.org/".toRegex()
    }
}
