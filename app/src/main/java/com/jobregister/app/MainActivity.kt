package com.jobregister.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.jobregister.app.ui.App

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App(viewModel)
        }
    }

    override fun onResume() {
        super.onResume()
        // Catch up on jobs raised elsewhere while this app was in the background.
        viewModel.refreshQuietly()
    }
}
