
/*
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.os.bundleOf
import com.google.android.material.snackbar.Snackbar
import org.openhab.habdroid.R
import org.openhab.habdroid.util.PrefKeys
import org.openhab.habdroid.util.TaskerIntent
import org.openhab.habdroid.util.TaskerPlugin
import org.openhab.habdroid.util.getPrefs
import org.openhab.habdroid.util.isTaskerPluginEnabled

class TaskerOpenAppActivity : AbstractBaseActivity() {
    lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_tasker_open_app)
        setResult(RESULT_CANCELED)

        val toolbar = findViewById<Toolbar>(R.id.openhab_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView = findViewById(R.id.list)

        val values = arrayOf(
            OpenAppAction(getString(R.string.default_sitemap), ""),
            OpenAppAction(getString(R.string.mainmenu_openhab_oh3_ui), MainActivity.ACTION_OH3_UI_SELECTED),
            OpenAppAction(getString(R.string.mainmenu_openhab_habpanel), MainActivity.ACTION_HABPANEL_SELECTED),
            OpenAppAction(getString(R.string.mainmenu_openhab_frontail), MainActivity.ACTION_FRONTAIL_SELECTED)
        )

        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_single_choice,
            android.R.id.text1,
            values.map { it.name }
        )

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = values[position]
            Log.d(TAG, "Selected ${item.name}")

            val resultBundle = bundleOf(EXTRA_OPEN_APP_ACTION to item.action)
            val resultIntent = Intent().apply {
                putExtra(TaskerIntent.EXTRA_STRING_BLURB, getString(R.string.tasker_open_app_blurb, item.name))
                putExtra(TaskerIntent.EXTRA_BUNDLE, resultBundle)
            }

            if (TaskerPlugin.Setting.hostSupportsSynchronousExecution(intent.extras)) {
                TaskerPlugin.Setting.requestTimeoutMS(resultIntent, TaskerPlugin.Setting.REQUESTED_TIMEOUT_MS_MAX)
            }

            setResult(RESULT_OK, resultIntent)
            finish()
        }

        if (!getPrefs().isTaskerPluginEnabled()) {
            showSnackbar(
                SNACKBAR_TAG_TASKER_PLUGIN_DISABLED,
                R.string.tasker_plugin_disabled,
                Snackbar.LENGTH_INDEFINITE,
                R.string.turn_on
            ) {
                getPrefs().edit {
                    putBoolean(PrefKeys.TASKER_PLUGIN_ENABLED, true)
                }
            }
        }
    }

    companion object {
        private val TAG = TaskerOpenAppActivity::class.java.simpleName
        private const val SNACKBAR_TAG_TASKER_PLUGIN_DISABLED = "tasker_disabled"

        const val EXTRA_OPEN_APP_ACTION = "action"
    }

    data class OpenAppAction(val name: String, val action: String)
}
