package com.shoshiluna.executive.core.external

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalOperationJournalAndroidTest {
    @Test fun encryptedJournalPersistsRecordWithinProcess() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ExternalOperationJournal.initialize(context)
        val key = "instrumented-${System.nanoTime()}"
        ExternalOperationJournal.put(ExternalOperationJournal.Record(key, "ext-1", "mission-1", ExternalOperationJournal.State.EXECUTION_PENDING))
        assertEquals(ExternalOperationJournal.State.EXECUTION_PENDING, ExternalOperationJournal.get(key)?.state)
    }
}
