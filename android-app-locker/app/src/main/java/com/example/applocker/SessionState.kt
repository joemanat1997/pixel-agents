package com.example.applocker

import java.util.Collections

/**
 * In-memory record of which locked packages the user has unlocked for the
 * current session. An app stays unlocked until it leaves the foreground or the
 * screen turns off, after which it must be unlocked again.
 */
object SessionState {

    private val unlocked: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf())

    /** Set while the lock screen is on top, to avoid re-launching it in a loop. */
    @Volatile
    var lockPromptShowing: Boolean = false

    /** True once the user has unlocked the App Locker app itself this session. */
    @Volatile
    var appUnlocked: Boolean = false

    fun isUnlocked(pkg: String): Boolean = unlocked.contains(pkg)

    fun markUnlocked(pkg: String) {
        unlocked.add(pkg)
    }

    fun relock(pkg: String) {
        unlocked.remove(pkg)
    }

    fun relockAll() {
        unlocked.clear()
    }
}
