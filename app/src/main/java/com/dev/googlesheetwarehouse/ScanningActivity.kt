package com.dev.googlesheetwarehouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dev.googlesheetwarehouse.scan.BaseScannerActivity
import com.dev.googlesheetwarehouse.scan.fragment.FullScannerFragment
import java.util.*
import kotlin.collections.ArrayList

class ScanningActivity : BaseScannerActivity() {
    private lateinit var tvScanCount: TextView
    private lateinit var btnComplete: Button

    private lateinit var scannedItemsList: ArrayList<String>

    private var pDialog: ProgressDialog? = null

    companion object {
        private const val TAG = "ScanningActivityTag"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE_CAMERA = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_scanner_fragment)
        setupToolbar()

        if (!hasCameraPermission())
            ActivityCompat.requestPermissions(
                this@ScanningActivity,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_PERMISSIONS_REQUEST_CODE_CAMERA
            )

        tvScanCount = findViewById(R.id.tv_scan_count)
        tvScanCount.text = getString(R.string.scan_count_formatted, 0)
        btnComplete = findViewById(R.id.btn_complete)
        scannedItemsList = ArrayList()

        btnComplete.setOnClickListener {
            if (scannedItemsList.size > 0) {
                val submitIntent = Intent(this@ScanningActivity, MainActivity::class.java)
                submitIntent.putStringArrayListExtra(
                    "scanned_items_list",
                    scannedItemsList
                )
                startActivity(submitIntent)
            } else {
                Toast.makeText(this, "No items scanned.", Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val actionBar = supportActionBar
        actionBar?.setTitle(R.string.scan_items)
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(false) // disable the button
            actionBar.setDisplayHomeAsUpEnabled(false) // remove the left caret
            actionBar.setDisplayShowHomeEnabled(false) // remove the icon
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onBackPressed() {
        showDialogBox(R.string.dialog_text_erase, R.string.dialog_title_sure, R.string.ok_button)
    }

    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE_CAMERA) {
            when {
                grantResults.isEmpty() -> // If user interaction was interrupted, the permission request is cancelled and you
                    // receive empty arrays.
                    Log.i(TAG, "User interaction was cancelled.")
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED -> // Permission granted.
                {
                    // cameraSource.start(svScanner.holder)
                    Log.i(TAG, "Permission granted.")
                }
                else -> // Permission denied.
                    Toast.makeText(
                        this,
                        "Scanner will not work without permissions granted.",
                        Toast.LENGTH_SHORT
                    ).show()
            }
        }
    }

    fun onItemScanned(itemBarCode: String) {
        scannedItemsList.add(itemBarCode)
        tvScanCount.text = getString(R.string.scan_count_formatted, scannedItemsList.size)

        // Get full fragment reference
        val fullScannerFragment =
            supportFragmentManager.findFragmentById(R.id.scanner_fragment) as FullScannerFragment

        // Play beep tone
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 300)
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_PIP, 300)

        // Vibrate for 500 milliseconds
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(
                VibrationEffect.createOneShot(
                    500,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            //deprecated in API 26
            v.vibrate(500)
        }
        Handler().postDelayed({
            fullScannerFragment.resumeCameraPreview()
        }, 1000)
    }

    private fun showDialogBox(
        mainTextStringId: Int,
        titleTextStringId: Int,
        positiveButtonTextStringId: Int
    ) {
        // build alert dialog
        val dialogBuilder = AlertDialog.Builder(this)
        dialogBuilder
            // if the dialog is cancelable
            .setCancelable(false)
            // positive button text and action
            .setPositiveButton(positiveButtonTextStringId) { dialog, _ ->
                when (positiveButtonTextStringId) {
                    R.string.open_location_settings -> {
                        dialog.dismiss()
                        // Display the location settings dialog.
                        this.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    R.string.open_app_settings -> {
                        dialog.dismiss()
                        /*
                            // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID, null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                            */
                    }
                    R.string.upload -> {
                        dialog.dismiss()
                        /*
                        val calendar = Calendar.getInstance()
                        val mdFormat = SimpleDateFormat("yyMMddHHmmss")
                        val strDate = mdFormat.format(calendar.time)
                        val fullScannerFragment =
                            supportFragmentManager.findFragmentById(R.id.scanner_fragment) as FullScannerFragment
                        onUploadImageSelected(fullScannerFragment.imageBytes, "image_$strDate")
                        */
                    }
                    R.string.ok_button -> {
                        dialog.dismiss()
                        finish()
                    }
                    R.string.ok_button_confirm -> {
                        dialog.dismiss()
                    }
                }
            }
            // negative button text and action
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                when (positiveButtonTextStringId) {
                    R.string.ok_button_confirm -> {
                        scannedItemsList.clear()
                    }
                }
                dialog.dismiss()
            }
            // set message of alert dialog
            .setMessage(mainTextStringId)
        // create dialog box
        val alert = dialogBuilder.create()
        // set title for alert dialog box
        alert.setTitle(titleTextStringId)
        // show alert dialog
        alert.show()

        // Toast.makeText(this@ScanningActivity, getString(mainTextStringId), Toast.LENGTH_LONG).show()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this@ScanningActivity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    }

    /**
     * Display Progress bar while uploading image
     */

    private fun displayLoader() {
        pDialog = ProgressDialog(this@ScanningActivity)
        pDialog!!.setMessage("Uploading image.. Please wait...")
        pDialog!!.isIndeterminate = false
        pDialog!!.setCancelable(true)
        pDialog!!.show()

    }

}