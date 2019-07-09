package com.apollographql.apollo;

import com.apollographql.apollo.api.cache.http.HttpCacheRecord;
import com.apollographql.apollo.api.cache.http.HttpCacheRecordEditor;
import com.apollographql.apollo.api.cache.http.HttpCacheStore;
import com.apollographql.apollo.cache.http.internal.DiskLruCache;
import com.apollographql.apollo.cache.http.internal.FileSystem;
import java.io.File;
import java.io.IOException;
import okio.Buffer;
import okio.Sink;
import okio.Source;
import okio.Timeout;
import org.jetbrains.annotations.NotNull;

class FaultyHttpCacheStore implements HttpCacheStore {
  private static final int VERSION = 99991;
  private static final int ENTRY_HEADERS = 0;
  private static final int ENTRY_BODY = 1;
  private static final int ENTRY_COUNT = 2;

  private DiskLruCache cache;
  final FaultySource faultySource = new FaultySource();
  final FaultySink faultySink = new FaultySink();
  FailStrategy failStrategy;

  FaultyHttpCacheStore(FileSystem fileSystem) {
    this.cache = DiskLruCache.create(fileSystem, new File("/cache/"), VERSION, ENTRY_COUNT, Integer.MAX_VALUE);
  }

  @Override public HttpCacheRecord cacheRecord(@NotNull String cacheKey) throws IOException {
    final DiskLruCache.Snapshot snapshot = cache.get(cacheKey);
    if (snapshot == null) {
      return null;
    }

    return new HttpCacheRecord() {
      @NotNull @Override public Source headerSource() {
        if (failStrategy == FailStrategy.FAIL_HEADER_READ) {
          return faultySource;
        } else {
          return snapshot.getSource(ENTRY_HEADERS);
        }
      }

      @NotNull @Override public Source bodySource() {
        if (failStrategy == FailStrategy.FAIL_BODY_READ) {
          return faultySource;
        } else {
          return snapshot.getSource(ENTRY_BODY);
        }
      }

      @Override public void close() {
        snapshot.close();
      }
    };
  }

  @Override public HttpCacheRecordEditor cacheRecordEditor(@NotNull String cacheKey) throws IOException {
    final DiskLruCache.Editor editor = cache.edit(cacheKey);
    if (editor == null) {
      return null;
    }

    return new HttpCacheRecordEditor() {
      @NotNull @Override public Sink headerSink() {
        if (failStrategy == FailStrategy.FAIL_HEADER_WRITE) {
          return faultySink;
        } else {
          return editor.newSink(ENTRY_HEADERS);
        }
      }

      @NotNull @Override public Sink bodySink() {
        if (failStrategy == FailStrategy.FAIL_BODY_WRITE) {
          return faultySink;
        } else {
          return editor.newSink(ENTRY_BODY);
        }
      }

      @Override public void abort() throws IOException {
        editor.abort();
      }

      @Override public void commit() throws IOException {
        editor.commit();
      }
    };
  }

  @Override public void delete() throws IOException {
    cache.delete();
  }

  @Override public void remove(@NotNull String cacheKey) throws IOException {
    cache.remove(cacheKey);
  }

  void failStrategy(FailStrategy failStrategy) {
    this.failStrategy = failStrategy;
  }

  enum FailStrategy {
    FAIL_HEADER_READ,
    FAIL_BODY_READ,
    FAIL_HEADER_WRITE,
    FAIL_BODY_WRITE
  }

  private static class FaultySource implements Source {
    @Override public long read(Buffer sink, long byteCount) throws IOException {
      throw new IOException("failed to read");
    }

    @Override public Timeout timeout() {
      return new Timeout();
    }

    @Override public void close() throws IOException {

    }
  }

  private static class FaultySink implements Sink {
    @Override public void write(Buffer source, long byteCount) throws IOException {
      throw new IOException("failed to write");
    }

    @Override public void flush() throws IOException {
    }

    @Override public Timeout timeout() {
      return new Timeout();
    }

    @Override public void close() throws IOException {
    }
  }
}
