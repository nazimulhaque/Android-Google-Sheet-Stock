package com.dev.googlesheetwarehouse

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dev.googlesheetwarehouse.auth.AuthenticationManager
import com.dev.googlesheetwarehouse.model.PhoneStockInfo
import com.dev.googlesheetwarehouse.scan.adapter.ScannedItemsAdapter
import com.dev.googlesheetwarehouse.sheetsapi.SheetsAPIDataSource
import com.dev.googlesheetwarehouse.sheetsapi.SheetsRepository
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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var signInOptions: GoogleSignInOptions
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleAccountCredential: GoogleAccountCredential

    private lateinit var authenticationManager: AuthenticationManager
    private lateinit var sheetsRepository: SheetsRepository
    private lateinit var sheetsApiDataSource: SheetsAPIDataSource

    private lateinit var readSpreadsheetDisposable: Disposable
    private lateinit var updateSpreadsheetDisposable: Disposable
    private val phones: MutableList<PhoneStockInfo> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Getting recyclerview from xml
        val recyclerView = findViewById<RecyclerView>(R.id.rv_scanned_items)
        // Adding a layoutmanager
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)

        // Get the list of scanned items from intent
        val scannedItemsList = intent.getStringArrayListExtra("scanned_items_list")

        // Create adapter
        val scannedItemsListAdapter = scannedItemsList?.let { ScannedItemsAdapter(it) }

        // Add adapter to recyclerview
        recyclerView.adapter = scannedItemsListAdapter

        // Call Google Sheets API
        initDependencies()
        launchAuthentication(googleSignInClient)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RQ_GOOGLE_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                loginSuccessful()
            } else {
                loginFailed()
            }
        }
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
            .usingOAuth2(this, Arrays.asList(*AuthenticationManager.SCOPES))
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
        // presenter = ReadSpreadsheetPresenter(this, authManager, sheetsRepository)
    }

    private fun launchAuthentication(client: GoogleSignInClient) {
        Log.d("SPREADSHEET_LOG", "LOGIN PROMPT");
        startActivityForResult(client.signInIntent, RQ_GOOGLE_SIGN_IN)
    }

    private fun loginSuccessful() {
        Log.d("SPREADSHEET_LOG", "LOGIN SUCCESSFUL");
        // view.showName(authenticationManager.getLastSignedAccount()?.displayName!!)
        authenticationManager.setUpGoogleAccountCredential()
        startReadingSpreadsheet(spreadsheetId, range)
        // startUpdatingSpreadsheetObservable(spreadSheetIdUpdate, rangeUpdate, valueRangeUpdate)
        // startUpdatingSpreadsheet(spreadSheetIdUpdate, rangeUpdate, valueRangeUpdate)
        // startUpdatingSpreadsheetRange(spreadSheetIdUpdate, batchUpdateRequest)
    }

    private fun loginFailed() {
        Log.d("SPREADSHEET_LOG", "LOGIN FAILED");
    }

    private fun startReadingSpreadsheet(spreadsheetId: String, range: String) {
        phones.clear()
        readSpreadsheetDisposable =
            sheetsRepository.readSpreadSheet(spreadsheetId, range)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnError {
                    Toast.makeText(this, it.message!!, Toast.LENGTH_SHORT).show()
                }
                .subscribe(Consumer {
                    phones.addAll(it)
                    // TODO: Populate list of phone models as well as corresponding value ranges to update Google Sheet
                    phones.forEach { t ->
                        Log.d("SPREADSHEET_LOG", t.phoneModel)
                    }
                })
    }

    private fun startUpdatingSpreadsheet(
        spreadSheetIdUpdate: String,
        rangeUpdate: String,
        valueRangeUpdate: ValueRange
    ) {
        sheetsRepository.updateSpreadsheet(
            spreadSheetIdUpdate,
            rangeUpdate,
            valueRangeUpdate
        )
    }

    private fun startUpdatingSpreadsheetObservable(
        spreadSheetIdUpdate: String,
        rangeUpdate: String,
        valueRangeUpdate: ValueRange
    ) {
        updateSpreadsheetDisposable = sheetsRepository.updateSpreadsheetObservable(
            spreadSheetIdUpdate,
            rangeUpdate,
            valueRangeUpdate
        )
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnError {
                Log.d("SPREADSHEET_LOG", it.message!!)
                // view.showToast("ERROR: " + it.message!!)
            }
            .subscribe(Consumer {
                // view.showToast("UPDATED CELLS: " + it.updatedCells)
                Log.d("SPREADSHEET_LOG", it.updatedCells.toString())
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
                Log.d("SPREADSHEET_LOG", it.message!!)
                // view.showToast("ERROR: " + it.message!!)
            }
            .subscribe(Consumer {
                // view.showToast("UPDATED CELLS COUNT: " + it.totalUpdatedCells.toString())
                Log.d("SPREADSHEET_LOG", it.totalUpdatedCells.toString())
            })
    }

    companion object {
        const val TAG = "ReadSpreadsheetActivity"
        const val RQ_GOOGLE_SIGN_IN = 999

        // Constants to read data
        private const val spreadsheetId = "1QpABUgh5Qydc3TL5uGrh9Plueen7p4RdIx51X1A29wE"
        private const val range = "STOCK!A2:J"

        // Constants to update data
        // md.nazimul.haque@gmail.com
        // private const val spreadSheetIdUpdate = "1QpABUgh5Qydc3TL5uGrh9Plueen7p4RdIx51X1A29wE"
        // noushin.sadia@gmail.com
        private const val spreadSheetIdUpdate = "1WXMxAwBtZSaPFRGigIrk-RN2YVEGgp8ehOo5AF5xKlQ"
        private const val sheetNameUpdate = "Sheet2"
        private const val sheetNameUpdateRange = "Sheet3"
        private const val rangeUpdate = "'$sheetNameUpdate'!A1"
        private val valueRangeUpdate: ValueRange = ValueRange().setValues(
            mutableListOf(
                listOf<Any>("Expenses January"),
                listOf<Any>("books", "30"),
                listOf<Any>("pens", "10"),
                listOf<Any>("Expenses February"),
                listOf<Any>("clothes", "20"),
                listOf<Any>("shoes", "5")
            )
        )

        private val rows = listOf<ValueRange>(
            ValueRange().setRange("'$sheetNameUpdateRange'!A1").setValues(
                listOf(
                    listOf<Any>("Updated A1", "Updated B1", "Updated C1")
                )
            ),
            ValueRange().setRange("'$sheetNameUpdateRange'!B2").setValues(
                listOf(
                    listOf<Any>("Updated B2", "", "Updated D2")
                )
            ),
            ValueRange().setRange("'$sheetNameUpdateRange'!D5").setValues(
                listOf(
                    listOf<Any>("", "", "Updated F5")
                )
            ),
        )

        private val batchUpdateRequest = BatchUpdateValuesRequest()
            .setValueInputOption("RAW")
            .setData(rows)
    }


}