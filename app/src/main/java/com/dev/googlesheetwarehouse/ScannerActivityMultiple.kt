package com.dev.googlesheetwarehouse

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dev.googlesheetwarehouse.adapter.ScannedItemsAdapter
import com.dev.googlesheetwarehouse.api.SheetsAPIDataSource
import com.dev.googlesheetwarehouse.api.SheetsRepository
import com.dev.googlesheetwarehouse.auth.AuthenticationManager
import com.dev.googlesheetwarehouse.model.PhoneStockInfo
import com.dev.googlesheetwarehouse.model.RecyclerViewItem
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.drive.Drive
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.zxing.Result
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import me.dm7.barcodescanner.zxing.ZXingScannerView
import me.dm7.barcodescanner.zxing.ZXingScannerView.ResultHandler
import java.util.*


class ScannerActivityMultiple : ScannerActivityBase(), ResultHandler {
    // Scanner
    private var mScannerView: ZXingScannerView? = null
    private var mFlash = false

    // Views
    private lateinit var rlPleaseScanItems: RelativeLayout
    private lateinit var rlButtons: RelativeLayout
    private lateinit var tvPleaseScanItems: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnMinus: Button
    private lateinit var btnPlus: Button

    // Authentication and Sheets API
    private lateinit var signInOptions: GoogleSignInOptions
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleAccountCredential: GoogleAccountCredential

    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var sheetsRepository: SheetsRepository
    private lateinit var sheetsApiDataSource: SheetsAPIDataSource

    private lateinit var readSpreadsheetDisposable: Disposable
    private lateinit var updateSpreadsheetDisposable: Disposable
    private val phoneModelsList: MutableList<PhoneStockInfo> = mutableListOf()

    private lateinit var scannedItemsList: ArrayList<RecyclerViewItem>
    private lateinit var scannedItemsListAdapter: ScannedItemsAdapter

    private lateinit var batchUpdateRequest: BatchUpdateValuesRequest

    private var dataLoaded = false

    public override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_scanner_multiple)
        setupToolbar()
        initViews()

        initDependencies()

        if (!hasCameraPermission())
            ActivityCompat.requestPermissions(
                this@ScannerActivityMultiple,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA
            )

        val contentFrame = findViewById<View>(R.id.content_frame) as ViewGroup
        mScannerView = ZXingScannerView(this)
        contentFrame.addView(mScannerView)

        // Create an empty list
        scannedItemsList = ArrayList()
        // Adding a layout manager
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        // Create adapter
        scannedItemsListAdapter = ScannedItemsAdapter(scannedItemsList)
        // Add adapter to recyclerview
        recyclerView.adapter = scannedItemsListAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val actionBar = supportActionBar
        actionBar?.setTitle(R.string.scan_items_multiple)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.single).isVisible = true
        menu.findItem(R.id.multiple).isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            (R.id.flash) -> {
                toggleFlash()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    public override fun onResume() {
        super.onResume()
        mScannerView!!.setResultHandler(this)
        // You can optionally set aspect ratio tolerance level
        // that is used in calculating the optimal Camera preview size
        mScannerView!!.setAspectTolerance(0.2f)
        if (dataLoaded) {
            mScannerView!!.startCamera()
        }
        mScannerView!!.flash = mFlash
    }

    public override fun onPause() {
        super.onPause()
        mScannerView!!.stopCamera()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(FLASH_STATE, mFlash)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CAMERA) {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_GOOGLE_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                loginSuccessful()
            } else {
                loginFailed()
            }
        }
    }

    override fun handleResult(rawResult: Result) {
        rlPleaseScanItems.visibility = GONE
        recyclerView.visibility = VISIBLE
        rlButtons.visibility = VISIBLE

        var check = false
        scannedItemsList.forEach { t ->
            if (t.scannedItem == rawResult.text) {
                check = true
            }
        }

        if (check) {
            Toast.makeText(this, "Duplicate item!", Toast.LENGTH_SHORT).show()
        } else {
            playBeepTone()
            scannedItemsListAdapter.companion.scannedItemsCountList.add(
                RecyclerViewItem(
                    rawResult.text,
                    "1"
                )
            )
            scannedItemsList.add(
                RecyclerViewItem(
                    rawResult.text,
                    "1"
                )
            )
            scannedItemsListAdapter.notifyDataSetChanged()
        }
        Handler(Looper.getMainLooper()).postDelayed({
            mScannerView!!.resumeCameraPreview(this)
        }, 1000)
    }

    private fun setRangeAndValue(operator: String) {
        mScannerView!!.stopCameraPreview()
        // Get range and value range for update
        val body = mutableListOf<ValueRange>()
        for (item in scannedItemsListAdapter.companion.scannedItemsCountList) {
            var count = item.count.toIntOrNull()
            if (count == null) count = 0
            var i = 1
            phoneModelsList.forEach { t ->
                // Log.d(TAG, t.phoneModel)
                i++
                when (item.scannedItem) {
                    (t.idClear) -> {
                        // Update list item first
                        t.countClear =
                            if (operator == "+") (t.countClear.toInt() + count).toString() else (t.countClear.toInt() - count).toString()
                        rangeUpdate = "'${sheetName}'!B$i"
                        valueRangeUpdate = ValueRange().setRange(rangeUpdate).setValues(
                            listOf(
                                listOf<Any>(t.countClear)
                            )
                        )
                        body.add(valueRangeUpdate)
                    }
                    t.idMatt -> {
                        t.countMatt =
                            if (operator == "+") (t.countMatt.toInt() + count).toString() else (t.countMatt.toInt() - count).toString()
                        rangeUpdate = "'${sheetName}'!E$i"
                        valueRangeUpdate = ValueRange().setRange(rangeUpdate).setValues(
                            listOf(
                                listOf<Any>(t.countMatt)
                            )
                        )
                        body.add(valueRangeUpdate)
                    }
                    t.idTough -> {
                        t.countTough =
                            if (operator == "+") (t.countTough.toInt() + count).toString() else (t.countTough.toInt() - count).toString()
                        rangeUpdate = "'${sheetName}'!H$i"
                        valueRangeUpdate = ValueRange().setRange(rangeUpdate).setValues(
                            listOf(
                                listOf<Any>(t.countTough)
                            )
                        )
                        body.add(valueRangeUpdate)
                    }
                }
            }
        }
        batchUpdateRequest = BatchUpdateValuesRequest()
            .setValueInputOption("RAW")
            .setData(body)
        startUpdatingSpreadsheetRange(spreadsheetId, batchUpdateRequest)
    }

    private fun initViews() {
        rlPleaseScanItems = findViewById(R.id.rl_please_scan_items)
        rlButtons = findViewById(R.id.rl_buttons)
        tvPleaseScanItems = findViewById(R.id.tv_please_scan_items)
        btnMinus = findViewById(R.id.btn_minus)
        btnPlus = findViewById(R.id.btn_plus)
        recyclerView = findViewById(R.id.rv_scanned_items)

        btnMinus.setOnClickListener { setRangeAndValue("-") }
        btnPlus.setOnClickListener { setRangeAndValue("+") }
    }

    private fun initDependencies() {
        signInOptions =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Scope(SheetsScopes.SPREADSHEETS_READONLY))
                .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
                .requestScopes(Drive.SCOPE_FILE)
                .requestEmail()
                .build()
        googleSignInClient = GoogleSignIn.getClient(this, signInOptions)
        googleAccountCredential = GoogleAccountCredential
            .usingOAuth2(this, listOf(*AuthenticationManager.SCOPES))
            .setBackOff(ExponentialBackOff())
        authenticationManager =
            AuthenticationManager(
                lazyOf(this),
                googleSignInClient,
                googleAccountCredential
            )
        sheetsApiDataSource =
            SheetsAPIDataSource(
                authenticationManager,
                AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance()
            )
        sheetsRepository = SheetsRepository(sheetsApiDataSource)
        launchAuthentication(googleSignInClient)
    }

    private fun launchAuthentication(client: GoogleSignInClient) {
        Log.d(TAG, "LOGIN PROMPT");
        startActivityForResult(client.signInIntent, REQUEST_CODE_GOOGLE_SIGN_IN)
    }

    private fun loginSuccessful() {
        Log.d(TAG, "LOGIN SUCCESSFUL");
        // view.showName(authenticationManager.getLastSignedAccount()?.displayName!!)
        authenticationManager.setUpGoogleAccountCredential()
        startReadingSpreadsheet(spreadsheetId, rangeData)
    }

    private fun loginFailed() {
        Log.d(TAG, "LOGIN FAILED");
    }

    private fun startReadingSpreadsheet(spreadsheetId: String, range: String) {
        phoneModelsList.clear()
        readSpreadsheetDisposable =
            sheetsRepository.readSpreadSheet(spreadsheetId, range)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    Toast.makeText(this, it.message!!, Toast.LENGTH_SHORT).show()
                }
                .subscribe(Consumer {
                    phoneModelsList.addAll(it)
                    dataLoaded = true
                    mScannerView!!.startCamera()
                    tvPleaseScanItems.text = "Please scan items ..."
                })
    }

    private fun startUpdatingSpreadsheetRange(
        spreadSheetIdUpdate: String,
        batchUpdateRequest: BatchUpdateValuesRequest
    ) {
        updateSpreadsheetDisposable = sheetsRepository.updateSpreadsheetRange(
            spreadSheetIdUpdate,
            batchUpdateRequest
        )
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError {
                Log.d(TAG, it.message!!)
            }
            .subscribe(Consumer {
                Log.d(TAG, "UPDATED CELLS COUNT: " + it.totalUpdatedCells.toString())
                scannedItemsListAdapter.companion.scannedItemsCountList.clear()
                scannedItemsList.clear()
                scannedItemsListAdapter.notifyDataSetChanged()
                recyclerView.visibility = GONE
                rlButtons.visibility = GONE
                rlPleaseScanItems.visibility = VISIBLE

                Handler(Looper.getMainLooper()).postDelayed({
                    mScannerView!!.resumeCameraPreview(this)
                }, 1000)

                phoneModelsList.forEach { t ->
                    Log.d(
                        TAG,
                        "countClear=${t.countClear},countClear=${t.countMatt},countClear=${t.countTough}"
                    )
                }

                Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show()
            })
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this@ScannerActivityMultiple,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    }

    private fun toggleFlash() {
        mFlash = !mFlash
        mScannerView!!.flash = mFlash
    }

    private fun playBeepTone() {

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
    }

    companion object {
        private const val TAG = "SCANNER_ACTIVITY_MULTI"
        private const val FLASH_STATE = "FLASH_STATE"
        private const val REQUEST_CODE_CAMERA = 1001
        const val REQUEST_CODE_GOOGLE_SIGN_IN = 999

        // Test Sheet
        // private const val spreadsheetId = "1QpABUgh5Qydc3TL5uGrh9Plueen7p4RdIx51X1A29wE"
        // Main Sheet
        private const val spreadsheetId = "1NvMlBT2f_lnnHNffBsLqYmhaDJJWbvmDR2LLw0OFOUU"
        private const val sheetName = "STOCK"
        private const val rangeData = "STOCK!A2:J"

        private lateinit var scannedItem: String
        private lateinit var rangeUpdate: String
        private lateinit var valueRangeUpdate: ValueRange
    }
}