package com.lumalink.g100d.transport.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumalink.g100d.core.data.TransferEntity
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.transport.transfer.TransferService
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransferItem(
    val id: String,
    val fileName: String,
    val sizeBytes: Long,
    val bytesDownloaded: Long,
    val speedBytesPerSecond: Long,
    val state: TransferState,
    val errorMessage: String?,
    val localUri: String?,
    val mimeType: String,
    val updatedAt: Long,
) {
    val progress: Float?
        get() = if (sizeBytes > 0L) {
            (bytesDownloaded.toFloat() / sizeBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    val etaSeconds: Long?
        get() = if (speedBytesPerSecond > 0L && sizeBytes > bytesDownloaded) {
            (sizeBytes - bytesDownloaded) / speedBytesPerSecond
        } else {
            null
        }
}

data class TransfersUiState(
    val items: List<TransferItem> = emptyList(),
) {
    val activeCount: Int
        get() = items.count { it.state == TransferState.QUEUED || it.state == TransferState.RUNNING }

    val currentSpeed: Long
        get() = items.filter { it.state == TransferState.RUNNING }.sumOf { it.speedBytesPerSecond }

    val completedToday: Int
        get() {
            val startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            return items.count { it.state == TransferState.COMPLETED && it.updatedAt >= startOfDay }
        }
}

class TransfersViewModel(
    private val repository: TransferRepository,
    private val appContext: Context,
) : ViewModel() {

    val uiState: StateFlow<TransfersUiState> = repository.transfers
        .map { entities -> TransfersUiState(entities.map(TransferEntity::toItem)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransfersUiState(),
        )

    fun cancel(id: String) {
        viewModelScope.launch {
            repository.markCanceled(id)
            TransferService.cancel(appContext, id)
        }
    }

    fun retry(id: String) {
        viewModelScope.launch {
            repository.retry(id)
            TransferService.retry(appContext, id)
        }
    }

    fun delete(id: String, deleteLocalFile: Boolean) {
        viewModelScope.launch { deleteRecord(id, deleteLocalFile) }
    }

    fun deleteMany(ids: List<String>, deleteLocalFile: Boolean) {
        viewModelScope.launch {
            ids.forEach { deleteRecord(it, deleteLocalFile) }
        }
    }

    private suspend fun deleteRecord(id: String, deleteLocalFile: Boolean) {
        val entity = repository.findById(id)
        if (deleteLocalFile) {
            entity?.localUri?.let { deleteLocalUri(it) }
        }
        repository.delete(id)
    }

    private fun deleteLocalUri(value: String) {
        runCatching {
            val uri = Uri.parse(value)
            if (uri.scheme == "file") {
                uri.path?.let { java.io.File(it).delete() }
            } else {
                appContext.contentResolver.delete(uri, null, null)
            }
        }
    }

    fun clearFinished() {
        viewModelScope.launch { repository.clearFinished() }
    }

    companion object {
        fun factory(repository: TransferRepository, context: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TransfersViewModel(repository, context.applicationContext)
                }
            }
    }
}

private fun TransferEntity.toItem(): TransferItem = TransferItem(
    id = id,
    fileName = fileName,
    sizeBytes = sizeBytes,
    bytesDownloaded = bytesDownloaded,
    speedBytesPerSecond = speedBytesPerSecond,
    state = state,
    errorMessage = errorMessage,
    localUri = localUri,
    mimeType = mimeType,
    updatedAt = updatedAt,
)
