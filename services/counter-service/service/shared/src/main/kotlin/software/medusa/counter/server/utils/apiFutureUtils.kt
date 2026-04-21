package software.medusa.counter.server.utils

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureToListenableFuture
import com.google.api.core.ListenableFutureToApiFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.guava.asDeferred
import kotlinx.coroutines.guava.asListenableFuture

/** @return Current [Deferred] as an [ApiFuture]. */
fun <T> Deferred<T>.asApiFuture(): ApiFuture<T> =
    ListenableFutureToApiFuture(this.asListenableFuture())

/** @return Current [ApiFuture] as a [Deferred]. */
fun <T> ApiFuture<T>.asDeferred(): Deferred<T> = ApiFutureToListenableFuture(this).asDeferred()

/** @return Awaited value from the [ApiFuture]. */
suspend fun <T> ApiFuture<T>.await(): T = asDeferred().await()

/** Runs [block] as a coroutine and returns its result as an [ApiFuture]. */
fun <T> CoroutineScope.apiFuture(block: suspend () -> T): ApiFuture<T> =
    async { block() }.asApiFuture()
