package pl.qus.threading

import pl.qus.vrhelper.Logger

abstract class StoppableRunnable : Runnable {
    enum class State {
        IDLE, LAUNCHED, CANCELLED
    }

    private var state = State.IDLE

    fun cancel(): Boolean {
        if (state.ordinal < State.LAUNCHED.ordinal) {
            state = State.CANCELLED
            return true
        } else if (state == State.LAUNCHED) {
            state = State.CANCELLED
            return false
        } else
            return false
    }

    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        if (!mayInterruptIfRunning && state == State.LAUNCHED)
            return false
        else {
            return cancel()
        }
    }

    val isCancelled: Boolean
        get() = state == State.CANCELLED

    val isRunning: Boolean
        get() = state == State.LAUNCHED

    abstract fun execute()

    override fun run() {
        if (!isCancelled) {
            try {
                state = State.LAUNCHED
                execute()
                state = State.IDLE
            } finally {
                Logger.d("[MilkServer] thread finished")
            }
        }
    }
}
