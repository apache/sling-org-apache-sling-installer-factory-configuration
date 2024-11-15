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

import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SortedDictionaryTest {

    @Test
    public void testKeysAndElements() {
        Dictionary<String, Object> dictionary = new Hashtable<>();
        dictionary.put("Z", "z");
        dictionary.put("A", "a");
        dictionary.put("B", "b");
        SortedDictionary<String, Object> sortedDictionary = new SortedDictionary<>(dictionary);
        assertArrayEquals(
                new String[] {"A", "B", "Z"},
                Collections.list(sortedDictionary.keys()).toArray(new String[] {}));
        assertArrayEquals(
                new String[] {"a", "b", "z"},
                Collections.list(sortedDictionary.elements()).toArray(new String[] {}));
    }

    @Test
    public void testPutGetRemove() {
        Dictionary<String, Object> dictionary = new Hashtable<>();
        Dictionary<String, Object> sortedDictionary = new SortedDictionary<>(dictionary);
        sortedDictionary.put("foo", "bar");
        assertEquals("bar", sortedDictionary.get("foo"));
        assertEquals("bar", dictionary.get("foo"));
        sortedDictionary.remove("foo");
        assertNull(sortedDictionary.get("foo"));
        assertNull(dictionary.get("foo"));
    }

    @Test
    public void testDelegatedGetters() {
        Dictionary<String, Object> dictionary = new Hashtable<>();
        dictionary.put("foo", "bar");
        dictionary.put("bar", "baz");
        Dictionary<String, Object> sortedDictionary = new SortedDictionary<>(dictionary);
        assertEquals(dictionary.size(), sortedDictionary.size());
        assertEquals(dictionary.isEmpty(), sortedDictionary.isEmpty());
        assertEquals(dictionary.toString(), sortedDictionary.toString());
    }

    @Test
    public void testEqualsAndHashcode() {
        Dictionary<String, Object> dictionary = new Hashtable<>();
        dictionary.put("foo", "bar");
        dictionary.put("bar", "baz");
        Dictionary<String, Object> sortedDictionary = new SortedDictionary<>(dictionary);
        Dictionary<String, Object> dictionary2 = new Hashtable<>();
        dictionary2.put("foo", "bar");
        dictionary2.put("bar", "baz");
        Dictionary<String, Object> sortedDictionary2 = new SortedDictionary<>(dictionary2);
        assertEquals(sortedDictionary, sortedDictionary2);
        assertEquals(sortedDictionary.hashCode(), sortedDictionary2.hashCode());
    }
}
