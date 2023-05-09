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
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;

import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.factories.configuration.ConfigurationMerger;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component
public class ConfigurationMergerImpl implements ConfigurationMerger {

    private final InfoProvider infoProvider;

    @Activate
    public ConfigurationMergerImpl(@Reference InfoProvider infoProvider) {
        this.infoProvider = infoProvider;
    }

    @Override
    public void removeDefaultProperties(@NotNull final String pid, @NotNull final Dictionary<String, Object> dict) {
        if ( Activator.MERGE_SCHEMES != null ) {
            final Dictionary<String, Object> defaultProps = getDefaultProperties(pid);
            if ( defaultProps != null ) {
                final Enumeration<String> keyEnum = defaultProps.keys();
                while ( keyEnum.hasMoreElements() ) {
                    final String key = keyEnum.nextElement();
                    final Object value = defaultProps.get(key);

                    final Object newValue = dict.get(key);
                    if ( newValue != null && ConfigUtil.isSameValue(newValue, value)) {
                        dict.remove(key);
                    }
                }
            }
        }
    }

    @Override
    public Dictionary<String, Object> getDefaultProperties(@NotNull final String pid) {
        if ( Activator.MERGE_SCHEMES != null ) {
            final List<Dictionary<String, Object>> propertiesList = new ArrayList<>();
            final String entityId = InstallableResource.TYPE_CONFIG.concat(":").concat(pid);
            boolean done = false;
            for(final ResourceGroup group : infoProvider.getInstallationState().getInstalledResources()) {
                for(final Resource rsrc : group.getResources()) {
                    if ( rsrc.getEntityId().equals(entityId) ) {
                        done = true;
                        if ( Activator.MERGE_SCHEMES.contains(rsrc.getScheme()) ) {
                            propertiesList.add(rsrc.getDictionary());
                        }
                    }
                }
                if ( done ) {
                    break;
                }
            }
            if ( !propertiesList.isEmpty() ) {
                final Dictionary<String, Object> defaultProps = ConfigUtil.mergeReverseOrder(propertiesList);
                return defaultProps;
            }
        }
        return null;
    }
}
