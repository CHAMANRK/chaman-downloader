package com.chaman.downloader

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Queue ka ek download (Room row). status = QStatus ka naam (String). */
@Entity(tableName = "downloads")
data class DlItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fmt: String,
    val title: String,
    val thumb: String,
    val status: String,
    val progress: Float,
    val error: String,
    val savedUri: String,
    val savedMime: String,
    val addedAt: Long
)

@Dao
interface DlDao {
    @Insert
    fun insert(item: DlItem): Long

    @Query("SELECT * FROM downloads")
    fun observeAll(): Flow<List<DlItem>>

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY addedAt ASC, id ASC LIMIT 1")
    fun nextQueued(): DlItem?

    /** QUEUED se RUNNING; 0 aaye to kisi ne beech me cancel/hata diya, skip karo. */
    @Query("UPDATE downloads SET status = 'RUNNING', progress = 0, error = '' WHERE id = :id AND status = 'QUEUED'")
    fun claim(id: Long): Int

    @Query("UPDATE downloads SET status = :status, progress = :progress, error = :error, savedUri = :savedUri, savedMime = :savedMime WHERE id = :id")
    fun finish(id: Long, status: String, progress: Float, error: String, savedUri: String, savedMime: String)

    @Query("UPDATE downloads SET title = :title WHERE id = :id")
    fun setTitle(id: Long, title: String)

    /** Sirf QUEUED item cancel hota hai (RUNNING ko service rokti hai). */
    @Query("UPDATE downloads SET status = 'CANCELLED' WHERE id = :id AND status = 'QUEUED'")
    fun cancelQueued(id: Long): Int

    /** FAIL / CANCELLED ko dobara line ke aakhir me daalo. */
    @Query("UPDATE downloads SET status = 'QUEUED', error = '', progress = 0, addedAt = :now WHERE id = :id AND status IN ('FAIL', 'CANCELLED')")
    fun requeue(id: Long, now: Long): Int

    @Query("DELETE FROM downloads WHERE id = :id AND status != 'RUNNING'")
    fun remove(id: Long): Int

    @Query("DELETE FROM downloads WHERE status = 'PASS'")
    fun clearCompleted(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED', 'RUNNING')")
    fun activeCount(): Int

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'QUEUED'")
    fun queuedCount(): Int

    /** chaman-link: ek item ki ab ki halat (null = hata diya gaya). */
    @Query("SELECT * FROM downloads WHERE id = :id")
    fun get(id: Long): DlItem?
}

@Database(entities = [DlItem::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): DlDao

    companion object {
        @Volatile
        private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "chaman.db")
                .addCallback(object : RoomDatabase.Callback() {
                    // DB is process me pehli baar khulta hai: pichli baar jo RUNNING reh gaya wo app band hone se adhura hai
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "UPDATE downloads SET status = 'FAIL', error = 'Interrupted: app band ho gaya tha' WHERE status = 'RUNNING'"
                        )
                    }
                })
                .build()
                .also { inst = it }
        }
    }
}
