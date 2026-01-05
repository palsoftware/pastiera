package it.palsoftware.pastiera.core.suggestions.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_words")
data class UserWordEntity(
    @PrimaryKey val word: String, // Use lowercase as key for lookup, original preserved in field if needed
    val originalWord: String,
    val frequency: Int,
    val lastUsed: Long
)

