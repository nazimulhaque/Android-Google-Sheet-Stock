package com.dev.googlesheetwarehouse.api

import com.dev.googlesheetwarehouse.model.PhoneStockInfo
import com.dev.googlesheetwarehouse.model.SpreadsheetInfo
import com.google.api.services.sheets.v4.model.*
import io.reactivex.Observable
import io.reactivex.Single

class SheetsRepository(private val sheetsAPIDataSource: SheetsAPIDataSource) {

    fun readSpreadSheet(
        spreadsheetId: String,
        spreadsheetRange: String
    ): Single<List<PhoneStockInfo>> {
        return sheetsAPIDataSource.readSpreadSheet(spreadsheetId, spreadsheetRange)
    }

    fun createSpreadsheet(spreadSheet: Spreadsheet): Observable<SpreadsheetInfo> {
        return sheetsAPIDataSource.createSpreadsheet(spreadSheet)
    }

    fun updateSpreadsheet(
        spreadsheetId: String,
        spreadsheetRange: String,
        requestBody: ValueRange
    ): Observable<UpdateValuesResponse> {
        return sheetsAPIDataSource.updateSpreadsheet(
            spreadsheetId,
            spreadsheetRange,
            requestBody
        )
    }

    fun updateSpreadsheetRange(
        spreadSheetIdUpdate: String,
        batchUpdateRequest: BatchUpdateValuesRequest
    ): Observable<BatchUpdateValuesResponse> {
        return sheetsAPIDataSource.updateSpreadsheetRange(
            spreadSheetIdUpdate,
            batchUpdateRequest,
        )
    }

}