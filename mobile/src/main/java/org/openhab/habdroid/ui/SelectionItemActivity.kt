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

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.model.LabeledValue
import org.openhab.habdroid.util.orDefaultIfEmpty
import org.openhab.habdroid.util.parcelable

class SelectionItemActivity : AbstractBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_selection_item)

        val boundItem = intent.extras?.parcelable<Item>(EXTRA_ITEM)
        if (boundItem == null) {
            finish()
            return
        }

        supportActionBar?.title = boundItem.label.orDefaultIfEmpty(getString(R.string.app_name))

        val selectionList = findViewById<RecyclerView>(R.id.activity_content)
        selectionList.layoutManager = LinearLayoutManager(this)
        selectionList.adapter = SelectionAdapter(this, boundItem)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> {
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
    private var itemState = item.state?.asString

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        SelectionViewHolder(inflater, parent, item)

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as SelectionViewHolder).bind(item.options!![position])
    }

    override fun getItemCount(): Int = item.options?.size ?: -1

    class SelectionViewHolder(inflater: LayoutInflater, parent: ViewGroup, val item: Item) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.selection_item, parent, false)) {
        private val radioButton: RadioButton = itemView.findViewById(R.id.radio_button)

        fun bind(option: LabeledValue) {
            val adapter = bindingAdapter as SelectionAdapter?
            radioButton.isChecked = option.value == adapter?.itemState
            radioButton.text = option.label
            radioButton.setOnClickListener {
                val connection = ConnectionFactory.primaryUsableConnection?.connection ?: return@setOnClickListener
                adapter?.itemState = option.value
                adapter?.notifyDataSetChanged()
                connection.httpClient.sendItemCommand(item, option.value)
            }
        }
    }
}
