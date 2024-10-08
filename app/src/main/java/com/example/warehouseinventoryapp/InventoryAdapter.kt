package com.example.warehouseinventoryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InventoryAdapter(private val inventoryList: List<InventoryItem>) :
    RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder>() {

    inner class InventoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewBarcode: TextView = itemView.findViewById(R.id.textViewBarcode)
        val textViewName: TextView = itemView.findViewById(R.id.textViewName)
        val textViewQuantity: TextView = itemView.findViewById(R.id.textViewQuantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InventoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return InventoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: InventoryViewHolder, position: Int) {
        val item = inventoryList[position]
        holder.textViewBarcode.text = item.barcode
        holder.textViewName.text = item.name
        holder.textViewQuantity.text = item.quantity.toString()
    }

    override fun getItemCount(): Int {
        return inventoryList.size
    }
}
