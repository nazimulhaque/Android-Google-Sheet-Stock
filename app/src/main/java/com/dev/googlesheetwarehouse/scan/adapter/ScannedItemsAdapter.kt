package com.dev.googlesheetwarehouse.scan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dev.googlesheetwarehouse.R

class ScannedItemsAdapter(private val scannedItemsList: ArrayList<String>) :
    RecyclerView.Adapter<ScannedItemsAdapter.ViewHolder>() {

    // This method is returning the view for each item in the list
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.scanned_item, parent, false)
        return ViewHolder(v)
    }

    // This method is binding the data on the list
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItems(position, scannedItemsList[position])
    }

    // This method is giving the size of the list
    override fun getItemCount(): Int {
        return scannedItemsList.size
    }

    // The class is hodling the list view
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindItems(slNo: Int, item: String) {
            val tvSlNo = itemView.findViewById(R.id.tv_sl_no) as TextView
            val tvScannedItem = itemView.findViewById(R.id.tv_scanned_item) as TextView
            val serialNo = slNo + 1
            tvSlNo.text = "$serialNo)"
            tvScannedItem.text = item
        }
    }
}