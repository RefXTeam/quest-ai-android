package com.chroniclequest.data.local

import androidx.room.TypeConverter
import com.chroniclequest.domain.model.QuestState
import com.chroniclequest.domain.model.VerificationMethod

/** Persists the domain enums as their stable string names. */
class Converters {
    @TypeConverter
    fun fromVerificationMethod(value: VerificationMethod): String = value.name

    @TypeConverter
    fun toVerificationMethod(value: String): VerificationMethod =
        VerificationMethod.valueOf(value)

    @TypeConverter
    fun fromQuestState(value: QuestState): String = value.name

    @TypeConverter
    fun toQuestState(value: String): QuestState = QuestState.valueOf(value)
}
