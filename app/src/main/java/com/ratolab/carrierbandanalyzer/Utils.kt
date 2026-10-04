package com.ratolab.carrierbandanalyzer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * ログファイルを外部アプリ（Googleドライブやメールなど）に共有する共通関数
 */
fun shareLogFile(context: Context, logFile: File) {
    if (!logFile.exists() || logFile.length() == 0L) {
        Toast.makeText(context, "Log file is empty", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            logFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 選択ダイアログのタイトル
        val title = context.getString(R.string.item_export_log)
        context.startActivity(Intent.createChooser(shareIntent, title))

    } catch (e: Exception) {
        Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
/** Export a selected SIM's SQLite records to a temporary CSV, then share normally. */
fun shareDatabaseLog(context: Context, subscriptionId: Int) {
    // Caller may invoke from a click callback; DB work belongs on IO.
    val scope = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope
        ?: kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
        )
    scope.launch {
        try {
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.ratolab.carrierbandanalyzer.data.BandRepository.get(context).exportCsv(subscriptionId)
            }
            shareLogFile(context, file)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
