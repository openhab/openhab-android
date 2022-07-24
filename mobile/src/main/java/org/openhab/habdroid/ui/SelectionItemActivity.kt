/*
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.util.orDefaultIfEmpty

class SelectionItemActivity : AbstractBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_selection_item)

        val boundItem = intent.extras?.get(EXTRA_ITEM) as Item?
        if (boundItem == null) {
            finish()
            return
        }

        setSupportActionBar(findViewById(R.id.openhab_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = boundItem.label.orDefaultIfEmpty(getString(R.string.app_name))

        val selectionList = findViewById<RecyclerView>(R.id.selection_list)
        selectionList.layoutManager = LinearLayoutManager(this)
        selectionList.adapter = SelectionAdapter(this, boundItem)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val TAG = SelectionItemActivity::class.java.simpleName

        const val EXTRA_ITEM = "item"
    }
}

class SelectionAdapter(context: Context, val item: Item) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val inflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return SelectionViewHolder(inflater, parent, item)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val itemState = item.state?.asString
        (holder as SelectionViewHolder).bind(item.options!![position], itemState)
    }

    override fun getItemCount(): Int {
        return item.options?.size ?: -1
    }

    class SelectionViewHolder(inflater: LayoutInflater, parent: ViewGroup, val item: Item) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.selection_item, parent, false)) {
        private val row: LinearLayout = itemView.findViewById(R.id.row)
        private val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)
        private val commandLabel: TextView = itemView.findViewById(R.id.command_text)

        fun bind(option: LabeledValue, itemState: String?) {
            radioButton.isChecked = option.value == itemState
            commandLabel.text = option.label
            setupOnClickListener(row, option)
            setupOnClickListener(radioButton, option)
            setupOnClickListener(commandLabel, option)
        }

        private fun setupOnClickListener(view: View, option: LabeledValue) {
            view.setOnClickListener {
                val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return@setOnClickListener
                connection.httpClient.sendItemCommand(item, option.value)
            }
        }
    }
}
