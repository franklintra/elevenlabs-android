package com.elevenlabs.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.core.content.ContextCompat
import com.elevenlabs.example.models.UiState
import com.elevenlabs.example.viewmodels.ConversationViewModel
import com.elevenlabs.models.ConversationStatus
import androidx.appcompat.app.AlertDialog
import android.widget.Toast

/**
 * Simple example app demonstrating ElevenLabs Conversational AI SDK
 *
 * Shows basic connection/disconnection functionality with status display
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: ConversationViewModel by viewModels()

    // UI Components
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button

    // Permission request launcher for RECORD_AUDIO only
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val prefs = getSharedPreferences("elevenlabs_permissions", MODE_PRIVATE)
            prefs.edit().putBoolean("audio_permission_working", true).apply()
            Log.d("MainActivity", "Permission granted and marked as working")

            // Permission granted, start conversation
            val demoConfig = com.elevenlabs.example.models.AppConfiguration.demo()
            viewModel.startConversation(demoConfig, this@MainActivity)
        } else {
            showError("Microphone permission is required for voice conversations")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        statusText = findViewById(R.id.statusText)
        connectButton = findViewById(R.id.connectButton)

        connectButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state == UiState.Connected) {
                endConversation()
            } else if (state == UiState.Idle || state == UiState.Error) {
                startConversation()
            }
        }

        // Single button toggles behavior based on state
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            updateUIForState(state)
        }

        viewModel.sessionStatus.observe(this) { status ->
            updateStatusDisplay(status)
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                // If we get a permission-related error, reset the working flag
                if (it.contains("permission", ignoreCase = true) || it.contains("insufficient", ignoreCase = true)) {
                    Log.d("MainActivity", "Permission error detected, resetting working flag")
                    val prefs = getSharedPreferences("elevenlabs_permissions", MODE_PRIVATE)
                    prefs.edit().putBoolean("audio_permission_working", false).apply()
                }
                showError(it)
                viewModel.clearError()
            }
        }


    }

    private fun updateUIForState(state: UiState) {
        when (state) {
            UiState.Idle -> {
                connectButton.isEnabled = true
                connectButton.text = "Connect"
            }
            UiState.Connecting -> {
                connectButton.isEnabled = false
                connectButton.text = "Connecting..."
            }
            UiState.Connected -> {
                connectButton.isEnabled = true
                connectButton.text = "Disconnect"
            }
            UiState.Disconnecting -> {
                connectButton.isEnabled = false
                connectButton.text = "Disconnecting..."
            }
            UiState.Error -> {
                connectButton.isEnabled = true
                connectButton.text = "Connect"
            }
        }
    }

    private fun updateStatusDisplay(status: ConversationStatus) {
        val statusColor = when (status) {
            ConversationStatus.CONNECTED -> ContextCompat.getColor(this, R.color.status_connected)
            ConversationStatus.CONNECTING -> ContextCompat.getColor(this, R.color.status_connecting)
            ConversationStatus.DISCONNECTED -> ContextCompat.getColor(this, R.color.status_disconnected)
            ConversationStatus.ERROR -> ContextCompat.getColor(this, R.color.status_error)
            ConversationStatus.DISCONNECTING -> ContextCompat.getColor(this, R.color.status_disconnected)
        }

        val statusMessage = when (status) {
            ConversationStatus.CONNECTED -> "Status: Connected to ElevenLabs"
            ConversationStatus.CONNECTING -> "Status: Connecting..."
            ConversationStatus.DISCONNECTED -> "Status: Disconnected"
            ConversationStatus.ERROR -> "Status: Connection Error"
            ConversationStatus.DISCONNECTING -> "Status: Disconnecting..."
        }

        statusText.text = statusMessage
        statusText.setTextColor(statusColor)
    }

    private fun startConversation() {
        // Check permissions before starting conversation
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions()
            return
        }

        // Use demo configuration for simplicity
        val demoConfig = com.elevenlabs.example.models.AppConfiguration.demo()
        viewModel.startConversation(demoConfig, this@MainActivity)
    }

    private fun endConversation() {
        viewModel.endConversation()
    }

            private fun hasRequiredPermissions(): Boolean {
        // WORKAROUND: LiveKit 2.13.0+ bug - always request permission to ensure it works properly
        // Even if permission appears granted, LiveKit may not recognize it due to the bug
        val systemPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        Log.d("MainActivity", "System permission check - hasPermission: $systemPermission, context: $this")

        // For LiveKit 2.13.0+ bug workaround, we need to check if this is a fresh permission grant
        val prefs = getSharedPreferences("elevenlabs_permissions", MODE_PRIVATE)
        val hasWorkingPermission = prefs.getBoolean("audio_permission_working", false)

        Log.d("MainActivity", "Working permission flag: $hasWorkingPermission")

        // If system says we have permission but our flag says it's not working, force re-request
        return systemPermission && hasWorkingPermission
    }

    private fun requestRequiredPermissions() {
        // WORKAROUND: LiveKit 2.13.0+ bug - always re-request permission to ensure it works
        Log.d("MainActivity", "Force requesting permission due to LiveKit 2.13.0+ bug")
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }



    override fun onDestroy() {
        super.onDestroy()
        viewModel.endConversation()
    }
}