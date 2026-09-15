package com.acaba.pdfreader

import android.app.Application
import com.acaba.pdfreader.data.AppDatabase
import com.acaba.pdfreader.data.DocumentRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfReaderApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { DocumentRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}
