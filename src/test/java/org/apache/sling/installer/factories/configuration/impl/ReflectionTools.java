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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Reflection utilities to facilitate testing
 */
public class ReflectionTools {

    private ReflectionTools() {
        // hide the public constructor
    }

    public static <T> T invokeMethodWithReflection(
            Object obj, String methodName, Class<?>[] parameterTypes, Class<T> expectedType, Object[] methodArgs) {
        Object result = null;
        try {
            Class<?> clazz = obj.getClass();
            Method method = null;
            do {
                try { // NOSONAR
                    method = clazz.getDeclaredMethod(methodName, parameterTypes);
                } catch (NoSuchMethodException nsfe) {
                    clazz = clazz.getSuperclass();
                }
            } while (method == null && clazz != null);
            if (method != null) {
                if (Modifier.isStatic(method.getModifiers())) {
                    if (!method.canAccess(null)) {
                        method.setAccessible(true);
                    }
                    result = method.invoke(null, methodArgs);
                } else {
                    if (!method.canAccess(obj)) {
                        method.setAccessible(true);
                    }
                    result = method.invoke(obj, methodArgs);
                }
            } else {
                fail("Failed to find method via reflection: " + methodName);
            }
        } catch (IllegalArgumentException | IllegalAccessException | SecurityException e) {
            fail("Failed to get method via reflection. Reason: " + e.getMessage());
        } catch (InvocationTargetException e) {
            fail("Failed to invoke method via reflection. Reason: " + e.getMessage());
        }
        return expectedType == null ? null : expectedType.cast(result);
    }
}
