package com.dev.googlesheetwarehouse

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ScanningActivity : BaseScannerActivity() {
    private lateinit var tvScanCount: TextView
    private lateinit var btnComplete: Button
    private lateinit var imageButton: ImageButton

    private lateinit var scannedItemsList: ArrayList<String>

    private var gpsLatitude: Double = -91.00
    private var gpsLongitude: Double = -181.00

    private var isEnableGpsDialogShown = false

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

        // mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        tvScanCount = findViewById(R.id.tvScanCount)
        tvScanCount.text = getString(R.string.scan_count_formatted, 0)
        btnComplete = findViewById(R.id.btnComplete)
        imageButton = findViewById(R.id.imageButton)
        scannedItemsList = ArrayList()

        imageButton.setOnClickListener {

            showDialogBox(
                R.string.confirm_upload_image,
                R.string.upload_image,
                R.string.upload
            )
        }

        btnComplete.setOnClickListener {
            if (scannedItemsList.size > 1) {
                Log.d(TAG, "Location coordinates outer: ($gpsLatitude,$gpsLongitude)")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                val currentTime = sdf.format(Calendar.getInstance().time)
                val uploadIntent = Intent(this, UploadActivity::class.java)
                // To pass any data to next activity
                uploadIntent.putExtra("scan_type", intent.getStringExtra("scan_type"))
                uploadIntent.putExtra("user_name", user.getUsername())
                uploadIntent.putExtra("full_name", user.getFullName())
                uploadIntent.putExtra("current_time", currentTime)
                uploadIntent.putStringArrayListExtra(
                    "scanned_items_list",
                    scannedItemsList
                )

                if (mLastLocation != null) {
                    if (gpsLatitude < -90.00 || gpsLatitude > 90.00 || gpsLongitude < -180.00 || gpsLongitude > 180.00) {
                        Toast.makeText(this, "Invalid GPS location", Toast.LENGTH_SHORT).show()
                    } else {
                        // notify user
                        // build alert dialog
                        val dialogBuilder = AlertDialog.Builder(this)
                        dialogBuilder
                            // if the dialog is cancelable
                            .setCancelable(false)
                            // positive button text and action
                            .setPositiveButton(R.string.yes) { _, _ ->
                                // start your next activity
                                uploadIntent.putExtra("gps_location", "$gpsLatitude,$gpsLongitude")
                                startActivity(uploadIntent)
                                finish()
                            }
                            // negative button text and action
                            .setNegativeButton(R.string.no) { dialog, _ ->
                                dialog.dismiss()
                            }
                            // set message of alert dialog
                            .setMessage(R.string.confirm_order_complete)
                        // create dialog box
                        val alert = dialogBuilder.create()
                        // set title for alert dialog box
                        alert.setTitle("Are You Sure?")
                        // show alert dialog
                        alert.show()
                    }
                } else {
                    Toast.makeText(this, "No GPS location information", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Barcode for item not found.", Toast.LENGTH_SHORT).show()
            }
        }

        foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()

    }

    override fun onStart() {
        super.onStart()

        if (hasLocationPermissions(
                this@ScanningActivity,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            val serviceIntent = Intent(this, ForegroundOnlyLocationService::class.java)
            bindService(serviceIntent, foregroundOnlyServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            foregroundOnlyBroadcastReceiver,
            IntentFilter(
                ForegroundOnlyLocationService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST
            )
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            foregroundOnlyBroadcastReceiver
        )
        super.onPause()
    }

    override fun onStop() {
        if (foregroundOnlyLocationServiceBound) {
            unbindService(foregroundOnlyServiceConnection)
            foregroundOnlyLocationServiceBound = false
        }

        super.onStop()
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
                    if (!hasLocationPermissions(
                            this@ScanningActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    ) {
                        requestLocationPermissions()
                    }
                }
                else -> // Permission denied.
                    Toast.makeText(
                        this,
                        "Scanner will not work without permissions granted",
                        Toast.LENGTH_SHORT
                    ).show()
            }
        }

        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE_LOCATION) {
            when {
                grantResults.isEmpty() -> // If user interaction was interrupted, the permission request is cancelled and you
                    // receive empty arrays.
                    Log.i(TAG, "User interaction was cancelled.")
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED -> // Permission granted.
                {
                    // foregroundOnlyLocationService?.subscribeToLocationUpdates()
                    checkGpsAndNetwork()
                    val serviceIntent = Intent(this, ForegroundOnlyLocationService::class.java)
                    bindService(
                        serviceIntent,
                        foregroundOnlyServiceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }
                else -> // Permission denied.
                    // Notify the user
                    showDialogBox(
                        R.string.permission_denied_explanation,
                        R.string.enable_location_permission,
                        R.string.open_app_settings
                    )
            }
        }

    }

    override fun onUploadComplete() {
        tvScanCount.text = getString(R.string.scan_count_formatted, 0)
    }

    fun onItemScanned(itemBarCode: String) {
        if (itemBarCode.length > 24) {
            Toast.makeText(this, "Barcode data too long!", Toast.LENGTH_SHORT).show()
        } else if (!scannedItemsList.contains(itemBarCode)) {
            scannedItemsList.add(itemBarCode)
            if (scannedItemsList.size == 1) {
                showDialogBox(
                    R.string.confirm_rack_scanned,
                    R.string.confirm,
                    R.string.ok_button_confirm
                )
            }
            tvScanCount.text = getString(R.string.scan_count_formatted, scannedItemsList.size - 1)

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
            Log.d("complete_upload", scannedItemsList[scannedItemsList.size - 1])
        } else {
            Toast.makeText(this, "Item already scanned!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onUploadImageSelected(imageBytes: ByteArray, imageName: String) {
        uploadImage(Base64.encodeToString(imageBytes, Base64.DEFAULT), imageName)
        scannedItemsList.add(imageName)
        tvScanCount.text = getString(R.string.scan_count_formatted, scannedItemsList.size - 1)
    }

    private fun checkGpsAndNetwork() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var gpsEnabled = false
        var networkEnabled = false
        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        try {
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        if (!gpsEnabled && !networkEnabled && !isEnableGpsDialogShown) {
            // notify user
            showDialogBox(
                R.string.gps_network_not_enabled,
                R.string.enable_location_service,
                R.string.open_location_settings
            )
            isEnableGpsDialogShown = true
        }
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
                    }
                    R.string.upload -> {
                        dialog.dismiss()
                        val calendar = Calendar.getInstance()
                        val mdFormat = SimpleDateFormat("yyMMddHHmmss")
                        val strDate = mdFormat.format(calendar.time)
                        val fullScannerFragment =
                            supportFragmentManager.findFragmentById(R.id.scanner_fragment) as FullScannerFragment
                        onUploadImageSelected(fullScannerFragment.imageBytes, "image_$strDate")
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

    /**
     * Return the current state of the permissions needed.
     */

    private fun hasLocationPermissions(context: Context, vararg permissions: String): Boolean =
        permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this@ScanningActivity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    }

    private fun startLocationPermissionRequest() {
        ActivityCompat.requestPermissions(
            this@ScanningActivity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQUEST_PERMISSIONS_REQUEST_CODE_LOCATION
        )
    }

    private fun requestLocationPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")

            showDialogBox(
                R.string.permission_rationale,
                R.string.enable_location_permission,
                R.string.open_app_settings
            )

        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            startLocationPermissionRequest()
        }
    }

    private fun uploadImage(imageBytes: String, imageName: String) {
        displayLoader()
        ServiceApi.Factory.getInstance(this)?.uploadImage(imageBytes, imageName)
            ?.enqueue(object : Callback<StatusMessageResponse> {
                override fun onFailure(call: Call<StatusMessageResponse>?, t: Throwable?) {
                    // Toast.makeText(this@ScanningActivity, "Image uploaded", Toast.LENGTH_SHORT).show()
                    pDialog!!.dismiss()
                }

                override fun onResponse(
                    call: Call<StatusMessageResponse>?,
                    response: Response<StatusMessageResponse>?
                ) {
                    pDialog!!.dismiss()
                    // Error Occurred during uploading
                }
            })
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

    /**
     * Receiver for location broadcasts from [ForegroundOnlyLocationService].
     */
    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                ForegroundOnlyLocationService.EXTRA_LOCATION
            )

            if (location != null) {
                mLastLocation = location
                gpsLatitude = location.latitude
                gpsLongitude = location.longitude
                Log.d(TAG, "Location coordinates inner: ($gpsLatitude,$gpsLongitude)")
            }
        }
    }

}