package dev.todor.fassistantclick.gesture

import android.os.Handler
import android.os.Looper
import dev.todor.fassistantclick.script.Script
import dev.todor.fassistantclick.script.Step

/**
 * Walks a script: countdown, then one gesture at a time, waiting each step's own delay before
 * moving on, for as many passes as the script asks for.
 *
 * Steps are chained off the dispatch callback rather than off a fixed clock, so a phone that
 * takes longer than asked to deliver a gesture falls behind instead of overlapping gestures.
 */
class Runner(private val host: Host) {

    interface Host {
        /** Returns false when Android would not even accept the gesture. */
        fun dispatch(step: Step, onDone: (Boolean) -> Unit): Boolean
        fun onRunnerChanged()
        fun onRunnerRejected(stepNumber: Int)
        fun onRunnerFinished()
    }

    enum class State { IDLE, COUNTDOWN, RUNNING, PAUSED }

    private val handler = Handler(Looper.getMainLooper())
    private val countdownTick = Runnable { tick() }
    private val stepTick = Runnable { runStep() }

    var state = State.IDLE
        private set
    var script: Script? = null
        private set

    /** 1-based, and the number a person would call "the third time round". */
    var pass = 0
        private set
    var stepIndex = 0
        private set
    var countdownLeft = 0
        private set

    /** A gesture cannot be interrupted once dispatched, so a pause lands at the next boundary. */
    var pausePending = false
        private set

    val busy: Boolean get() = state != State.IDLE

    fun start(toRun: Script) {
        handler.removeCallbacks(countdownTick)
        handler.removeCallbacks(stepTick)
        script = toRun
        pass = 1
        stepIndex = 0
        pausePending = false

        countdownLeft = ((toRun.countdownMs + 999L) / 1000L).toInt()
        if (countdownLeft > 0) {
            state = State.COUNTDOWN
            host.onRunnerChanged()
            handler.postDelayed(countdownTick, 1_000L)
        } else {
            state = State.RUNNING
            host.onRunnerChanged()
            runStep()
        }
    }

    fun stop() {
        reset()
    }

    fun pause() {
        when (state) {
            State.RUNNING -> {
                pausePending = true
                host.onRunnerChanged()
            }
            State.COUNTDOWN -> {
                handler.removeCallbacks(countdownTick)
                state = State.PAUSED
                host.onRunnerChanged()
            }
            else -> Unit
        }
    }

    fun resume() {
        if (state != State.PAUSED) return
        state = State.RUNNING
        pausePending = false
        host.onRunnerChanged()
        runStep()
    }

    private fun tick() {
        if (state != State.COUNTDOWN) return
        countdownLeft--
        if (countdownLeft > 0) {
            host.onRunnerChanged()
            handler.postDelayed(countdownTick, 1_000L)
        } else {
            state = State.RUNNING
            runStep()
        }
    }

    private fun runStep() {
        val current = script ?: return reset()
        val step = current.steps.getOrNull(stepIndex) ?: return reset()
        host.onRunnerChanged()

        val accepted = host.dispatch(step) { completed -> onGestureDone(completed) }
        if (!accepted) {
            val rejectedAt = stepIndex + 1
            reset()
            host.onRunnerRejected(rejectedAt)
        }
    }

    private fun onGestureDone(completed: Boolean) {
        if (state != State.RUNNING) return
        if (!completed) {
            val rejectedAt = stepIndex + 1
            reset()
            host.onRunnerRejected(rejectedAt)
            return
        }

        val current = script ?: return reset()
        val justRan = current.steps.getOrNull(stepIndex) ?: return reset()

        // Advance first, so a pause leaves stepIndex on the step that has *not* run yet —
        // otherwise resuming would repeat the step the pause interrupted.
        if (!advance(current)) {
            reset()
            host.onRunnerFinished()
            return
        }

        if (pausePending) {
            pausePending = false
            state = State.PAUSED
            host.onRunnerChanged()
            return
        }

        handler.postDelayed(stepTick, justRan.delayMs.coerceAtLeast(0L))
    }

    /** Moves to the next step, wrapping into the next pass, and says whether there is one. */
    private fun advance(current: Script): Boolean {
        stepIndex++
        if (stepIndex < current.steps.size) return true
        stepIndex = 0
        pass++
        return current.forever || pass <= current.repeats
    }

    private fun reset() {
        handler.removeCallbacks(countdownTick)
        handler.removeCallbacks(stepTick)
        state = State.IDLE
        pass = 0
        stepIndex = 0
        countdownLeft = 0
        pausePending = false
        host.onRunnerChanged()
    }
}
