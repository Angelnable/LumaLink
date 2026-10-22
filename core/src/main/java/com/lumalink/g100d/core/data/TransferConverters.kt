package com.lumalink.g100d.core.data

import androidx.room.TypeConverter

class TransferConverters {

    @TypeConverter
    fun fromState(state: TransferState): String = state.name

    @TypeConverter
    fun toState(value: String): TransferState =
        runCatching { TransferState.valueOf(value) }.getOrDefault(TransferState.FAILED)
}
