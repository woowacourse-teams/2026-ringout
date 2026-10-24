package com.joon.ringout.presentation.profilechange

internal class ProfileImagePickerLaunchGuard {
    private var state = State.Idle

    fun tryStart(): Boolean {
        if (state != State.Idle) return false
        state = State.Busy
        return true
    }

    fun complete() {
        if (state == State.Busy) state = State.Idle
    }

    fun dispose() {
        state = State.Disposed
    }

    private enum class State {
        Idle,
        Busy,
        Disposed,
    }
}

/** Joins picker/file completion with UIKit dismissal before releasing the launch guard. */
internal class ProfileImagePickerDismissalGate(
    private val onComplete: (ProfileImagePickResult) -> Unit,
) {
    private var pickerFinished = false
    private var dismissed = false
    private var result: ProfileImagePickResult? = null
    private var disposed = false
    private var completed = false

    fun markPickerFinished() {
        if (disposed || completed) return
        pickerFinished = true
    }

    fun reportResult(result: ProfileImagePickResult) {
        if (disposed || completed || this.result != null) return
        pickerFinished = true
        this.result = result
        completeIfReady()
    }

    fun markDismissed() {
        if (disposed || completed) return
        dismissed = true
        if (!pickerFinished) {
            pickerFinished = true
            result = ProfileImagePickResult.Cancelled
        }
        completeIfReady()
    }

    fun dispose() {
        disposed = true
        result = null
    }

    private fun completeIfReady() {
        val completedResult = result ?: return
        if (!dismissed || disposed || completed) return
        completed = true
        onComplete(completedResult)
    }
}
