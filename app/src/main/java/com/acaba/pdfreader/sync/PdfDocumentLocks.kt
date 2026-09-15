package com.acaba.pdfreader.sync

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** Prevents a PDF from being rendered and rewritten at the same time in this process. */
object PdfDocumentLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun forDocument(documentId: String): Mutex = locks.getOrPut(documentId) { Mutex() }
}
