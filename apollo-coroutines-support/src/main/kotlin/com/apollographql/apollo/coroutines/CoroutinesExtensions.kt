package com.apollographql.apollo.coroutines

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloPrefetch
import com.apollographql.apollo.ApolloQueryWatcher
import com.apollographql.apollo.ApolloSubscriptionCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume


/**
 * Converts an [ApolloCall] to an [Flow].
 *
 * @param <T>  the value type.
 * @return a flow which emits [Responses<T>]
 */
@ExperimentalCoroutinesApi
fun <T> ApolloCall<T>.toFlow(): Flow<Response<T>> = callbackFlow {
  val clone = clone()
  clone.enqueue(
      object : ApolloCall.Callback<T>() {
        override fun onResponse(response: Response<T>) {
          runCatching {
            offer(response)
          }
        }

        override fun onFailure(e: ApolloException) {
          close(e)
        }

        override fun onStatusEvent(event: ApolloCall.StatusEvent) {
          if (event == ApolloCall.StatusEvent.COMPLETED) {
            close()
          }
        }
      }
  )
  awaitClose { clone.cancel() }
}

/**
 * Converts an [ApolloQueryWatcher] to an [Flow].
 *
 * @param <T>  the value type.
 * @return a flow which emits [Responses<T>]
 */
@ExperimentalCoroutinesApi
fun <T> ApolloQueryWatcher<T>.toFlow(): Flow<Response<T>> = callbackFlow {
  val clone = clone()
  clone.enqueueAndWatch(
      object : ApolloCall.Callback<T>() {
        override fun onResponse(response: Response<T>) {
          runCatching {
            offer(response)
          }
        }

        override fun onFailure(e: ApolloException) {
          close(e)
        }
      }
  )
  awaitClose { clone.cancel() }
}


/**
 * Suspends the [ApolloCall] until it completes and returns the value on success or throws an exception on failure.
 * The [ApolloCall] is cancelled when the coroutine running the operation is cancelled as well.
 *
 * This is a convenience method that will only return the first value emitted. If more than one
 * response is required, for an example to retrieve cached and network response, use [toFlow] instead.
 *
 * @param <T>  the value type.
 * @return the response on success.
 * @throws ApolloException on failure.
 */
suspend fun <T> ApolloCall<T>.await(): Response<T> = suspendCancellableCoroutine { cont ->

  cont.invokeOnCancellation {
    cancel()
  }

  enqueue(object : ApolloCall.Callback<T>() {

    private val wasCalled = AtomicBoolean(false)

    override fun onResponse(response: Response<T>) {
      if (!wasCalled.getAndSet(true)) {
        cont.resume(response)
      }
    }

    override fun onFailure(e: ApolloException) {
      if (!wasCalled.getAndSet(true)) {
        cont.resumeWithException(e)
      }
    }
  })
}

/**
 * Converts an [ApolloCall] to an [Deferred]. This is a convenience method that will only return the first value emitted.
 * If the more than one response is required, for an example to retrieve cached and network response, use [toFlow] instead.
 *
 * @param <T>  the value type.
 * @return the deferred
 */
@Deprecated("Use await() instead.")
fun <T> ApolloCall<T>.toDeferred(): Deferred<Response<T>> {
  val deferred = CompletableDeferred<Response<T>>()

  deferred.invokeOnCompletion {
    if (deferred.isCancelled) {
      cancel()
    }
  }
  enqueue(object : ApolloCall.Callback<T>() {
    override fun onResponse(response: Response<T>) {
      if (deferred.isActive) {
        deferred.complete(response)
      }
    }

    override fun onFailure(e: ApolloException) {
      if (deferred.isActive) {
        deferred.completeExceptionally(e)
      }
    }
  })

  return deferred
}

/**
 * Converts an [ApolloSubscriptionCall] to an [Flow].
 *
 * @param <T>  the value type.
 * @return a flow which emits [Responses<T>]
 */
@ExperimentalCoroutinesApi
fun <T> ApolloSubscriptionCall<T>.toFlow(): Flow<Response<T>> = callbackFlow {
  val clone = clone()
  clone.execute(
      object : ApolloSubscriptionCall.Callback<T> {
        override fun onConnected() {
        }

        override fun onResponse(response: Response<T>) {
          runCatching {
            channel.offer(response)
          }
        }

        override fun onFailure(e: ApolloException) {
          channel.close(e)
        }

        override fun onCompleted() {
          channel.close()
        }

        override fun onTerminated() {
          channel.close()
        }
      }
  )
  awaitClose { clone.cancel() }
}

/**
 * Suspends the [ApolloPrefetch] until it completes and returns the value on success or throws an exception on failure.
 * The [ApolloPrefetch] is cancelled when the coroutine running the operation is cancelled as well.
 *
 * @param <T>  the value type.
 * @return the response on success.
 * @throws ApolloException on failure.
 */
suspend fun ApolloPrefetch.await(): Unit = suspendCancellableCoroutine { cont ->

  cont.invokeOnCancellation {
    cancel()
  }

  enqueue(object : ApolloPrefetch.Callback() {

    private val wasCalled = AtomicBoolean(false)

    override fun onSuccess() {
      if (!wasCalled.getAndSet(true)) {
        cont.resume(Unit)
      }
    }

    override fun onFailure(e: ApolloException) {
      if (!wasCalled.getAndSet(true)) {
        cont.resumeWithException(e)
      }
    }
  })
}

/**
 * Converts an [ApolloPrefetch] to [Job].
 *
 * @param <T>  the value type.
 * @return the converted job
 */
@Deprecated("Use await() instead.")
fun ApolloPrefetch.toJob(): Job {
  val deferred = CompletableDeferred<Unit>()

  deferred.invokeOnCompletion {
    if (deferred.isCancelled) {
      cancel()
    }
  }

  enqueue(object : ApolloPrefetch.Callback() {
    override fun onSuccess() {
      if (deferred.isActive) {
        deferred.complete(Unit)
      }
    }

    override fun onFailure(e: ApolloException) {
      if (deferred.isActive) {
        deferred.completeExceptionally(e)
      }
    }
  })

  return deferred
}

