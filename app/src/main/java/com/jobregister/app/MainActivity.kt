package com.jobregister.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.jobregister.app.ui.App
import com.jobregister.app.util.Notifier
import com.jobregister.app.work.JobWatchWorker

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        requestNotificationPermission()
        // Check the register in the background so updates arrive while the
        // app is closed, not only when someone opens it.
        JobWatchWorker.schedule(this)
        setContent {
            App(viewModel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        // Catch up on jobs raised elsewhere while this app was in the background.
        viewModel.refreshQuietly()
    }
}
