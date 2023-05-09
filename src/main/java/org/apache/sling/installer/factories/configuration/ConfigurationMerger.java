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
package org.apache.sling.installer.factories.configuration;

import java.util.Dictionary;

import org.jetbrains.annotations.NotNull;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Exposes helper methods to identify those configuration properties which are set through installer resources having a configured merge scheme.
 * Those configuration properties are referred to as default properties. 
 * Note though that both OSGi metatype and OSGi declarative services define default properties as well which are not meant here.
 * @see <a href="https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#merging-of-configurations">Merging of Configurations</a>
 */
@ProviderType
public interface ConfigurationMerger {

    /**
     * Modifies the given dictionary so that all properties which have values equal to one 
     * of the same named property values provided by the inherited configurations are removed.
     * @param pid the PID of the configuration
     * @param dict the configuration properties to modify
     */
    void removeDefaultProperties(@NotNull final String pid, @NotNull final Dictionary<String, Object> dict);

    /**
     * Returns all properties for the given PID which are set through any of the resource with the configured merge schemes.
     * @param pid the PID of the configuration
     * @return the properties set through specific installer resources in a mutable dictionary
     */
    Dictionary<String, Object> getDefaultProperties(@NotNull final String pid);

}
