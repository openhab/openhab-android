package org.openhab.habdroid.ui

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView

import org.openhab.habdroid.R
import org.openhab.habdroid.core.connection.ConnectionFactory
import org.openhab.habdroid.model.Item
import org.openhab.habdroid.ui.widget.WidgetImageView
import org.openhab.habdroid.util.getIconFormat
import org.openhab.habdroid.util.getPrefs

import java.util.ArrayList
import java.util.Comparator

class ItemPickerAdapter(context: Context, private val itemClickListener: ItemClickListener?) :
        RecyclerView.Adapter<ItemPickerAdapter.ItemViewHolder>(), View.OnClickListener {

    private val filteredItems = ArrayList<Item>()
    private val allItems = ArrayList<Item>()
    private val inflater = LayoutInflater.from(context)
    private val iconFormat = context.getPrefs().getIconFormat()
    private var highlightedPosition = -1

    interface ItemClickListener {
        fun onItemClicked(item: Item)
    }

    fun setItems(items: List<Item>) {
        filteredItems.clear()
        filteredItems.addAll(items)
        filteredItems.sortWith(Comparator { lhs, rhs -> lhs.name.compareTo(rhs.name, ignoreCase = true) })
        allItems.clear()
        allItems.addAll(filteredItems)
        notifyDataSetChanged()
    }

    fun filter(filter: String) {
        filteredItems.clear()
        val searchTerm = filter.toLowerCase()
        allItems.filterTo(filteredItems) { item ->
            searchTerm in item.name.toLowerCase()
                    || searchTerm in item.label?.toLowerCase().orEmpty()
                    || searchTerm in item.type.toString().toLowerCase()
        }
        notifyDataSetChanged()
    }

    fun clear() {
        filteredItems.clear()
        notifyDataSetChanged()
    }

    fun findPositionForName(name: String): Int {
        return filteredItems.indexOfFirst { item -> item.name == name }
    }

    fun highlightItem(position: Int) {
        highlightedPosition = position
        notifyItemChanged(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        return ItemViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(filteredItems[position], iconFormat)
        holder.itemView.setOnClickListener(if (itemClickListener != null) this else null)

        if (position == highlightedPosition) {
            val v = holder.itemView
            v.post {
                if (v.background != null) {
                    val centerX = v.width / 2
                    val centerY = v.height / 2
                    DrawableCompat.setHotspot(v.background, centerX.toFloat(), centerY.toFloat())
                }
                v.isPressed = true
                v.isPressed = false
                highlightedPosition = -1
            }
        }
    }

    override fun getItemCount(): Int {
        return filteredItems.size
    }

    override fun onClick(view: View) {
        val holder = view.tag as ItemViewHolder
        val position = holder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            itemClickListener?.onItemClicked(filteredItems[position])
        }
    }

    class ItemViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
            RecyclerView.ViewHolder(inflater.inflate(R.layout.itempickerlist_item, parent, false)) {
        private val itemNameView: TextView = itemView.findViewById(R.id.itemName)
        private val itemLabelView: TextView = itemView.findViewById(R.id.itemLabel)
        private val iconView: WidgetImageView = itemView.findViewById(R.id.itemIcon)
        private val itemTypeView: TextView = itemView.findViewById(R.id.itemType)

        init {
            itemView.tag = this
        }

        fun bind(item: Item, iconFormat: String) {
            itemNameView.text = item.name
            itemLabelView.text = item.label
            itemTypeView.text = item.type.toString()

            val connection = ConnectionFactory.usableConnectionOrNull
            if (item.category != null && connection != null) {
                val encodedIcon = Uri.encode(item.category)
                val iconUrl = "images/$encodedIcon.$iconFormat"
                val size = iconView.resources.getDimensionPixelSize(R.dimen.notificationlist_icon_size)
                iconView.setImageUrl(connection, iconUrl, size, 2000)
            } else {
                iconView.setImageResource(R.drawable.ic_openhab_appicon_24dp)
            }
        }
    }
}