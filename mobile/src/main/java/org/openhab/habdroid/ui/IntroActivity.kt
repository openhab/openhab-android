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

package org.openhab.habdroid.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.openhab.habdroid.R
import org.openhab.habdroid.ui.intro.IntroAuthFragment
import org.openhab.habdroid.ui.intro.IntroDoneFragment
import org.openhab.habdroid.ui.intro.IntroInfoFragment
import org.openhab.habdroid.ui.intro.IntroServerSelectFragment
import org.openhab.habdroid.ui.intro.IntroViewModel
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.applyUserSelectedTheme
import org.openhab.habdroid.util.getConfiguredServerIds
import org.openhab.habdroid.util.getConnectionFactory
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.hasPermissions

/**
 * Introduces openHAB and guides the user through connecting to a server:
 * 1. Some pages about openHAB and the app, while servers are discovered in the background
 * 2. Choose a discovered server or enter an address
 *    and try to connect to it
 * 3. Sign in, if required
 * 4. Done
 */
class IntroActivity : AppCompatActivity() {
    private val viewModel: IntroViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyUserSelectedTheme()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.intro_container)) { v, insets ->
            val i = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            v.updatePadding(left = i.left, top = i.top, right = i.right, bottom = i.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val isRestore = viewModel.restoredServer != null
        if (savedInstanceState == null) {
            Log.d(TAG, if (isRestore) "Show restore intro" else "Show regular intro")
            supportFragmentManager.commit {
                replace(R.id.intro_container, if (isRestore) IntroServerSelectFragment() else IntroInfoFragment())
            }
        }

        if (!isRestore && getPrefs().getConfiguredServerIds().isEmpty()) {
            viewModel.startDiscovery()
        } else {
            Log.d(TAG, "Don't start discovery, because there's already at least one server configured")
        }

        supportFragmentManager.addOnBackStackChangedListener {
            val current = supportFragmentManager.findFragmentById(R.id.intro_container)
            if (current is IntroServerSelectFragment || current is IntroInfoFragment) {
                viewModel.cancelProbe()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        IntroViewModel.Event.Connected -> replaceCurrentStep(IntroDoneFragment(), BACK_STACK_DONE)
                        IntroViewModel.Event.AuthRequired -> {
                            if (supportFragmentManager.findFragmentById(R.id.intro_container) !is IntroAuthFragment) {
                                replaceCurrentStep(IntroAuthFragment(), BACK_STACK_AUTH)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Show dialogs for unknown certificates while connecting to the server
        getConnectionFactory().trustManager.bindDisplayActivity(this)
    }

    override fun onStop() {
        super.onStop()
        getConnectionFactory().trustManager.unbindDisplayActivity(this)
    }

    fun showServerSelection() {
        val fm = supportFragmentManager
        val hasSelectionEntry = (0 until fm.backStackEntryCount)
            .any { fm.getBackStackEntryAt(it).name == BACK_STACK_SELECT }
        when {
            hasSelectionEntry -> fm.popBackStack(BACK_STACK_SELECT, 0)
            fm.findFragmentById(R.id.intro_container) is IntroInfoFragment -> showStep(
                IntroServerSelectFragment(),
                BACK_STACK_SELECT
            )
            // Server selection is the first step, e.g. after restoring a backup
            else -> fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    /**
     * Shows the next step and removes the auth step from the back stack,
     * so that going back leads to the server selection.
     */
    private fun replaceCurrentStep(fragment: Fragment, name: String) {
        val fm = supportFragmentManager
        val hasAuthEntry = (0 until fm.backStackEntryCount).any { fm.getBackStackEntryAt(it).name == BACK_STACK_AUTH }
        if (hasAuthEntry) {
            fm.popBackStack(BACK_STACK_AUTH, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        showStep(fragment, name)
    }

    private fun showStep(fragment: Fragment, name: String) {
        supportFragmentManager.commit {
            setCustomAnimations(
                android.R.animator.fade_in,
                android.R.animator.fade_out,
                android.R.animator.fade_in,
                android.R.animator.fade_out
            )
            replace(R.id.intro_container, fragment)
            addToBackStack(name)
        }
    }

    fun finishWithNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    override fun finish() {
        Log.d(TAG, "finish()")
        getPrefs().edit {
            putBoolean(PrefKeys.FIRST_START, false)
            putBoolean(PrefKeys.RECENTLY_RESTORED, false)
        }
        super.finish()
    }

    companion object {
        private val TAG = IntroActivity::class.java.simpleName
        private const val BACK_STACK_SELECT = "select"
        private const val BACK_STACK_AUTH = "auth"
        private const val BACK_STACK_DONE = "done"
    }
}
