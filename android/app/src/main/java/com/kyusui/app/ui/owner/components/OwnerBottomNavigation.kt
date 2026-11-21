package com.kyusui.app.ui.owner.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Tab utama area Owner. Route sekunder tidak dimasukkan ke navigasi bawah. */
enum class OwnerMainTab { HOME, ORDERS, FINANCE, PROFILE }

/**
 * Navigasi bawah bersama untuk empat tujuan utama Owner.
 * Komponen ini hanya mengirim event navigasi; pemilihan route tetap menjadi
 * tanggung jawab NavigationHost agar back stack tidak dibuat dari UI.
 */
@Composable
fun OwnerBottomNavigation(
    selectedTab: OwnerMainTab,
    onSelect: (OwnerMainTab) -> Unit
) {
    NavigationBar(containerColor = Color.White) {
        val items = listOf(
            Triple(OwnerMainTab.HOME, "Beranda", Icons.Filled.Home),
            Triple(OwnerMainTab.ORDERS, "Pesanan", Icons.Filled.ReceiptLong),
            Triple(OwnerMainTab.FINANCE, "Finance", Icons.Filled.Wallet),
            Triple(OwnerMainTab.PROFILE, "Profil", Icons.Filled.Person)
        )
        items.forEach { (tab, label, image) ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = image, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF087FA8),
                    selectedTextColor = Color(0xFF087FA8),
                    indicatorColor = Color(0xFFE5F4FA),
                    unselectedIconColor = Color(0xFF718792),
                    unselectedTextColor = Color(0xFF718792)
                )
            )
        }
    }
}
