package com.apollographql.apollo.rx3;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloPrefetch;
import com.apollographql.apollo.ApolloQueryWatcher;
import com.apollographql.apollo.ApolloSubscriptionCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.cache.normalized.ApolloStoreOperation;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.internal.subscription.ApolloSubscriptionTerminatedException;
import com.apollographql.apollo.internal.util.Cancelable;
import org.jetbrains.annotations.NotNull;
import io.reactivex.rxjava3.annotations.CheckReturnValue;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.reactivex.rxjava3.core.CompletableOnSubscribe;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.Exceptions;
import static com.apollographql.apollo.api.internal.Utils.checkNotNull;

/**
 * The Rx3Apollo class provides methods for converting ApolloCall, ApolloPrefetch and ApolloWatcher types to RxJava 3
 * sources.
 */
public class Rx3Apollo {

  private Rx3Apollo() {
    throw new AssertionError("This class cannot be instantiated");
  }

  /**
   * Converts an {@link ApolloQueryWatcher} to an asynchronous Observable.
   *
   * @param watcher the ApolloQueryWatcher to convert.
   * @param <T>     the value type
   * @return the converted Observable
   * @throws NullPointerException if watcher == null
   */
  @NotNull
  @CheckReturnValue
  public static <T> Observable<Response<T>> from(@NotNull final ApolloQueryWatcher<T> watcher) {
    checkNotNull(watcher, "watcher == null");
    return Observable.create(new ObservableOnSubscribe<Response<T>>() {
      @Override public void subscribe(final ObservableEmitter<Response<T>> emitter) throws Exception {
        ApolloQueryWatcher<T> clone = watcher.clone();
        cancelOnObservableDisposed(emitter, clone);

        clone.enqueueAndWatch(new ApolloCall.Callback<T>() {
          @Override public void onResponse(@NotNull Response<T> response) {
            if (!emitter.isDisposed()) {
              emitter.onNext(response);
            }
          }

          @Override public void onFailure(@NotNull ApolloException e) {
            Exceptions.throwIfFatal(e);
            if (!emitter.isDisposed()) {
              emitter.onError(e);
            }
          }
        });
      }
    });
  }

  /**
   * Converts an {@link ApolloCall} to an {@link Observable}. The number of emissions this Observable will have is based
   * on the {@link com.apollographql.apollo.fetcher.ResponseFetcher} used with the call.
   *
   * @param call the ApolloCall to convert
   * @param <T>  the value type.
   * @return the converted Observable
   * @throws NullPointerException if originalCall == null
   */
  @NotNull
  @CheckReturnValue
  public static <T> Observable<Response<T>> from(@NotNull final ApolloCall<T> call) {
    checkNotNull(call, "call == null");

    return Observable.create(new ObservableOnSubscribe<Response<T>>() {
      @Override public void subscribe(final ObservableEmitter<Response<T>> emitter) throws Exception {
        ApolloCall<T> clone = call.clone();
        cancelOnObservableDisposed(emitter, clone);
        clone.enqueue(new ApolloCall.Callback<T>() {
          @Override public void onResponse(@NotNull Response<T> response) {
            if (!emitter.isDisposed()) {
              emitter.onNext(response);
            }
          }

          @Override public void onFailure(@NotNull ApolloException e) {
            Exceptions.throwIfFatal(e);
            if (!emitter.isDisposed()) {
              emitter.onError(e);
            }
          }

          @Override public void onStatusEvent(@NotNull ApolloCall.StatusEvent event) {
            if (event == ApolloCall.StatusEvent.COMPLETED && !emitter.isDisposed()) {
              emitter.onComplete();
            }
          }
        });
      }
    });
  }

  /**
   * Converts an {@link ApolloPrefetch} to a synchronous Completable
   *
   * @param prefetch the ApolloPrefetch to convert
   * @return the converted Completable
   * @throws NullPointerException if prefetch == null
   */
  @NotNull
  @CheckReturnValue
  public static Completable from(@NotNull final ApolloPrefetch prefetch) {
    checkNotNull(prefetch, "prefetch == null");

    return Completable.create(new CompletableOnSubscribe() {
      @Override public void subscribe(final CompletableEmitter emitter) {
        ApolloPrefetch clone = prefetch.clone();
        cancelOnCompletableDisposed(emitter, clone);
        clone.enqueue(new ApolloPrefetch.Callback() {
          @Override public void onSuccess() {
            if (!emitter.isDisposed()) {
              emitter.onComplete();
            }
          }

          @Override public void onFailure(@NotNull ApolloException e) {
            Exceptions.throwIfFatal(e);
            if (!emitter.isDisposed()) {
              emitter.onError(e);
            }
          }
        });
      }
    });
  }

  @NotNull
  @CheckReturnValue
  public static <T> Flowable<Response<T>> from(@NotNull ApolloSubscriptionCall<T> call) {
    return from(call, BackpressureStrategy.LATEST);
  }

  @NotNull
  @CheckReturnValue
  public static <T> Flowable<Response<T>> from(@NotNull final ApolloSubscriptionCall<T> call,
      @NotNull BackpressureStrategy backpressureStrategy) {
    checkNotNull(call, "originalCall == null");
    checkNotNull(backpressureStrategy, "backpressureStrategy == null");
    return Flowable.create(new FlowableOnSubscribe<Response<T>>() {
      @Override public void subscribe(final FlowableEmitter<Response<T>> emitter) throws Exception {
        ApolloSubscriptionCall<T> clone = call.clone();
        cancelOnFlowableDisposed(emitter, clone);
        clone.execute(
            new ApolloSubscriptionCall.Callback<T>() {
              @Override public void onResponse(@NotNull Response<T> response) {
                if (!emitter.isCancelled()) {
                  emitter.onNext(response);
                }
              }

              @Override public void onFailure(@NotNull ApolloException e) {
                Exceptions.throwIfFatal(e);
                if (!emitter.isCancelled()) {
                  emitter.onError(e);
                }
              }

              @Override public void onCompleted() {
                if (!emitter.isCancelled()) {
                  emitter.onComplete();
                }
              }

              @Override public void onTerminated() {
                onFailure(new ApolloSubscriptionTerminatedException("Subscription server unexpectedly terminated "
                    + "connection"));
              }

              @Override public void onConnected() {
              }
            }
        );
      }
    }, backpressureStrategy);
  }

  /**
   * Converts an {@link ApolloStoreOperation} to a Single.
   *
   * @param operation the ApolloStoreOperation to convert
   * @param <T>       the value type
   * @return the converted Single
   */
  @NotNull
  @CheckReturnValue
  public static <T> Single<T> from(@NotNull final ApolloStoreOperation<T> operation) {
    checkNotNull(operation, "operation == null");
    return Single.create(new SingleOnSubscribe<T>() {
      @Override
      public void subscribe(final SingleEmitter<T> emitter) {
        operation.enqueue(new ApolloStoreOperation.Callback<T>() {
          @Override
          public void onSuccess(T result) {
            if (!emitter.isDisposed()) {
              emitter.onSuccess(result);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            if (!emitter.isDisposed()) {
              emitter.onError(t);
            }
          }
        });
      }
    });
  }

  private static void cancelOnCompletableDisposed(CompletableEmitter emitter, final Cancelable cancelable) {
    emitter.setDisposable(getRx3Disposable(cancelable));
  }

  private static <T> void cancelOnObservableDisposed(ObservableEmitter<T> emitter, final Cancelable cancelable) {
    emitter.setDisposable(getRx3Disposable(cancelable));
  }

  private static <T> void cancelOnFlowableDisposed(FlowableEmitter<T> emitter, final Cancelable cancelable) {
    emitter.setDisposable(getRx3Disposable(cancelable));
  }

  private static Disposable getRx3Disposable(final Cancelable cancelable) {
    return new Disposable() {
      @Override public void dispose() {
        cancelable.cancel();
      }

      @Override public boolean isDisposed() {
        return cancelable.isCanceled();
      }
    };
  }
}
