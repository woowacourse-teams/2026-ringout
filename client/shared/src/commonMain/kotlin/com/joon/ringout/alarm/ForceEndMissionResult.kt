package com.joon.ringout.alarm

/** Completed means the terminal state is committed; record/OS cleanup may still be retried. */
enum class ForceEndMissionResult {
    Completed,
    NoLongerActive,
    Failed,
}
