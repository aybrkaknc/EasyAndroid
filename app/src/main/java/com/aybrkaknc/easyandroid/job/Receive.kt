package com.aybrkaknc.easyandroid.job

import com.aybrkaknc.easyandroid.core.PcProgress

/** Where the item at one place of an incoming transfer stands. */
enum class ReceiveStep { WAITING, RUNNING, ARRIVED, CANCELLED, STOPPED }

/** Reads what the PC last said for the item at [index]. */
fun receiveStep(progress: PcProgress?, index: Int): ReceiveStep = when {
    progress == null -> ReceiveStep.WAITING
    progress.index > index || progress.state == "done" -> ReceiveStep.ARRIVED
    progress.state == "cancelled" -> ReceiveStep.CANCELLED
    progress.state == "failed" -> ReceiveStep.STOPPED
    progress.index == index -> ReceiveStep.RUNNING
    else -> ReceiveStep.WAITING
}
