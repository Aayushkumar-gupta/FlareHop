package com.flarehop.flare

enum class AdvertState { STOPPED, STARTING, ON, PAUSED }

sealed interface AdvertAction {
    class Start(val bytes: ByteArray) : AdvertAction

    class Update(val bytes: ByteArray) : AdvertAction

    class Resume(val bytes: ByteArray) : AdvertAction

    data object Pause : AdvertAction

    data object Wait : AdvertAction
}

object AdvertPlanner {
    fun decide(state: AdvertState, last: ByteArray?, next: ByteArray?): AdvertAction =
        when (state) {
            AdvertState.STOPPED -> if (next == null) AdvertAction.Wait else AdvertAction.Start(next)

            AdvertState.STARTING -> AdvertAction.Wait

            AdvertState.ON -> when {
                next == null -> AdvertAction.Pause
                last != null && next.contentEquals(last) -> AdvertAction.Wait
                else -> AdvertAction.Update(next)
            }

            AdvertState.PAUSED -> if (next == null) AdvertAction.Wait else AdvertAction.Resume(next)
        }
}
