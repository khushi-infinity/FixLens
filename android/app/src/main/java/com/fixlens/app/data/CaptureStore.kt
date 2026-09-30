package com.fixlens.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.captureDataStore by preferencesDataStore(name = "fixlens_captures")

/** One entry in the local, on-device repair history (Phase 1: capture records only). */
data class CaptureRecord(
    val id: String,
    val source: CaptureSource,
    val createdAtMillis: Long,
    /** Absolute path of the stored image, or null if the file is gone. */
    val imagePath: String?,
)

enum class CaptureSource { PHOTO_MODE, LIVE_CAMERA }

/**
 * Local capture history for "My Repairs". Records are created only after the
 * user confirms a captured image, never on preview or retake. Images are kept
 * under filesDir/captures; stale image files are pruned when records are removed.
 */
class CaptureStore(private val context: Context) {

    private val dataStore = context.captureDataStore

    /** Newest first. */
    val records: Flow<List<CaptureRecord>> = dataStore.data.map { prefs ->
        val count = prefs[KEY_COUNT] ?: 0L
        (0 until count.toInt())
            .map { index -> prefs[prefKey(index)] }
            .filterNotNull()
            .map(::decode)
            .sortedByDescending { it.createdAtMillis }
    }

    /**
     * Persists the confirmed image and a record. Returns the stored record.
     * All-or-nothing: file copy happens before the record is committed.
     */
    suspend fun saveConfirmedCapture(
        sourceFile: File,
        source: CaptureSource,
    ): CaptureRecord {
        val captureDir = File(context.filesDir, "captures").apply { mkdirs() }
        val fileName = "capture_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.jpg"
        val target = File(captureDir, fileName)
        sourceFile.copyTo(target, overwrite = false)

        val record = CaptureRecord(
            id = "cap_${target.lastModified()}_${fileName.hashCode()}",
            source = source,
            createdAtMillis = Instant.now().toEpochMilli(),
            imagePath = target.absolutePath,
        )

        dataStore.edit { prefs ->
            val count = prefs[KEY_COUNT] ?: 0L
            prefs[KEY_COUNT] = count + 1
            prefs[prefKey(count.toInt())] = encode(record)
        }
        return record
    }

    /** Removes a record and its stored image file. */
    suspend fun delete(record: CaptureRecord) {
        dataStore.edit { prefs ->
            val count = prefs[KEY_COUNT] ?: 0L
            val entries = (0 until count.toInt())
                .map { index -> prefs[prefKey(index)] }
                .filterNotNull()
                .filterNot { decoded -> decode(decoded).id == record.id }
            prefs[KEY_COUNT] = 0
            entries.forEachIndexed { index, encoded -> prefs[prefKey(index)] = encoded }
        }
        record.imagePath?.let { path -> File(path).delete() }
    }

    /** Clears all records and stored images. Used by the My Repairs empty-state test path. */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            val count = prefs[KEY_COUNT] ?: 0L
            (0 until count.toInt())
                .map { index -> prefs[prefKey(index)] }
                .filterNotNull()
                .forEach { encoded -> decode(encoded).imagePath?.let { path -> File(path).delete() } }
            prefs.clear()
        }
    }

    private fun encode(record: CaptureRecord): String =
        listOf(
            record.id,
            record.source.name,
            record.createdAtMillis.toString(),
            record.imagePath.orEmpty(),
        ).joinToString(SEPARATOR)

    private fun decode(encoded: String): CaptureRecord {
        val parts = encoded.split(SEPARATOR)
        require(parts.size >= 4) { "Corrupt capture record" }
        return CaptureRecord(
            id = parts[0],
            source = CaptureSource.valueOf(parts[1]),
            createdAtMillis = parts[2].toLong(),
            imagePath = parts[3].ifEmpty { null },
        )
    }

    private fun prefKey(index: Int) = stringPreferencesKey("record_$index")

    private companion object {
        val KEY_COUNT = longPreferencesKey("capture_count")
        const val SEPARATOR = "|"
        val SET_KEY = stringSetPreferencesKey("unused") // reserved for future metadata
    }
}
