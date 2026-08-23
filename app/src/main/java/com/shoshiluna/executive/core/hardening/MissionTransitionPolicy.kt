package com.shoshiluna.executive.core.hardening

import com.shoshiluna.executive.core.empire.MissionStatus

/** Fail-closed mission state machine. Unknown transitions are denied. */
object MissionTransitionPolicy {
    private val terminal = setOf(MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.REJECTED, MissionStatus.CANCELLED, MissionStatus.HALTED)
    private val allowed = mapOf(
        MissionStatus.CREATED to setOf(MissionStatus.PLANNED, MissionStatus.ASSIGNED, MissionStatus.RUNNING, MissionStatus.DISCOVERED, MissionStatus.CANCELLED, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.PLANNED to setOf(MissionStatus.ASSIGNED, MissionStatus.RUNNING, MissionStatus.AWAITING_APPROVAL, MissionStatus.CANCELLED, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.ASSIGNED to setOf(MissionStatus.RUNNING, MissionStatus.WAITING, MissionStatus.CANCELLED, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.RUNNING to setOf(MissionStatus.WAITING, MissionStatus.REVIEW, MissionStatus.AWAITING_APPROVAL, MissionStatus.WAITING_FOR_USER, MissionStatus.WAITING_FOR_APPROVAL, MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE, MissionStatus.WAITING_FOR_DATA, MissionStatus.WAITING_FOR_DEPENDENCY, MissionStatus.PAUSED, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.WAITING to setOf(MissionStatus.RUNNING, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.REVIEW to setOf(MissionStatus.COMPLETED, MissionStatus.RUNNING, MissionStatus.REJECTED, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.AWAITING_APPROVAL to setOf(MissionStatus.APPROVED, MissionStatus.REJECTED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.APPROVED to setOf(MissionStatus.EXECUTING, MissionStatus.RUNNING, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.EXECUTING to setOf(MissionStatus.VALIDATING, MissionStatus.REVIEW, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.VALIDATING to setOf(MissionStatus.REVIEW, MissionStatus.COMPLETED, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.RESEARCHING to setOf(MissionStatus.EVALUATING, MissionStatus.WAITING_FOR_DATA, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.EVALUATING to setOf(MissionStatus.AWAITING_APPROVAL, MissionStatus.REJECTED, MissionStatus.FAILED, MissionStatus.HALTED),
        MissionStatus.DISCOVERED to setOf(MissionStatus.RESEARCHING, MissionStatus.REJECTED, MissionStatus.HALTED),
        MissionStatus.WAITING_FOR_USER to setOf(MissionStatus.RUNNING, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.WAITING_FOR_APPROVAL to setOf(MissionStatus.RUNNING, MissionStatus.REJECTED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.WAITING_FOR_EXTERNAL_RESPONSE to setOf(MissionStatus.RUNNING, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.WAITING_FOR_DATA to setOf(MissionStatus.RUNNING, MissionStatus.RESEARCHING, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.WAITING_FOR_DEPENDENCY to setOf(MissionStatus.RUNNING, MissionStatus.FAILED, MissionStatus.CANCELLED, MissionStatus.HALTED),
        MissionStatus.PAUSED to setOf(MissionStatus.RUNNING, MissionStatus.CANCELLED, MissionStatus.HALTED)
    )
    fun canTransition(from: MissionStatus, to: MissionStatus): Boolean {
        if (from == to) return true
        if (from in terminal) return false
        return to in (allowed[from] ?: emptySet())
    }

    fun requireAllowed(from: MissionStatus, to: MissionStatus) {
        require(canTransition(from, to)) {
            if (from in terminal) "Terminal mission cannot transition: $from -> $to"
            else "Illegal mission transition: $from -> $to"
        }
    }
}
