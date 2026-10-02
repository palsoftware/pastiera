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

    // Trigram (two-word context) variants. The "prefix" here is a compound key of the two
    // preceding normalized words (see NextWordPredictor.contextKey). Kept as a distinct table
    // rather than overloading the bigram one so bigram fallback/backoff stays a simple query.
    fun learnTrigram(locale: String, contextKey: String, nextWord: String, nowMs: Long = System.currentTimeMillis())
    fun predictTrigram(locale: String, contextKey: String, limit: Int): List<UserNGramStore.Prediction>
    fun deleteTrigram(locale: String, contextKey: String, nextWord: String): Int
    fun deleteTrigramNextWord(locale: String, nextWord: String): Int

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
        createNGramTable(db, TABLE_BIGRAMS)
        createNGramTable(db, TABLE_TRIGRAMS)
        seedDefaultBigrams(db)
    }

    private fun createNGramTable(db: SQLiteDatabase, table: String) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $table (
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
            "CREATE INDEX IF NOT EXISTS ${table}_lookup ON $table " +
                "($COL_LOCALE, $COL_PREFIX, $COL_COUNT DESC, $COL_LAST_USED DESC)"
        )
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
            return
        }
        if (oldVersion < 2) {
            // Added the trigram (two-word context) table. Bigram data is untouched.
            createNGramTable(db, TABLE_TRIGRAMS)
        }
    }

    override fun learn(locale: String, prefix: String, nextWord: String, nowMs: Long) =
        learnInto(TABLE_BIGRAMS, locale, prefix, nextWord, nowMs)

    override fun predict(locale: String, prefix: String, limit: Int): List<Prediction> =
        predictFrom(TABLE_BIGRAMS, locale, prefix, limit)

    override fun delete(locale: String, prefix: String, nextWord: String): Int =
        deleteFrom(TABLE_BIGRAMS, locale, prefix, nextWord)

    override fun deleteNextWord(locale: String, nextWord: String): Int =
        deleteNextWordFrom(TABLE_BIGRAMS, locale, nextWord)

    override fun learnTrigram(locale: String, contextKey: String, nextWord: String, nowMs: Long) =
        learnInto(TABLE_TRIGRAMS, locale, contextKey, nextWord, nowMs)

    override fun predictTrigram(locale: String, contextKey: String, limit: Int): List<Prediction> =
        predictFrom(TABLE_TRIGRAMS, locale, contextKey, limit)

    override fun deleteTrigram(locale: String, contextKey: String, nextWord: String): Int =
        deleteFrom(TABLE_TRIGRAMS, locale, contextKey, nextWord)

    override fun deleteTrigramNextWord(locale: String, nextWord: String): Int =
        deleteNextWordFrom(TABLE_TRIGRAMS, locale, nextWord)

    private fun learnInto(table: String, locale: String, prefix: String, nextWord: String, nowMs: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict(
                table,
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
                UPDATE $table
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

    private fun predictFrom(table: String, locale: String, prefix: String, limit: Int): List<Prediction> {
        if (limit <= 0) return emptyList()
        val cursor = readableDatabase.query(
            table,
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

    private fun deleteFrom(table: String, locale: String, prefix: String, nextWord: String): Int {
        return writableDatabase.delete(
            table,
            "$COL_LOCALE = ? AND $COL_PREFIX = ? AND $COL_NEXT_WORD = ? COLLATE NOCASE",
            arrayOf(locale, prefix, nextWord)
        )
    }

    private fun deleteNextWordFrom(table: String, locale: String, nextWord: String): Int {
        return writableDatabase.delete(
            table,
            "$COL_LOCALE = ? AND $COL_NEXT_WORD = ? COLLATE NOCASE",
            arrayOf(locale, nextWord)
        )
    }

    override fun clearAll() {
        writableDatabase.delete(TABLE_BIGRAMS, null, null)
        writableDatabase.delete(TABLE_TRIGRAMS, null, null)
    }

    companion object {
        private const val DATABASE_NAME = "user_ngrams.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_BIGRAMS = "bigrams"
        private const val TABLE_TRIGRAMS = "trigrams"
        private const val COL_LOCALE = "locale"
        private const val COL_PREFIX = "prefix"
        private const val COL_NEXT_WORD = "next_word"
        private const val COL_COUNT = "count"
        private const val COL_LAST_USED = "last_used"

        // prefix is the accent-stripped, lowercase normalized form of the previous word
        // (matching NextWordPredictor.normalizedKey); next_word is the real display form.
        private val DEFAULT_VI_BIGRAMS: List<Pair<String, String>> = listOf(
            "dinh" to "làm",
            "dinh" to "đi",
            "dinh" to "ăn",
            "dinh" to "mua",
            "dinh" to "học",
            "dinh" to "nói",
            "dinh" to "gặp",
            "dinh" to "xem",
            "dinh" to "chơi",
            "dinh" to "về",
            "dinh" to "ở",
            "dinh" to "viết",
            "dinh" to "gọi",
            "dinh" to "hỏi",
            "dinh" to "nghỉ",
            "muon" to "đi",
            "muon" to "làm",
            "muon" to "biết",
            "muon" to "nói",
            "muon" to "ăn",
            "muon" to "mua",
            "muon" to "xem",
            "muon" to "gặp",
            "muon" to "học",
            "muon" to "về",
            "muon" to "hỏi",
            "muon" to "thử",
            "muon" to "nghỉ",
            "muon" to "ngủ",
            "can" to "đi",
            "can" to "làm",
            "can" to "biết",
            "can" to "nói",
            "can" to "mua",
            "can" to "học",
            "can" to "gặp",
            "can" to "xem",
            "can" to "giúp",
            "can" to "thêm",
            "can" to "thiết",
            "can" to "thận",
            "phai" to "làm",
            "phai" to "đi",
            "phai" to "học",
            "phai" to "không",
            "phai" to "biết",
            "phai" to "nói",
            "phai" to "chăng",
            "phai" to "chi",
            "phai" to "trả",
            "phai" to "về",
            "nen" to "làm",
            "nen" to "đi",
            "nen" to "học",
            "nen" to "không",
            "nen" to "biết",
            "nen" to "nói",
            "nen" to "chăng",
            "nen" to "ăn",
            "nen" to "mua",
            "nen" to "xem",
            "sap" to "đi",
            "sap" to "về",
            "sap" to "tới",
            "sap" to "xong",
            "sap" to "hết",
            "sap" to "đến",
            "sap" to "ra",
            "sap" to "làm",
            "hay" to "là",
            "hay" to "không",
            "hay" to "đi",
            "hay" to "làm",
            "hay" to "gì",
            "hay" to "không nào",
            "hay" to "quá",
            "hay" to "thế",
            "hay" to "vậy",
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
            "toi" to "đang",
            "toi" to "cần",
            "toi" to "phải",
            "minh" to "là",
            "minh" to "có",
            "minh" to "muốn",
            "minh" to "đi",
            "minh" to "nghĩ",
            "minh" to "không",
            "minh" to "sẽ",
            "minh" to "đã",
            "minh" to "đang",
            "minh" to "cần",
            "minh" to "biết",
            "minh" to "thích",
            "ban" to "có",
            "ban" to "là",
            "ban" to "muốn",
            "ban" to "đi",
            "ban" to "làm",
            "ban" to "ơi",
            "ban" to "biết",
            "ban" to "không",
            "ban" to "cần",
            "ban" to "nghĩ",
            "anh" to "có",
            "anh" to "là",
            "anh" to "muốn",
            "anh" to "đi",
            "anh" to "ơi",
            "anh" to "biết",
            "anh" to "không",
            "anh" to "sẽ",
            "anh" to "đã",
            "anh" to "đang",
            "anh" to "cần",
            "anh" to "nghĩ",
            "chi" to "có",
            "chi" to "là",
            "chi" to "ơi",
            "chi" to "muốn",
            "chi" to "đi",
            "chi" to "biết",
            "chi" to "không",
            "em" to "có",
            "em" to "là",
            "em" to "muốn",
            "em" to "ơi",
            "em" to "đi",
            "em" to "biết",
            "em" to "không",
            "em" to "sẽ",
            "no" to "là",
            "no" to "có",
            "no" to "không",
            "no" to "sẽ",
            "no" to "đã",
            "no" to "đang",
            "no" to "vẫn",
            "ho" to "là",
            "ho" to "có",
            "ho" to "không",
            "ho" to "sẽ",
            "ho" to "đã",
            "ho" to "đang",
            "ho" to "vẫn",
            "ho" to "muốn",
            "chungtoi" to "là",
            "chungtoi" to "có",
            "chungtoi" to "muốn",
            "chungtoi" to "sẽ",
            "chungtoi" to "đã",
            "chungtoi" to "đang",
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
            "khong" to "nên",
            "khong" to "cần",
            "khong" to "đúng",
            "khong" to "tốt",
            "khong" to "quan tâm",
            "khong" to "sao đâu",
            "khong" to "ngờ",
            "khong" to "hề",
            "khong" to "chỉ",
            "chua" to "biết",
            "chua" to "có",
            "chua" to "được",
            "chua" to "xong",
            "chua" to "chắc",
            "chua" to "hẳn",
            "chua" to "chắc chắn",
            "dung" to "rồi",
            "dung" to "đấy",
            "dung" to "vậy",
            "dung" to "là",
            "sai" to "rồi",
            "sai" to "đấy",
            "sai" to "lầm",
            "hom" to "nay",
            "hom" to "qua",
            "hom" to "sau",
            "hom" to "trước",
            "hom" to "đó",
            "hom" to "kia",
            "bay" to "giờ",
            "ngay" to "mai",
            "ngay" to "kia",
            "ngay" to "hôm",
            "ngay" to "xưa",
            "ngay" to "nay",
            "ngay" to "mốt",
            "tuan" to "này",
            "tuan" to "sau",
            "tuan" to "trước",
            "tuan" to "tới",
            "thang" to "này",
            "thang" to "sau",
            "thang" to "trước",
            "thang" to "tới",
            "nam" to "nay",
            "nam" to "sau",
            "nam" to "trước",
            "nam" to "ngoái",
            "nam" to "tới",
            "luc" to "này",
            "luc" to "đó",
            "luc" to "nãy",
            "luc" to "trước",
            "luc" to "sau",
            "khi" to "nào",
            "khi" to "đó",
            "khi" to "nãy",
            "khi" to "ấy",
            "rat" to "vui",
            "rat" to "tốt",
            "rat" to "nhiều",
            "rat" to "đẹp",
            "rat" to "thích",
            "rat" to "mệt",
            "rat" to "tiếc",
            "rat" to "khó",
            "rat" to "quan trọng",
            "rat" to "buồn",
            "rat" to "đói",
            "rat" to "lạnh",
            "rat" to "nóng",
            "rat" to "nhanh",
            "rat" to "chậm",
            "kha" to "vui",
            "kha" to "tốt",
            "kha" to "nhiều",
            "kha" to "đẹp",
            "kha" to "khó",
            "kha" to "xa",
            "kha" to "gần",
            "kha" to "nhanh",
            "qua" to "nhiều",
            "qua" to "tốt",
            "qua" to "vui",
            "qua" to "đắt",
            "qua" to "rẻ",
            "qua" to "khó",
            "qua" to "dễ",
            "hoi" to "gì",
            "hoi" to "sao",
            "hoi" to "tôi",
            "hoi" to "vậy",
            "co" to "thể",
            "co" to "lẽ",
            "co" to "người",
            "co" to "một",
            "co" to "nhiều",
            "co" to "vẻ",
            "co" to "khi",
            "co" to "lúc",
            "co" to "gì",
            "co" to "ai",
            "co" to "phải",
            "co" to "vấn đề",
            "la" to "một",
            "la" to "người",
            "la" to "gì",
            "la" to "ai",
            "la" to "vì",
            "la" to "do",
            "la" to "sao",
            "va" to "tôi",
            "va" to "anh",
            "va" to "em",
            "va" to "các",
            "va" to "những",
            "va" to "cả",
            "nhung" to "tôi",
            "nhung" to "anh",
            "nhung" to "không",
            "nhung" to "mà",
            "nhung" to "vẫn",
            "nhung" to "rồi",
            "hoac" to "là",
            "hoac" to "không",
            "vi" to "vậy",
            "vi" to "sao",
            "vi" to "thế",
            "vi" to "tôi",
            "vi" to "anh",
            "vi" to "không",
            "neu" to "không",
            "neu" to "có",
            "neu" to "được",
            "neu" to "như",
            "roi" to "sao",
            "roi" to "thì",
            "roi" to "đấy",
            "roi" to "à",
            "roi" to "nhé",
            "xin" to "chào",
            "xin" to "lỗi",
            "xin" to "cảm ơn",
            "xin" to "phép",
            "xin" to "mời",
            "xin" to "hỏi",
            "cam" to "ơn",
            "cam" to "thấy",
            "cam" to "giác",
            "tam" to "biệt",
            "tam" to "thời",
            "dang" to "làm",
            "dang" to "đi",
            "dang" to "học",
            "dang" to "xem",
            "dang" to "nói",
            "dang" to "chờ",
            "dang" to "nghĩ",
            "dang" to "ăn",
            "se" to "có",
            "se" to "là",
            "se" to "đi",
            "se" to "làm",
            "se" to "không",
            "se" to "được",
            "se" to "sớm",
            "da" to "có",
            "da" to "là",
            "da" to "đi",
            "da" to "làm",
            "da" to "xong",
            "da" to "rồi",
            "da" to "từng",
            "duoc" to "không",
            "duoc" to "rồi",
            "duoc" to "chưa",
            "duoc" to "đấy",
            "noi" to "chuyện",
            "noi" to "gì",
            "noi" to "với",
            "noi" to "dối",
            "noi" to "thật",
            "noi" to "là",
            "lam" to "gì",
            "lam" to "sao",
            "lam" to "việc",
            "lam" to "ơn",
            "lam" to "được",
            "lam" to "xong",
            "di" to "đâu",
            "di" to "học",
            "di" to "làm",
            "di" to "ngủ",
            "di" to "chơi",
            "di" to "về",
            "di" to "xe",
            "di" to "bộ",
            "di" to "ăn",
            "di" to "chưa",
            "an" to "cơm",
            "an" to "sáng",
            "an" to "trưa",
            "an" to "tối",
            "an" to "gì",
            "an" to "chưa",
            "an" to "uống",
            "uong" to "nước",
            "uong" to "trà",
            "uong" to "cà phê",
            "uong" to "rượu",
            "uong" to "bia",
            "uong" to "gì",
            "xem" to "phim",
            "xem" to "gì",
            "xem" to "nào",
            "xem" to "thử",
            "nghe" to "nhạc",
            "nghe" to "gì",
            "nghe" to "nói",
            "nghe" to "thấy",
            "nghe" to "này",
            "hoc" to "bài",
            "hoc" to "gì",
            "hoc" to "ở",
            "hoc" to "xong",
            "hoc" to "hành",
            "mua" to "gì",
            "mua" to "đồ",
            "mua" to "sắm",
            "mua" to "ở",
            "mua" to "được",
            "biet" to "không",
            "biet" to "chưa",
            "biet" to "rồi",
            "biet" to "gì",
            "biet" to "đâu",
            "hieu" to "không",
            "hieu" to "rồi",
            "hieu" to "chưa",
            "hieu" to "được",
            "hieu" to "ý",
            "thich" to "không",
            "thich" to "gì",
            "thich" to "lắm",
            "thich" to "quá",
            "thich" to "rồi",
            "nghi" to "gì",
            "nghi" to "sao",
            "nghi" to "vậy",
            "nghi" to "đến",
            "nghi" to "rằng",
            "goi" to "điện",
            "goi" to "cho",
            "goi" to "là",
            "goi" to "ai",
            "cho" to "tôi",
            "cho" to "anh",
            "cho" to "em",
            "cho" to "biết",
            "cho" to "hỏi",
            "cho" to "xin",
            "ai" to "đó",
            "ai" to "vậy",
            "ai" to "cũng",
            "ai" to "biết",
            "gi" to "vậy",
            "gi" to "đó",
            "gi" to "nữa",
            "gi" to "không",
            "gi" to "thế",
            "dau" to "vậy",
            "dau" to "đó",
            "dau" to "rồi",
            "dau" to "nhỉ",
            "sao" to "vậy",
            "sao" to "thế",
            "sao" to "không",
            "sao" to "rồi",
            "sao" to "lại",
            "nao" to "vậy",
            "nao" to "đó",
            "nao" to "cũng",
            "bao" to "giờ",
            "bao" to "nhiêu",
            "bao" to "lâu",
        )
    }
}
