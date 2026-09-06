package it.palsoftware.pastiera.core.suggestions

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

interface UserNGramRepository {
    fun learn(locale: String, prefix: String, nextWord: String, nowMs: Long = System.currentTimeMillis())
    fun predict(locale: String, prefix: String, limit: Int): List<UserNGramStore.Prediction>
    fun delete(locale: String, prefix: String, nextWord: String): Int
    fun deleteNextWord(locale: String, nextWord: String): Int
    fun clearAll()
}

class UserNGramStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
), UserNGramRepository {

    data class Prediction(
        val word: String,
        val count: Int,
        val lastUsed: Long
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_BIGRAMS (
                $COL_LOCALE TEXT NOT NULL,
                $COL_PREFIX TEXT NOT NULL,
                $COL_NEXT_WORD TEXT NOT NULL,
                $COL_COUNT INTEGER NOT NULL,
                $COL_LAST_USED INTEGER NOT NULL,
                PRIMARY KEY ($COL_LOCALE, $COL_PREFIX, $COL_NEXT_WORD)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX ${TABLE_BIGRAMS}_lookup ON $TABLE_BIGRAMS " +
                "($COL_LOCALE, $COL_PREFIX, $COL_COUNT DESC, $COL_LAST_USED DESC)"
        )
        seedDefaultBigrams(db)
    }

    /**
     * Pre-populates a small set of very common Vietnamese word-pair predictions, so next-word
     * suggestions aren't completely empty for a brand-new install. Seeded at a low baseline
     * count (1) so genuinely learned usage (which increments on every real use) naturally
     * overtakes it over time rather than permanently dominating.
     */
    private fun seedDefaultBigrams(db: SQLiteDatabase) {
        val nowMs = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for ((prefix, nextWord) in DEFAULT_VI_BIGRAMS) {
                db.insertWithOnConflict(
                    TABLE_BIGRAMS,
                    null,
                    ContentValues().apply {
                        put(COL_LOCALE, "vi")
                        put(COL_PREFIX, prefix)
                        put(COL_NEXT_WORD, nextWord)
                        put(COL_COUNT, 1)
                        put(COL_LAST_USED, nowMs)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_BIGRAMS")
            onCreate(db)
        }
    }

    override fun learn(locale: String, prefix: String, nextWord: String, nowMs: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                TABLE_BIGRAMS,
                null,
                ContentValues().apply {
                    put(COL_LOCALE, locale)
                    put(COL_PREFIX, prefix)
                    put(COL_NEXT_WORD, nextWord)
                    put(COL_COUNT, 0)
                    put(COL_LAST_USED, nowMs)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            db.execSQL(
                """
                UPDATE $TABLE_BIGRAMS
                SET $COL_COUNT = $COL_COUNT + 1,
                    $COL_LAST_USED = ?
                WHERE $COL_LOCALE = ?
                    AND $COL_PREFIX = ?
                    AND $COL_NEXT_WORD = ?
                """.trimIndent(),
                arrayOf(nowMs, locale, prefix, nextWord)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun predict(locale: String, prefix: String, limit: Int): List<Prediction> {
        if (limit <= 0) return emptyList()
        val cursor = readableDatabase.query(
            TABLE_BIGRAMS,
            arrayOf(COL_NEXT_WORD, COL_COUNT, COL_LAST_USED),
            "$COL_LOCALE = ? AND $COL_PREFIX = ?",
            arrayOf(locale, prefix),
            null,
            null,
            "$COL_COUNT DESC, $COL_LAST_USED DESC",
            limit.toString()
        )
        cursor.use {
            val results = ArrayList<Prediction>(limit)
            val wordIndex = it.getColumnIndexOrThrow(COL_NEXT_WORD)
            val countIndex = it.getColumnIndexOrThrow(COL_COUNT)
            val lastUsedIndex = it.getColumnIndexOrThrow(COL_LAST_USED)
            while (it.moveToNext()) {
                results.add(
                    Prediction(
                        word = it.getString(wordIndex),
                        count = it.getInt(countIndex),
                        lastUsed = it.getLong(lastUsedIndex)
                    )
                )
            }
            return results
        }
    }

    override fun delete(locale: String, prefix: String, nextWord: String): Int {
        return writableDatabase.delete(
            TABLE_BIGRAMS,
            "$COL_LOCALE = ? AND $COL_PREFIX = ? AND $COL_NEXT_WORD = ? COLLATE NOCASE",
            arrayOf(locale, prefix, nextWord)
        )
    }

    override fun deleteNextWord(locale: String, nextWord: String): Int {
        return writableDatabase.delete(
            TABLE_BIGRAMS,
            "$COL_LOCALE = ? AND $COL_NEXT_WORD = ? COLLATE NOCASE",
            arrayOf(locale, nextWord)
        )
    }

    override fun clearAll() {
        writableDatabase.delete(TABLE_BIGRAMS, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "user_ngrams.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_BIGRAMS = "bigrams"
        private const val COL_LOCALE = "locale"
        private const val COL_PREFIX = "prefix"
        private const val COL_NEXT_WORD = "next_word"
        private const val COL_COUNT = "count"
        private const val COL_LAST_USED = "last_used"

        // prefix is the accent-stripped, lowercase normalized form of the previous word
        // (matching NextWordPredictor.normalizedKey); next_word is the real display form.
        private val DEFAULT_VI_BIGRAMS: List<Pair<String, String>> = listOf(
            "khong" to "biết",
            "khong" to "có",
            "khong" to "phải",
            "khong" to "được",
            "khong" to "thể",
            "khong" to "sao",
            "khong" to "muốn",
            "khong" to "còn",
            "khong" to "ai",
            "khong" to "gì",
            "khong" to "đâu",
            "khong" to "hiểu",
            "khong" to "thích",
            "khong" to "bao giờ",
            "khong" to "dám",
            "toi" to "là",
            "toi" to "có",
            "toi" to "muốn",
            "toi" to "nghĩ",
            "toi" to "thích",
            "toi" to "đi",
            "toi" to "làm",
            "toi" to "biết",
            "toi" to "không",
            "toi" to "sẽ",
            "toi" to "đã",
            "minh" to "là",
            "minh" to "có",
            "minh" to "muốn",
            "minh" to "đi",
            "minh" to "nghĩ",
            "minh" to "không",
            "ban" to "có",
            "ban" to "là",
            "ban" to "muốn",
            "ban" to "đi",
            "ban" to "làm",
            "ban" to "ơi",
            "anh" to "có",
            "anh" to "là",
            "anh" to "muốn",
            "anh" to "đi",
            "anh" to "ơi",
            "chi" to "có",
            "chi" to "là",
            "chi" to "ơi",
            "em" to "có",
            "em" to "là",
            "em" to "muốn",
            "em" to "ơi",
            "rat" to "vui",
            "rat" to "tốt",
            "rat" to "nhiều",
            "rat" to "đẹp",
            "rat" to "thích",
            "rat" to "mệt",
            "rat" to "tiếc",
            "rat" to "khó",
            "rat" to "quan trọng",
            "kha" to "vui",
            "kha" to "tốt",
            "kha" to "nhiều",
            "qua" to "nhiều",
            "qua" to "tốt",
            "qua" to "vui",
            "co" to "thể",
            "co" to "lẽ",
            "co" to "người",
            "co" to "một",
            "co" to "nhiều",
            "co" to "vẻ",
            "co" to "khi",
            "co" to "lúc",
            "la" to "một",
            "la" to "người",
            "la" to "gì",
            "la" to "ai",
            "hom" to "nay",
            "hom" to "qua",
            "hom" to "sau",
            "bay" to "giờ",
            "va" to "tôi",
            "va" to "anh",
            "va" to "em",
            "va" to "các",
            "nhung" to "tôi",
            "nhung" to "anh",
            "nhung" to "không",
            "nhung" to "mà",
            "xin" to "chào",
            "xin" to "lỗi",
            "xin" to "cảm ơn",
            "cam" to "ơn",
            "cam" to "thấy",
            "dang" to "làm",
            "dang" to "đi",
            "dang" to "học",
            "se" to "có",
            "se" to "là",
            "se" to "đi",
            "se" to "làm",
            "se" to "không",
            "da" to "có",
            "da" to "là",
            "da" to "đi",
            "da" to "làm",
            "da" to "xong",
            "duoc" to "không",
            "duoc" to "rồi",
            "muon" to "đi",
            "muon" to "làm",
            "muon" to "biết",
            "muon" to "nói",
            "noi" to "chuyện",
            "noi" to "gì",
            "noi" to "với",
            "lam" to "gì",
            "lam" to "sao",
            "lam" to "việc",
            "di" to "đâu",
            "di" to "học",
            "di" to "làm",
            "di" to "ngủ",
            "an" to "cơm",
            "an" to "sáng",
            "an" to "trưa",
            "an" to "tối"
        )
    }
}
