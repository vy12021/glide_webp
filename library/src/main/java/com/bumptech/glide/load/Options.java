package com.bumptech.glide.load;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.util.CachedHashCodeLinkedHashMap;

import java.security.MessageDigest;
import java.util.Map;

/** A set of {@link Option Options} to apply to in memory and disk cache keys. */
public final class Options implements Key {
  private final Map<Option<?>, Object> values = new CachedHashCodeLinkedHashMap<>();

  public void putAll(@NonNull Options other) {
    values.putAll(other.values);
  }

  @NonNull
  public <T> Options set(@NonNull Option<T> option, @NonNull T value) {
    values.put(option, value);
    return this;
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public <T> T get(@NonNull Option<T> option) {
    return values.containsKey(option) ? (T) values.get(option) : option.getDefaultValue();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Options) {
      Options other = (Options) o;
      return values.equals(other.values);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }

  @Override
  public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
    for (Map.Entry<Option<?>, Object> entry : values.entrySet()) {
      updateDiskCacheKey(entry.getKey(), entry.getValue(), messageDigest);
    }
  }

  @Override
  public String toString() {
    return "Options{" + "values=" + values + '}';
  }

  @SuppressWarnings("unchecked")
  private static <T> void updateDiskCacheKey(
      @NonNull Option<T> option, @NonNull Object value, @NonNull MessageDigest md) {
    option.update((T) value, md);
  }
}
