package com.dev.googlesheetwarehouse.sheetsapi

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
                PhoneStockInfo(
                    it[0].toString(),
                    it[1].toString(),
                    it[2].toString(),
                    it[4].toString(),
                    it[5].toString(),
                    it[7].toString(),
                    it[8].toString()
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
    ): UpdateValuesResponse {
        val response = sheetsAPI.spreadsheets().values()
            .update(spreadsheetId, spreadsheetRange, requestBody).execute()
        Log.d("SPREADSHEET_LOG", "UPDATING SHEET")
        Log.d("SPREADSHEET_LOG", "UPDATING SHEET RESPONSE: " + response.updatedCells)

        return response
    }

    override fun updateSpreadsheetObservable(
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
        val KEY_ID = "spreadsheetId"
        val KEY_URL = "spreadsheetUrl"
    }
}