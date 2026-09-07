package com.mobeen.amrapali

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Simple setup screen. A keyboard app cannot be "opened" the way a normal app can;
 * the user has to (1) enable it under system input-method settings and then
 * (2) pick it as the active keyboard. This screen just gives two big buttons
 * that jump straight to those two system screens so the user doesn't have to
 * hunt through Settings menus.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // The keyboard's mic button needs RECORD_AUDIO. A background IME service
        // cannot ask for a runtime permission itself, so we ask for it here, the
        // one time the user opens this setup screen.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            // Opens the system list of installed keyboards, where the user turns
            // "Amrapali" on (this is the same screen as Settings > System > Languages
            // & input > On-screen keyboard > Manage keyboards).
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.btnSelect).setOnClickListener {
            // Opens the system keyboard-switch picker so the user can select
            // Amrapali as the currently active keyboard.
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
    }
}
