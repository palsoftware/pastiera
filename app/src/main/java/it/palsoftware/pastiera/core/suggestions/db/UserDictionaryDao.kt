package it.palsoftware.pastiera.core.suggestions.db

import androidx.room.*

@Dao
interface UserDictionaryDao {
    @Query("SELECT * FROM user_words")
    suspend fun getAllWords(): List<UserWordEntity>

    @Query("SELECT * FROM user_words WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): UserWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<UserWordEntity>)

    @Update
    suspend fun updateWord(word: UserWordEntity)

    @Query("DELETE FROM user_words WHERE word = :word")
    suspend fun deleteWord(word: String)

    @Query("DELETE FROM user_words")
    suspend fun clearAllWords()

    // NGram operations
    @Query("SELECT * FROM user_ngrams WHERE contextKey = :contextKey ORDER BY frequency DESC")
    suspend fun getNGramsForContext(contextKey: String): List<NGramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNGrams(ngrams: List<NGramEntity>)

    @Query("SELECT * FROM user_ngrams WHERE contextKey = :contextKey AND word = :word LIMIT 1")
    suspend fun getNGram(contextKey: String, word: String): NGramEntity?

    @Query("DELETE FROM user_ngrams")
    suspend fun clearAllNGrams()

    @Query("SELECT * FROM user_ngrams")
    suspend fun getAllNGrams(): List<NGramEntity>
    
    @Transaction
    suspend fun resetAll() {
        clearAllWords()
        clearAllNGrams()
    }
}

