package com.kyusui.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberPublicLinkOpener(): (String) -> Unit {
    val context = LocalContext.current
    return { path ->
        if (path in setOf("privacy-policy", "terms-and-conditions", "support", "delete-account", "manual-book")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://depot.berkahwater.my.id/$path"))
            try { context.startActivity(intent) }
            catch (_: android.content.ActivityNotFoundException) {
                Toast.makeText(context, "Browser tidak tersedia. Buka depot.berkahwater.my.id secara manual.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
