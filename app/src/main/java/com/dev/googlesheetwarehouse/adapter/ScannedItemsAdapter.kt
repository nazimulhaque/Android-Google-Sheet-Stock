package com.dev.googlesheetwarehouse.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.dev.googlesheetwarehouse.R
import com.dev.googlesheetwarehouse.model.RecyclerViewItem

class ScannedItemsAdapter(private val scannedItemsList: ArrayList<RecyclerViewItem>) :
    RecyclerView.Adapter<ScannedItemsAdapter.ViewHolder>() {
    var companion = Companion

    // This method is returning the view for each item in the list
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val v =
            LayoutInflater.from(parent.context).inflate(R.layout.scanned_item, parent, false)
        return ViewHolder(v)
    }

    // This method is binding the data on the list
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItems(scannedItemsList[position])
    }

    // This method is giving the size of the list
    override fun getItemCount(): Int {
        return scannedItemsList.size
    }

    // The class is holing the list view
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindItems(item: RecyclerViewItem) {
            val tvScannedItemName = itemView.findViewById(R.id.tv_scanned_item_name) as TextView
            val etCount = itemView.findViewById(R.id.et_count) as TextView
            tvScannedItemName.text = item.scannedItem
            etCount.text = item.count

            etCount.doAfterTextChanged {
                for (x in scannedItemsCountList) {
                    if (x.scannedItem == item.scannedItem) {
                        x.count = etCount.text.toString()
                        // Log.d(TAG, "Item= ${x.scannedItem}; Count=${x.count}")
                        break
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "SCANNED_ITEMS_ADAPTER"
        val scannedItemsCountList: ArrayList<RecyclerViewItem> = ArrayList()
    }
}