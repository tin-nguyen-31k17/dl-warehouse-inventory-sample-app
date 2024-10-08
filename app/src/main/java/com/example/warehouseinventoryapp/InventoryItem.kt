package com.example.warehouseinventoryapp

data class InventoryItem(
    val barcode: String,
    val name: String,
    var quantity: Int
)
