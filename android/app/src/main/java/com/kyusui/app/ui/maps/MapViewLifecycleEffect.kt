package com.kyusui.app.ui.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.maps.MapView

/**
 * Meneruskan lifecycle Android ke MapView tepat satu kali untuk setiap transisi.
 *
 * LifecycleRegistry langsung menyinkronkan observer baru ke state LifecycleOwner
 * saat ini. Karena itu kita tidak boleh memanggil onStart()/onResume() lagi secara
 * manual setelah addObserver(), atau MapView bisa menerima transisi ganda.
 *
 * onDestroy() juga dipastikan hanya dipanggil satu kali, termasuk saat Activity
 * dihancurkan tepat ketika composable sedang dilepas.
 */
@Composable
internal fun MapViewLifecycleEffect(mapView: MapView?) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mapView) {
        if (mapView == null) {
            onDispose { }
        } else {
            var started = false
            var resumed = false
            var destroyed = false

            fun pauseIfNeeded() {
                if (resumed && !destroyed) {
                    mapView.onPause()
                    resumed = false
                }
            }

            fun stopIfNeeded() {
                pauseIfNeeded()
                if (started && !destroyed) {
                    mapView.onStop()
                    started = false
                }
            }

            fun destroyIfNeeded() {
                stopIfNeeded()
                if (!destroyed) {
                    mapView.onDestroy()
                    destroyed = true
                }
            }

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        if (!started && !destroyed) {
                            mapView.onStart()
                            started = true
                        }
                    }

                    Lifecycle.Event.ON_RESUME -> {
                        if (!resumed && !destroyed) {
                            if (!started) {
                                mapView.onStart()
                                started = true
                            }
                            mapView.onResume()
                            resumed = true
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> pauseIfNeeded()
                    Lifecycle.Event.ON_STOP -> stopIfNeeded()
                    Lifecycle.Event.ON_DESTROY -> destroyIfNeeded()
                    else -> Unit
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                destroyIfNeeded()
            }
        }
    }
}
