package org.wikipedia.translation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranslationCacheDao {

    @Query("SELECT * FROM TranslationCacheEntry WHERE title = :title AND sourceLang = :sourceLang AND targetLang = :targetLang LIMIT 1")
    suspend fun getEntry(title: String, sourceLang: String, targetLang: String): TranslationCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TranslationCacheEntry)

    @Query("DELETE FROM TranslationCacheEntry WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
