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

package org.openhab.habdroid.ui.preference

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.MenuItem
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import org.openhab.habdroid.R
import org.openhab.habdroid.background.tiles.AbstractTileService
import org.openhab.habdroid.databinding.ActivityPrefsBinding
import org.openhab.habdroid.ui.AbstractBaseActivity
import org.openhab.habdroid.ui.preference.fragments.AbstractSettingsFragment
import org.openhab.habdroid.ui.preference.fragments.DayDreamFragment
import org.openhab.habdroid.ui.preference.fragments.MainSettingsFragment
import org.openhab.habdroid.ui.preference.fragments.TileOverviewFragment
import org.openhab.habdroid.ui.preference.fragments.TileSettingsFragment
import org.openhab.habdroid.ui.preference.fragments.WidgetSettingsFragment
import org.openhab.habdroid.util.parcelable

/**
 * This is a class to provide preferences activity for application.
 */
class PreferencesActivity : AbstractBaseActivity() {
    private lateinit var resultIntent: Intent

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            resultIntent = Intent()
            val fragment = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    intent.action == TileService.ACTION_QS_TILE_PREFERENCES -> {
                    val tile = intent.parcelable<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
                    val tileId: Int = tile?.className?.let { AbstractTileService.getIdFromClassName(it) } ?: 0
                    if (tileId > 0) {
                        TileSettingsFragment.newInstance(tileId)
                    } else {
                        TileOverviewFragment()
                    }
                }
                intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE -> {
                    val id = intent?.extras?.getInt(
                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID
                    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
                    WidgetSettingsFragment.newInstance(id)
                }
                intent.action == ACTION_DAY_DREAM -> {
                    DayDreamFragment()
                }
                else -> {
                    MainSettingsFragment()
                }
            }
            supportFragmentManager.commit {
                add(R.id.activity_content, fragment)
            }
        } else {
            resultIntent = savedInstanceState.parcelable<Intent>(STATE_KEY_RESULT) ?: Intent()
        }
        setResult(RESULT_OK, resultIntent)
    }

    override fun inflateBinding(): CommonBinding {
        val binding = ActivityPrefsBinding.inflate(layoutInflater)
        return CommonBinding(binding.root, binding.appBar, binding.coordinator, binding.activityContent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_KEY_RESULT, resultIntent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (isFinishing) {
            return true
        }
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun handleThemeChange() {
        addResultFlag(RESULT_EXTRA_THEME_CHANGED)
        recreate()
    }

    fun addResultFlag(name: String) {
        resultIntent.putExtra(name, true)
    }

    fun openSubScreen(subScreenFragment: AbstractSettingsFragment) {
        supportFragmentManager.commit {
            replace(R.id.activity_content, subScreenFragment)
            addToBackStack(null)
        }
    }

    class ConfirmationDialogFragment : DialogFragment() {
        interface Callback {
            fun onConfirmed(tag: String?)
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val args = requireArguments()
            return AlertDialog.Builder(requireContext())
                .setMessage(args.getInt("message"))
                .setPositiveButton(args.getInt("buttontext")) { _, _ ->
                    val callback = parentFragment as Callback? ?: throw IllegalArgumentException()
                    callback.onConfirmed(args.getString("tag"))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }

        companion object {
            fun show(fm: FragmentManager, messageResId: Int, actionButtonTextResId: Int, tag: String) {
                val f = ConfirmationDialogFragment()
                f.arguments = bundleOf(
                    "message" to messageResId,
                    "buttontext" to actionButtonTextResId,
                    "tag" to tag
                )
                f.show(fm, tag)
            }
        }
    }

    class ConfirmLeaveDialogFragment : DialogFragment() {
        interface Callback {
            fun onLeaveAndSave()

            fun onLeaveAndDiscard()
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_server_confirm_leave_title)
            .setMessage(R.string.settings_server_confirm_leave_message)
            .setPositiveButton(R.string.save) { _, _ -> handleDone(true) }
            .setNegativeButton(R.string.discard) { _, _ -> handleDone(false) }
            .setNeutralButton(android.R.string.cancel, null)
            .create()

        private fun handleDone(confirmed: Boolean) {
            val callback = parentFragment as Callback? ?: throw IllegalArgumentException()
            if (confirmed) {
                callback.onLeaveAndSave()
            } else {
                callback.onLeaveAndDiscard()
            }
        }
    }

    companion object {
        const val ACTION_DAY_DREAM = "day_dream"
        const val RESULT_EXTRA_THEME_CHANGED = "theme_changed"
        const val RESULT_EXTRA_SITEMAP_CLEARED = "sitemap_cleared"
        const val RESULT_EXTRA_SITEMAP_DRAWER_CHANGED = "sitemap_drawer_changed"
        const val RESULT_EXTRA_SHOW_ICONS_CHANGED = "show_icons_changed"
        const val START_EXTRA_SERVER_PROPERTIES = "server_properties"
        const val ITEM_UPDATE_WIDGET_ITEM = "item"
        const val ITEM_UPDATE_WIDGET_COMMAND = "state"
        const val ITEM_UPDATE_WIDGET_LABEL = "label"
        const val ITEM_UPDATE_WIDGET_WIDGET_LABEL = "widgetLabel"
        const val ITEM_UPDATE_WIDGET_MAPPED_STATE = "mappedState"
        const val ITEM_UPDATE_WIDGET_ICON = "icon"
        const val ITEM_UPDATE_WIDGET_SHOW_STATE = "show_state"
        private const val STATE_KEY_RESULT = "result"

        internal const val SNACKBAR_TAG_CLIENT_SSL_NOT_SUPPORTED = "clientSslNotSupported"
        internal const val SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_PHONE = "bgTasksPermissionDeclinedPhone"
        internal const val SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_WIFI = "bgTasksPermissionDeclinedWifi"
        internal const val SNACKBAR_TAG_BG_TASKS_PERMISSION_DECLINED_BLUETOOTH = "bgTasksPermissionDeclinedBluetooth"
        internal const val SNACKBAR_TAG_BG_TASKS_MISSING_PERMISSION_LOCATION = "bgTasksMissingPermissionLocation"
        internal const val SNACKBAR_TAG_MISSING_PREFS = "missingPrefs"
    }
}

interface CustomDialogPreference {
    fun createDialog(): DialogFragment
}

data class PushNotificationStatus(val message: String, @DrawableRes val icon: Int, val notifyUser: Boolean)
