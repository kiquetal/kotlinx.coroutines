/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlinx.coroutines.internal.*
import kotlinx.coroutines.intrinsics.*
import kotlinx.coroutines.selects.*
import kotlinx.coroutines.timeunit.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Runs a given suspending [block] of code inside a coroutine with a specified timeout and throws
 * [TimeoutCancellationException] if timeout was exceeded.
 *
 * The code that is executing inside the [block] is cancelled on timeout and the active or next invocation of
 * cancellable suspending function inside the block throws [TimeoutCancellationException].
 * Even if the code in the block suppresses [TimeoutCancellationException], it
 * is still thrown by `withTimeout` invocation.
 *
 * The sibling function that does not throw exception on timeout is [withTimeoutOrNull].
 * Note, that timeout action can be specified for [select] invocation with [onTimeout][SelectBuilder.onTimeout] clause.
 *
 * This function delegates to [Delay.invokeOnTimeout] if the context [CoroutineDispatcher]
 * implements [Delay] interface, otherwise it tracks time using a built-in single-threaded scheduled executor service.
 *
 * @param time timeout time in milliseconds.
 */
public suspend fun <T> withTimeout(time: Int, block: suspend CoroutineScope.() -> T): T =
    withTimeout(time.toLong(), TimeUnit.MILLISECONDS, block)

/**
 * Runs a given suspending [block] of code inside a coroutine with a specified timeout and throws
 * [TimeoutCancellationException] if timeout was exceeded.
 *
 * The code that is executing inside the [block] is cancelled on timeout and the active or next invocation of
 * cancellable suspending function inside the block throws [TimeoutCancellationException].
 * Even if the code in the block suppresses [TimeoutCancellationException], it
 * is still thrown by `withTimeout` invocation.
 *
 * The sibling function that does not throw exception on timeout is [withTimeoutOrNull].
 * Note, that timeout action can be specified for [select] invocation with [onTimeout][SelectBuilder.onTimeout] clause.
 *
 * This function delegates to [Delay.invokeOnTimeout] if the context [CoroutineDispatcher]
 * implements [Delay] interface, otherwise it tracks time using a built-in single-threaded scheduled executor service.
 *
 * @param time timeout time
 * @param unit timeout unit (milliseconds by default)
 */
public suspend fun <T> withTimeout(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, block: suspend CoroutineScope.() -> T): T {
    if (time <= 0L) throw CancellationException("Timed out immediately")
    return suspendCoroutineUninterceptedOrReturn { uCont ->
        setupTimeout(TimeoutCoroutine(time, unit, uCont), block)
    }
}

private fun <U, T: U> setupTimeout(
    coroutine: TimeoutCoroutine<U, T>,
    block: suspend CoroutineScope.() -> T
): Any? {
    // schedule cancellation of this coroutine on time
    val cont = coroutine.uCont
    val context = cont.context
    coroutine.disposeOnCompletion(context.delay.invokeOnTimeout(coroutine.time, coroutine.unit, coroutine))
    // restart block using new coroutine with new job,
    // however start it as undispatched coroutine, because we are already in the proper context
    return coroutine.startUndispatchedOrReturn(coroutine, block)
}

/**
 * @suppress **Deprecated**: for binary compatibility only
 */
@Deprecated("for binary compatibility only", level=DeprecationLevel.HIDDEN)
public suspend fun <T> withTimeout(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, block: suspend () -> T): T =
    withTimeout(time, unit) { block() }

private open class TimeoutCoroutine<U, in T: U>(
    @JvmField val time: Long,
    @JvmField val unit: TimeUnit,
    @JvmField val uCont: Continuation<U> // unintercepted continuation
) : AbstractCoroutine<T>(uCont.context, active = true), Runnable, Continuation<T> {
    override val defaultResumeMode: Int get() = MODE_DIRECT

    @Suppress("LeakingThis")
    override fun run() {
        cancel(TimeoutCancellationException(time, unit, this))
    }

    @Suppress("UNCHECKED_CAST")
    internal override fun onCompletionInternal(state: Any?, mode: Int) {
        if (state is CompletedExceptionally)
            uCont.resumeUninterceptedWithExceptionMode(state.cause, mode)
        else
            uCont.resumeUninterceptedMode(state as T, mode)
    }

    override fun nameString(): String =
        "${super.nameString()}($time $unit)"
}

/**
 * Runs a given suspending block of code inside a coroutine with a specified timeout and returns
 * `null` if this timeout was exceeded.
 *
 * The code that is executing inside the [block] is cancelled on timeout and the active or next invocation of
 * cancellable suspending function inside the block throws [TimeoutCancellationException].
 * Even if the code in the block suppresses [TimeoutCancellationException], this
 * invocation of `withTimeoutOrNull` still returns `null`.
 *
 * The sibling function that throws exception on timeout is [withTimeout].
 * Note, that timeout action can be specified for [select] invocation with [onTimeout][SelectBuilder.onTimeout] clause.
 *
 * This function delegates to [Delay.invokeOnTimeout] if the context [CoroutineDispatcher]
 * implements [Delay] interface, otherwise it tracks time using a built-in single-threaded scheduled executor service.
 *
 * @param time timeout time in milliseconds.
 */
public suspend fun <T> withTimeoutOrNull(time: Int, block: suspend CoroutineScope.() -> T): T? =
    withTimeoutOrNull(time.toLong(), TimeUnit.MILLISECONDS, block)

/**
 * Runs a given suspending block of code inside a coroutine with a specified timeout and returns
 * `null` if this timeout was exceeded.
 *
 * The code that is executing inside the [block] is cancelled on timeout and the active or next invocation of
 * cancellable suspending function inside the block throws [TimeoutCancellationException].
 * Even if the code in the block suppresses [TimeoutCancellationException], this
 * invocation of `withTimeoutOrNull` still returns `null`.
 *
 * The sibling function that throws exception on timeout is [withTimeout].
 * Note, that timeout action can be specified for [select] invocation with [onTimeout][SelectBuilder.onTimeout] clause.
 *
 * This function delegates to [Delay.invokeOnTimeout] if the context [CoroutineDispatcher]
 * implements [Delay] interface, otherwise it tracks time using a built-in single-threaded scheduled executor service.
 *
 * @param time timeout time
 * @param unit timeout unit (milliseconds by default)
 */
public suspend fun <T> withTimeoutOrNull(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, block: suspend CoroutineScope.() -> T): T? {
    if (time <= 0L) return null
    return suspendCoroutineUninterceptedOrReturn { uCont ->
        setupTimeout(TimeoutOrNullCoroutine(time, unit, uCont), block)
    }
}

/**
 * @suppress **Deprecated**: for binary compatibility only
 */
@Deprecated("for binary compatibility only", level=DeprecationLevel.HIDDEN)
public suspend fun <T> withTimeoutOrNull(time: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, block: suspend () -> T): T? =
    withTimeoutOrNull(time, unit) { block() }

private class TimeoutOrNullCoroutine<T>(
    time: Long,
    unit: TimeUnit,
    uCont: Continuation<T?> // unintercepted continuation
) : TimeoutCoroutine<T?, T>(time, unit, uCont) {
    @Suppress("UNCHECKED_CAST")
    internal override fun onCompletionInternal(state: Any?, mode: Int) {
        if (state is CompletedExceptionally) {
            val exception = state.cause
            if (exception is TimeoutCancellationException && exception.coroutine === this)
                uCont.resumeUninterceptedMode(null, mode) else
                uCont.resumeUninterceptedWithExceptionMode(exception, mode)
        } else
            uCont.resumeUninterceptedMode(state as T, mode)
    }
}

/**
 * This exception is thrown by [withTimeout] to indicate timeout.
 */
public class TimeoutCancellationException internal constructor(
    message: String,
    @JvmField internal val coroutine: Job?
) : CancellationException(message) {
    /**
     * Creates timeout exception with a given message.
     */
    public constructor(message: String) : this(message, null)
}

@Suppress("FunctionName")
internal fun TimeoutCancellationException(
    time: Long,
    unit: TimeUnit,
    coroutine: Job
) : TimeoutCancellationException = TimeoutCancellationException("Timed out waiting for $time $unit", coroutine)
