package com.skydream.androiddesk.sdk

/**
 * Stable surface for assistants that want to act on Android Desk.
 *
 * v1 covers the desktop's own actions only (pages, drawer, icons, launching apps). It is a
 * ContentProvider `call()` API: an official IPC that tells the desktop which package is
 * calling, so the user's per-assistant permission can be enforced. Resident surfaces drawn
 * by the desktop (pet content, panel cards) and a bound-service session are still M4 work.
 */
enum class AgentCapability {
    Pet,
    Panel,
    DesktopActions,
    Screenshot,
}

data class AgentHello(
    val sdkVersion: Int,
    val agentId: String,
    val displayName: String,
    val capabilities: Set<AgentCapability>,
)

data class SessionToken(val value: String)

/**
 * Wire names for `ContentResolver.call(Uri.parse("content://" + AUTHORITY), method, arg, extras)`.
 *
 * `arg` is a shorthand for the method's main argument (the package name for app methods,
 * the page index for [Method.GO_TO_PAGE], the text for [Method.REPORT_STATUS]); the same
 * value may be passed in `extras` instead. Page and cell indexes are 0-based.
 *
 * The call blocks while the user is being asked, up to [CONFIRM_TIMEOUT_MS]; call it off
 * the main thread. The reply Bundle always has [Result.STATUS] and [Result.MESSAGE].
 */
object DeskProtocol {
    const val VERSION = 1
    const val AUTHORITY = "com.skydream.androiddesk.agent"
    const val CONFIRM_TIMEOUT_MS = 60_000L

    object Method {
        const val HELLO = "hello"
        const val READ_DESKTOP = "readDesktop"
        const val LIST_APPS = "listApps"
        const val GO_TO_PAGE = "goToPage"
        const val OPEN_DRAWER = "openDrawer"
        const val CLOSE_DRAWER = "closeDrawer"
        const val LAUNCH_APP = "launchApp"
        const val ADD_TO_DESKTOP = "addToDesktop"
        const val REMOVE_FROM_DESKTOP = "removeFromDesktop"
        const val MOVE_ITEM = "moveItem"
        const val PIN_TO_DOCK = "pinToDock"
        const val UNPIN_FROM_DOCK = "unpinFromDock"
        const val SET_ICON_STYLE = "setIconStyle"
        const val REPORT_STATUS = "reportStatus"
        const val END_TASK = "endTask"

        val all: Set<String> = setOf(
            HELLO, READ_DESKTOP, LIST_APPS, GO_TO_PAGE, OPEN_DRAWER, CLOSE_DRAWER, LAUNCH_APP,
            ADD_TO_DESKTOP, REMOVE_FROM_DESKTOP, MOVE_ITEM, PIN_TO_DOCK, UNPIN_FROM_DOCK,
            SET_ICON_STYLE, REPORT_STATUS, END_TASK,
        )
    }

    object Arg {
        const val PACKAGE = "package"
        const val PAGE = "page"
        const val X = "x"
        const val Y = "y"
        const val SHAPE = "shape"
        const val TREATMENT = "treatment"
        const val STATE = "state"
        const val TEXT = "text"

        /** Groups steps so "ask once per task" can apply. Optional. */
        const val TASK = "task"
    }

    object Result {
        const val STATUS = "status"
        const val MESSAGE = "message"

        /** JSON text, for [Method.READ_DESKTOP], [Method.LIST_APPS] and [Method.HELLO]. */
        const val DATA = "data"
    }

    object Status {
        /** Done. */
        const val OK = "ok"

        /** The user has not allowed this package to act on the desktop. */
        const val NOT_ALLOWED = "not_allowed"

        /** The user was asked and said no. */
        const val DENIED = "denied"

        /** The desktop will not do this for an assistant; the user has to do it. */
        const val NEEDS_USER = "needs_user"

        /** The user pressed stop. Nothing more is accepted until the user resumes. */
        const val STOPPED = "stopped"

        /** The desktop is not on screen, so the user could not see or confirm the action. */
        const val DESK_NOT_VISIBLE = "desk_not_visible"

        /** Nobody answered the confirmation in time. */
        const val TIMEOUT = "timeout"

        /** Unknown method, missing argument, unknown app, no room, and so on. */
        const val INVALID = "invalid"
    }
}
