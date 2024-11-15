/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.installer.factories.configuration.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A sorted dictionary is a view on an existing {@link Dictionary} that is sorted by its keys
 * in natural ordering.
 * This is just a view on top of the delegate dictionary. All write operations modify
 * the underlying delegate dictionary.
 * @param <K> the type of the keys
 * @param <V> the type of the values
 */
public class SortedDictionary<K, V> extends Dictionary<K, V> {

    private final Dictionary<K, V> delegate;

    public SortedDictionary(Dictionary<K, V> delegate) {
        this.delegate = delegate;
    }

    public int size() {
        return delegate.size();
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public Enumeration<K> keys() {
        return sortedEnumeration(delegate.keys());
    }

    public Enumeration<V> elements() {
        // this needs to be sorted by keys
        Enumeration<K> sortedKeys = keys();
        Collection<V> sortedValues = new ArrayList<V>();
        while (sortedKeys.hasMoreElements()) {
            K key = sortedKeys.nextElement();
            sortedValues.add(delegate.get(key));
        }
        return Collections.enumeration(sortedValues);
    }

    public V get(Object key) {
        return delegate.get(key);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SortedDictionary other = (SortedDictionary) obj;
        return Objects.equals(delegate, other.delegate);
    }

    public V put(K key, V value) {
        return delegate.put(key, value);
    }

    public V remove(Object key) {
        return delegate.remove(key);
    }

    public String toString() {
        return delegate.toString();
    }

    /**
     * Returns an sorted enumeration of the given enumeration, sorted according to the natural ordering of the elements.
     * For Strings, this is the lexicographic order.
     *
     * @param enumeration the enumeration to sort
     * @return the sorted enumeration
     */
    private static <T> Enumeration<T> sortedEnumeration(Enumeration<T> enumeration) {
        SortedSet<T> sortedSet = new TreeSet<>();
        while (enumeration.hasMoreElements()) {
            sortedSet.add(enumeration.nextElement());
        }
        return Collections.enumeration(sortedSet);
    }
}
