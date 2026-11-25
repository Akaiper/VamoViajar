package com.aline.viagens.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector

enum class Category(val id: String, val label: String, val icon: ImageVector) {
    PASSEIO("passeio", "Passeio", Icons.Outlined.Attractions),
    COMIDA("comida", "Comida", Icons.Outlined.Restaurant),
    TRANSPORTE("transporte", "Transporte", Icons.Outlined.DirectionsBus),
    HOSPEDAGEM("hospedagem", "Hospedagem", Icons.Outlined.Hotel),
    COMPRAS("compras", "Compras", Icons.Outlined.ShoppingBag),
    OUTRO("outro", "Outro", Icons.Outlined.MoreHoriz);

    companion object {
        fun from(id: String) = entries.firstOrNull { it.id == id } ?: OUTRO
    }
}
