package com.bumptech.glide.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An {@link LinkedHashMap} that caches its hashCode to support efficient lookup.
 *
 * @param <K> the key type.
 * @param <V> the value type.
 */
// We're overriding hashcode, but not in a way that changes the output, so we don't need to
// override equals.
@SuppressWarnings("PMD.OverrideBothEqualsAndHashcode")
public final class CachedHashCodeLinkedHashMap<K, V> extends LinkedHashMap<K, V> {
  private static final long serialVersionUID = 6811721733252226365L;

  private int hashCode;

  @Override
  public void clear() {
    hashCode = 0;
    super.clear();
  }

  @Override
  public V put(K key, V value) {
    hashCode = 0;
    return super.put(key, value);
  }

  @Override
  public void putAll(@NonNull Map<? extends K, ? extends V> m) {
    hashCode = 0;
    super.putAll(m);
  }

  @Nullable
  @Override
  public V remove(@Nullable Object key) {
    hashCode = 0;
    return super.remove(key);
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = super.hashCode();
    }
    return hashCode;
  }
}
