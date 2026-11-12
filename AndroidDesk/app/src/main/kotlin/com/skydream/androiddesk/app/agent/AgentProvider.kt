package com.skydream.androiddesk.app.agent

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.skydream.androiddesk.app.DeskGraph
import com.skydream.androiddesk.sdk.DeskProtocol

/**
 * Entry point for assistants: `ContentResolver.call()` on [DeskProtocol.AUTHORITY].
 *
 * It is exported with no permission on purpose, because the check is per caller: the system
 * tells us which package is calling, and only packages the user switched on in the desktop's
 * settings get past `hello`. It stores nothing and exposes no rows.
 */
class AgentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return reply(AgentReply(DeskProtocol.Status.INVALID, "桌面还没有准备好"))
        val args = buildMap {
            extras?.keySet()?.forEach { key ->
                @Suppress("DEPRECATION")
                extras.get(key)?.let { put(key, it.toString()) }
            }
        }
        return reply(DeskGraph.controller(context).agent.handle(callingPackage, method, arg, args))
    }

    private fun reply(reply: AgentReply): Bundle = Bundle().apply {
        putString(DeskProtocol.Result.STATUS, reply.status)
        putString(DeskProtocol.Result.MESSAGE, reply.message)
        reply.data?.let { putString(DeskProtocol.Result.DATA, it) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
