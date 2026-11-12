package com.skydream.androiddesk.app.data

import android.content.Context
import com.skydream.androiddesk.core.state.DeskSnapshot
import com.skydream.androiddesk.core.state.DeskSnapshotCodec
import java.io.File
import java.io.IOException

/** The desktop's single on-device file: layout, icon styles and settings. */
class DeskStore(context: Context) {
    private val file = File(context.filesDir, "desk.json")
    private val temp = File(context.filesDir, "desk.json.tmp")
    private val rejected = File(context.filesDir, "desk.json.bad")

    /** Null on first run, or when the file cannot be read; an unreadable file is kept aside, not deleted. */
    fun load(): DeskSnapshot? {
        if (!file.exists()) return null
        return try {
            val snapshot = DeskSnapshotCodec.decode(file.readText())
            if (snapshot == null) file.copyTo(rejected, overwrite = true)
            snapshot
        } catch (_: IOException) {
            null
        }
    }

    /** Writes to a temp file first so a kill mid-write cannot leave half a layout behind. */
    fun save(snapshot: DeskSnapshot) {
        try {
            temp.writeText(DeskSnapshotCodec.encode(snapshot))
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } catch (_: IOException) {
            // Losing one save is recoverable: the next change writes again.
        }
    }
}
