package com.alsoug.keswa

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.alsoug.keswa.core.platform.AndroidFilePicker
import com.alsoug.keswa.core.platform.IFilePicker
import com.alsoug.keswa.core.sync.SyncScheduler
import kotlinx.coroutines.CompletableDeferred
import org.koin.android.ext.android.inject

/**
 * One activity, hosting Compose and bridging platform contracts (such as file selection)
 * that require an Activity context.
 *
 * Everything the handheld shows is the same `App()` the till shows — which is the point of adding
 * the target at all: a second consumer of `commonMain` proves there were no desktop assumptions
 * hiding in it.
 */
class MainActivity : ComponentActivity() {

    private val filePicker: IFilePicker by inject()
    private val syncScheduler: SyncScheduler by inject()
    private var pendingPickerDeferred: CompletableDeferred<Uri?>? = null

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val deferred = pendingPickerDeferred
        pendingPickerDeferred = null
        deferred?.complete(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncScheduler.start()

        (filePicker as? AndroidFilePicker)?.bindLauncher {
            val deferred = CompletableDeferred<Uri?>()
            pendingPickerDeferred = deferred
            openDocumentLauncher.launch(
                arrayOf(
                    "text/*",
                    "text/comma-separated-values",
                    "text/tab-separated-values",
                    "text/csv",
                    "text/plain",
                    "*/*",
                ),
            )
            deferred.await()
        }

        setContent { App() }
    }

    override fun onDestroy() {
        (filePicker as? AndroidFilePicker)?.bindLauncher(null)
        pendingPickerDeferred?.cancel()
        pendingPickerDeferred = null
        super.onDestroy()
    }
}
