package com.apollographql.apollo.cache.normalized.internal;

import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.ResponseField;
import com.apollographql.apollo.api.internal.ResolveDelegate;
import com.apollographql.apollo.cache.normalized.CacheKey;
import com.apollographql.apollo.cache.normalized.CacheKeyResolver;
import com.apollographql.apollo.cache.normalized.CacheReference;
import com.apollographql.apollo.cache.normalized.Record;
import com.apollographql.apollo.cache.normalized.RecordSet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ResponseNormalizer<R> implements ResolveDelegate<R> {
  private SimpleStack<List<String>> pathStack;
  private SimpleStack<Record> recordStack;
  private SimpleStack<Object> valueStack;
  private List<String> path;
  private Record.Builder currentRecordBuilder;

  private RecordSet recordSet = new RecordSet();
  private Set<String> dependentKeys = Collections.emptySet();

  public Collection<Record> records() {
    return recordSet.allRecords();
  }

  public Set<String> dependentKeys() {
    return dependentKeys;
  }

  @Override public void willResolveRootQuery(Operation operation) {
    willResolveRecord(CacheKeyResolver.rootKeyForOperation(operation));
  }

  @Override public void willResolve(ResponseField field, Operation.Variables variables, @Nullable Object value) {
    String key = cacheKeyBuilder().build(field, variables);
    path.add(key);
  }

  @Override public void didResolve(ResponseField field, Operation.Variables variables) {
    path.remove(path.size() - 1);
    Object value = valueStack.pop();
    String cacheKey = cacheKeyBuilder().build(field, variables);
    String dependentKey = currentRecordBuilder.getKey() + "." + cacheKey;
    dependentKeys.add(dependentKey);
    currentRecordBuilder.addField(cacheKey, value);

    if (recordStack.isEmpty()) {
      recordSet.merge(currentRecordBuilder.build());
    }
  }

  @Override public void didResolveScalar(@Nullable Object value) {
    valueStack.push(value);
  }

  @Override public void willResolveObject(ResponseField field, @Nullable R objectSource) {
    pathStack.push(path);

    CacheKey cacheKey = objectSource != null ? resolveCacheKey(field, objectSource) : CacheKey.NO_KEY;
    String cacheKeyValue = cacheKey.key();
    if (cacheKey.equals(CacheKey.NO_KEY)) {
      cacheKeyValue = pathToString();
    } else {
      path = new ArrayList<>();
      path.add(cacheKeyValue);
    }
    recordStack.push(currentRecordBuilder.build());
    currentRecordBuilder = Record.builder(cacheKeyValue);
  }

  @Override public void didResolveObject(ResponseField field, @Nullable R objectSource) {
    path = pathStack.pop();
    if (objectSource != null) {
      Record completedRecord = currentRecordBuilder.build();
      valueStack.push(new CacheReference(completedRecord.key()));
      dependentKeys.add(completedRecord.key());
      recordSet.merge(completedRecord);
    }
    currentRecordBuilder = recordStack.pop().toBuilder();
  }

  @Override public void didResolveList(List array) {
    List<Object> parsedArray = new ArrayList<>(array.size());
    for (int i = 0, size = array.size(); i < size; i++) {
      parsedArray.add(0, valueStack.pop());
    }
    valueStack.push(parsedArray);
  }

  @Override public void willResolveElement(int atIndex) {
    path.add(Integer.toString(atIndex));
  }

  @Override public void didResolveElement(int atIndex) {
    path.remove(path.size() - 1);
  }

  @Override public void didResolveNull() {
    valueStack.push(null);
  }

  @NotNull public abstract CacheKey resolveCacheKey(@NotNull ResponseField field, @NotNull R record);

  @NotNull public abstract CacheKeyBuilder cacheKeyBuilder();

  public void willResolveRecord(CacheKey cacheKey) {
    pathStack = new SimpleStack<>();
    recordStack = new SimpleStack<>();
    valueStack = new SimpleStack<>();
    dependentKeys = new HashSet<>();

    path = new ArrayList<>();
    currentRecordBuilder = Record.builder(cacheKey.key());
    recordSet = new RecordSet();
  }

  private String pathToString() {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0, size = path.size(); i < size; i++) {
      String pathPiece = path.get(i);
      stringBuilder.append(pathPiece);
      if (i < size - 1) {
        stringBuilder.append(".");
      }
    }
    return stringBuilder.toString();
  }

  @SuppressWarnings("unchecked") public static final ResponseNormalizer NO_OP_NORMALIZER = new ResponseNormalizer() {
    @Override public void willResolveRootQuery(Operation operation) {
    }

    @Override public void willResolve(ResponseField field, Operation.Variables variables, @Nullable Object value) {
    }

    @Override public void didResolve(ResponseField field, Operation.Variables variables) {
    }

    @Override public void didResolveScalar(Object value) {
    }

    @Override public void willResolveObject(ResponseField field, @Nullable Object objectSource) {
    }

    @Override public void didResolveObject(ResponseField field, @Nullable Object objectSource) {
    }

    @Override public void didResolveList(List array) {
    }

    @Override public void willResolveElement(int atIndex) {
    }

    @Override public void didResolveElement(int atIndex) {
    }

    @Override public void didResolveNull() {
    }

    @Override public Collection<Record> records() {
      return Collections.emptyList();
    }

    @Override public Set<String> dependentKeys() {
      return Collections.emptySet();
    }

    @NotNull @Override public CacheKey resolveCacheKey(@NotNull ResponseField field, @NotNull Object record) {
      return CacheKey.NO_KEY;
    }

    @NotNull @Override public CacheKeyBuilder cacheKeyBuilder() {
      return new CacheKeyBuilder() {
        @NotNull @Override public String build(@NotNull ResponseField field, @NotNull Operation.Variables variables) {
          return CacheKey.NO_KEY.key();
        }
      };
    }
  };
}
