package com.dev.googlesheetwarehouse.sheetsapi

import com.dev.googlesheetwarehouse.model.PhoneStockInfo
import com.dev.googlesheetwarehouse.model.SpreadsheetInfo
import com.google.api.services.sheets.v4.model.*
import io.reactivex.Observable
import io.reactivex.Single

interface SheetsDataSource {

    fun readSpreadSheet(
        spreadsheetId: String,
        spreadsheetRange: String
    ): Single<List<PhoneStockInfo>>

    fun createSpreadsheet(spreadSheet: Spreadsheet): Observable<SpreadsheetInfo>

    fun updateSpreadsheet(
        spreadsheetId: String,
        spreadsheetRange: String,
        requestBody: ValueRange
    ): Observable<UpdateValuesResponse>

    fun updateSpreadsheetRange(
        spreadSheetIdUpdate: String,
        batchUpdateRequest: BatchUpdateValuesRequest
    ): Observable<BatchUpdateValuesResponse>
}