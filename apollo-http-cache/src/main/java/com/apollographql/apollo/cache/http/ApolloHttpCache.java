package com.apollographql.apollo.cache.http;

import com.apollographql.apollo.Logger;
import com.apollographql.apollo.api.cache.http.HttpCache;
import com.apollographql.apollo.api.cache.http.HttpCacheRecord;
import com.apollographql.apollo.api.cache.http.HttpCacheRecordEditor;
import com.apollographql.apollo.api.cache.http.HttpCacheStore;
import com.apollographql.apollo.api.internal.ApolloLogger;
import okhttp3.Interceptor;
import okhttp3.Response;
import okio.ForwardingSource;
import okio.Sink;
import okio.Source;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static com.apollographql.apollo.api.internal.Utils.checkNotNull;
import static com.apollographql.apollo.cache.http.Utils.copyResponseBody;
import static com.apollographql.apollo.cache.http.Utils.skipStoreResponse;

@SuppressWarnings("WeakerAccess")
public final class ApolloHttpCache implements HttpCache {

  private final HttpCacheStore cacheStore;
  private final ApolloLogger logger;

  public ApolloHttpCache(@NotNull final HttpCacheStore cacheStore) {
    this(cacheStore, null);
  }

  public ApolloHttpCache(@NotNull final HttpCacheStore cacheStore, @Nullable final Logger logger) {
    this.cacheStore = checkNotNull(cacheStore, "cacheStore == null");
    this.logger = new ApolloLogger(logger);
  }

  @Override public void clear() {
    try {
      cacheStore.delete();
    } catch (IOException e) {
      logger.e(e, "Failed to clear http cache");
    }
  }

  @Override public void remove(@NotNull String cacheKey) throws IOException {
    cacheStore.remove(cacheKey);
  }

  @Override public void removeQuietly(@NotNull String cacheKey) {
    try {
      remove(cacheKey);
    } catch (Exception ignore) {
      logger.w(ignore, "Failed to remove cached record for key: %s", cacheKey);
    }
  }

  @Override public Response read(@NotNull final String cacheKey) {
    return read(cacheKey, false);
  }

  @Override public Response read(@NotNull final String cacheKey, final boolean expireAfterRead) {
    HttpCacheRecord responseCacheRecord = null;
    try {
      responseCacheRecord = cacheStore.cacheRecord(cacheKey);
      if (responseCacheRecord == null) {
        return null;
      }

      final HttpCacheRecord cacheRecord = responseCacheRecord;
      Source cacheResponseSource = new ForwardingSource(responseCacheRecord.bodySource()) {
        @Override
        public void close() throws IOException {
          super.close();
          closeQuietly(cacheRecord);
          if (expireAfterRead) {
            removeQuietly(cacheKey);
          }
        }
      };

      Response response = new ResponseHeaderRecord(responseCacheRecord.headerSource()).response();
      String contentType = response.header("Content-Type");
      String contentLength = response.header("Content-Length");
      return response.newBuilder()
          .addHeader(FROM_CACHE, "true")
          .body(new CacheResponseBody(cacheResponseSource, contentType, contentLength))
          .build();
    } catch (Exception e) {
      closeQuietly(responseCacheRecord);
      logger.e(e, "Failed to read http cache entry for key: %s", cacheKey);
      return null;
    }
  }

  @Override public Interceptor interceptor() {
    return new HttpCacheInterceptor(this, logger);
  }

  Response cacheProxy(@NotNull Response response, @NotNull String cacheKey) {
    if (skipStoreResponse(response.request())) {
      return response;
    }

    HttpCacheRecordEditor cacheRecordEditor = null;
    try {
      cacheRecordEditor = cacheStore.cacheRecordEditor(cacheKey);
      if (cacheRecordEditor != null) {
        Sink headerSink = cacheRecordEditor.headerSink();
        try {
          new ResponseHeaderRecord(response).writeTo(headerSink);
        } finally {
          closeQuietly(headerSink);
        }

        return response.newBuilder()
            .body(new ResponseBodyProxy(cacheRecordEditor, response, logger))
            .build();
      }
    } catch (Exception e) {
      abortQuietly(cacheRecordEditor);
      logger.e(e, "Failed to proxy http response for key: %s", cacheKey);
    }
    return response;
  }

  void write(@NotNull Response response, @NotNull String cacheKey) {
    HttpCacheRecordEditor cacheRecordEditor = null;
    try {
      cacheRecordEditor = cacheStore.cacheRecordEditor(cacheKey);
      if (cacheRecordEditor != null) {
        Sink headerSink = cacheRecordEditor.headerSink();
        try {
          new ResponseHeaderRecord(response).writeTo(headerSink);
        } finally {
          closeQuietly(headerSink);
        }

        Sink bodySink = cacheRecordEditor.bodySink();
        try {
          copyResponseBody(response, bodySink);
        } finally {
          closeQuietly(bodySink);
        }

        cacheRecordEditor.commit();
      }
    } catch (Exception e) {
      abortQuietly(cacheRecordEditor);
      logger.e(e, "Failed to cache http response for key: %s", cacheKey);
    }
  }

  void closeQuietly(HttpCacheRecord cacheRecord) {
    try {
      if (cacheRecord != null) {
        cacheRecord.close();
      }
    } catch (Exception ignore) {
      logger.w(ignore, "Failed to close cache record");
    }
  }

  private void abortQuietly(HttpCacheRecordEditor cacheRecordEditor) {
    try {
      if (cacheRecordEditor != null) {
        cacheRecordEditor.abort();
      }
    } catch (Exception ignore) {
      logger.w(ignore, "Failed to abort cache record edit");
    }
  }

  private void closeQuietly(Sink sink) {
    try {
      sink.close();
    } catch (Exception ignore) {
      logger.w(ignore, "Failed to close sink");
    }
  }
}
