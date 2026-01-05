package it.palsoftware.pastiera.core.suggestions.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "user_ngrams",
    primaryKeys = ["contextKey", "word"],
    indices = [Index(value = ["contextKey"])]
)
data class NGramEntity(
    val contextKey: String, // e.g. "word1|word2"
    val word: String,
    val frequency: Int,
    val lastUsed: Long
)

