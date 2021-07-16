package com.dev.googlesheetwarehouse.api

import android.util.Log
import com.dev.googlesheetwarehouse.auth.AuthenticationManager
import com.dev.googlesheetwarehouse.model.PhoneStockInfo
import com.dev.googlesheetwarehouse.model.SpreadsheetInfo
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import io.reactivex.Observable
import io.reactivex.Single

class SheetsAPIDataSource(
    private val authManager: AuthenticationManager,
    private val transport: HttpTransport,
    private val jsonFactory: JsonFactory
) : SheetsDataSource {

    private val sheetsAPI: Sheets
        get() {
            return Sheets.Builder(
                transport,
                jsonFactory,
                authManager.googleAccountCredential
            )
                .setApplicationName("test")
                .build()
        }

    override fun readSpreadSheet(
        spreadsheetId: String,
        spreadsheetRange: String
    ): Single<List<PhoneStockInfo>> {
        return Observable
            .fromCallable {
                val response = sheetsAPI.spreadsheets().values()
                    .get(spreadsheetId, spreadsheetRange)
                    .execute()
                response.getValues()
            }
            .flatMapIterable { it -> it }
            .map {
                Log.d(TAG, it[0].toString())
                PhoneStockInfo(
                    if (it.size > 0) it[0].toString() else "",
                    if (it.size > 1) it[1].toString() else "",
                    if (it.size > 2) it[2].toString() else "",
                    if (it.size > 4) it[4].toString() else "",
                    if (it.size > 5) it[5].toString() else "",
                    if (it.size > 7) it[7].toString() else "",
                    if (it.size > 8) it[8].toString() else "",
                )
            }
            .toList()
    }

    override fun createSpreadsheet(spreadSheet: Spreadsheet): Observable<SpreadsheetInfo> {
        return Observable
            .fromCallable {
                val response =
                    sheetsAPI
                        .spreadsheets()
                        .create(spreadSheet)
                        .execute()
                response
            }
            .map { SpreadsheetInfo(it[KEY_ID] as String, it[KEY_URL] as String) }
    }

    override fun updateSpreadsheet(
        spreadsheetId: String,
        spreadsheetRange: String,
        requestBody: ValueRange
    ): Observable<UpdateValuesResponse> {
        Log.d("SPREADSHEET_LOG", "UPDATING SHEET")
        return Observable.fromCallable {
            val response = sheetsAPI.spreadsheets().values()
                .update(spreadsheetId, spreadsheetRange, requestBody)
                .setValueInputOption("RAW")
                .execute()
            if (response != null) {
                Log.d("SPREADSHEET_LOG", "UPDATED CELLS COUNT: " + response.updatedCells)
            } else {
                Log.d("SPREADSHEET_LOG", "RESPONSE: NULL")
            }
            response
        }
    }

    override fun updateSpreadsheetRange(
        spreadSheetIdUpdate: String,
        batchUpdateRequest: BatchUpdateValuesRequest
    ): Observable<BatchUpdateValuesResponse> {
        Log.d("SPREADSHEET_LOG", "UPDATING SHEET")
        return Observable.fromCallable {
            val response = sheetsAPI.spreadsheets().values()
                .batchUpdate(spreadSheetIdUpdate, batchUpdateRequest).execute()
            if (response != null) {
                Log.d("SPREADSHEET_LOG", "UPDATED CELLS COUNT: " + response.totalUpdatedCells)
            } else {
                Log.d("SPREADSHEET_LOG", "RESPONSE: NULL")
            }
            response
        }
    }

    companion object {
        private const val TAG = "SHEETS_API_DATA_SOURCE"
        val KEY_ID = "spreadsheetId"
        val KEY_URL = "spreadsheetUrl"
    }
}