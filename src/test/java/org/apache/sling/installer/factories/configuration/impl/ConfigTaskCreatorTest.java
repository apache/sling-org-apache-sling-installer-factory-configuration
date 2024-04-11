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


import org.apache.sling.installer.api.ResourceChangeListener;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.factories.configuration.ConfigurationConstants;
import org.junit.Test;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.apache.sling.installer.api.InstallableResource.RESOURCE_URI_HINT;
import static org.apache.sling.installer.api.InstallableResource.TYPE_CONFIG;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.osgi.framework.Constants.SERVICE_PID;
import static org.osgi.service.cm.ConfigurationAdmin.SERVICE_FACTORYPID;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ConfigTaskCreatorTest {

    @Test
    public void testDeleteEventWithDifferentPidAndFactoryPid() {
        final ConfigurationAdmin ca = mock(ConfigurationAdmin.class);
        final ServiceReference sr = mock(ServiceReference.class);
        final ResourceChangeListener rcl = mock(ResourceChangeListener.class);
        final InfoProvider ip = mock(InfoProvider.class);
        final ConfigurationEvent ce = new ConfigurationEvent(sr, 2, "a.b.c", "c1");
        final ConfigTaskCreator ctc = new ConfigTaskCreator(rcl, ca, ip);
        ctc.configurationEvent(ce);
        verify(rcl, times(1)).resourceRemoved(TYPE_CONFIG, "c1");
    }

    @Test
    public void testDeleteEventWhenPidStartsWithFactoryPid() {
        final ConfigurationAdmin ca = mock(ConfigurationAdmin.class);
        final ServiceReference sr = mock(ServiceReference.class);
        final ResourceChangeListener rcl = mock(ResourceChangeListener.class);
        final InfoProvider ip = mock(InfoProvider.class);
        final ConfigurationEvent ce = new ConfigurationEvent(sr, 2, "a.b.c", "a.b.c.c1");
        final ConfigTaskCreator ctc = new ConfigTaskCreator(rcl, ca, ip);
        ctc.configurationEvent(ce);
        verify(rcl, times(1)).resourceRemoved(TYPE_CONFIG, "a.b.c~c1");
    }

    @Test
    public void testDeleteEventWithNullFactoryPid() {
        final ConfigurationAdmin ca = mock(ConfigurationAdmin.class);
        final ServiceReference sr = mock(ServiceReference.class);
        final ResourceChangeListener rcl = mock(ResourceChangeListener.class);
        final InfoProvider ip = mock(InfoProvider.class);
        final ConfigurationEvent ce = new ConfigurationEvent(sr, 2, null, "a.b.c.c1");
        final ConfigTaskCreator ctc = new ConfigTaskCreator(rcl, ca, ip);
        ctc.configurationEvent(ce);
        verify(rcl, times(1)).resourceRemoved(TYPE_CONFIG, "a.b.c.c1");
    }

    @Test
    public void testUpdateEventWithDifferentPidAndFactoryPid() throws InvalidSyntaxException, IOException {
        final ConfigurationAdmin ca = mock(ConfigurationAdmin.class);
        final Configuration c = mock(Configuration.class);
        final Dictionary d = new Hashtable();
        d.put(ConfigurationConstants.PROPERTY_PERSISTENCE, "true");
        when(c.getProperties()).thenReturn(d);
        when(ca.listConfigurations(anyString())).thenReturn(new Configuration[]{c});
        final ServiceReference sr = mock(ServiceReference.class);
        final ResourceChangeListener rcl = mock(ResourceChangeListener.class);
        final InfoProvider ip = mock(InfoProvider.class);
        final ConfigurationEvent ce = new ConfigurationEvent(sr, 1, "a.b.c", "c1");
        final ConfigTaskCreator ctc = new ConfigTaskCreator(rcl, ca, ip);
        ctc.configurationEvent(ce);

        // create attributes
        final Map<String, Object> attr = new HashMap<>();
        attr.put(SERVICE_PID, "c1");
        attr.put(SERVICE_FACTORYPID, "a.b.c");
        attr.put(RESOURCE_URI_HINT, "c1");

        verify(rcl, times(1)).resourceAddedOrUpdated(eq(TYPE_CONFIG), eq("c1"), isNull(), eq(d), eq(attr));
    }

    @Test
    public void testUpdateEventWhenPidStartsWithFactoryPid() throws InvalidSyntaxException, IOException {
        final ConfigurationAdmin ca = mock(ConfigurationAdmin.class);
        final Configuration c = mock(Configuration.class);
        final Dictionary d = new Hashtable();
        d.put(ConfigurationConstants.PROPERTY_PERSISTENCE, "true");
        when(c.getProperties()).thenReturn(d);
        when(ca.listConfigurations(anyString())).thenReturn(new Configuration[]{c});
        final ServiceReference sr = mock(ServiceReference.class);
        final ResourceChangeListener rcl = mock(ResourceChangeListener.class);
        final InfoProvider ip = mock(InfoProvider.class);
        final ConfigurationEvent ce = new ConfigurationEvent(sr, 1, "a.b.c", "a.b.c.c1");
        final ConfigTaskCreator ctc = new ConfigTaskCreator(rcl, ca, ip);
        ctc.configurationEvent(ce);

        // create attributes
        final Map<String, Object> attr = new HashMap<>();
        attr.put(SERVICE_PID, "a.b.c~c1");
        attr.put(SERVICE_FACTORYPID, "a.b.c");
        attr.put(RESOURCE_URI_HINT, "a.b.c~c1");
        verify(rcl, times(1)).resourceAddedOrUpdated(eq(TYPE_CONFIG), eq("a.b.c~c1"), isNull(), eq(d), eq(attr));
    }

    @Test
    public void testUpdateEventWithNullFactoryPid() throws InvalidSyntaxException, IOException {
        final ConfigurationAdmin ca = mock(ConfigurationAdmin.class);
        final Configuration c = mock(Configuration.class);
        final Dictionary d = new Hashtable();
        d.put(ConfigurationConstants.PROPERTY_PERSISTENCE, "true");
        when(c.getProperties()).thenReturn(d);
        when(ca.listConfigurations(anyString())).thenReturn(new Configuration[]{c});
        final ServiceReference sr = mock(ServiceReference.class);
        final ResourceChangeListener rcl = mock(ResourceChangeListener.class);
        final InfoProvider ip = mock(InfoProvider.class);
        final ConfigurationEvent ce = new ConfigurationEvent(sr, 1, null, "a.b.c.c1");
        final ConfigTaskCreator ctc = new ConfigTaskCreator(rcl, ca, ip);
        ctc.configurationEvent(ce);

        // create attributes
        final Map<String, Object> attr = new HashMap<>();
        attr.put(SERVICE_PID, "a.b.c.c1");
        attr.put(RESOURCE_URI_HINT, "a.b.c.c1");
        verify(rcl, times(1)).resourceAddedOrUpdated(eq(TYPE_CONFIG), eq("a.b.c.c1"), isNull(), eq(d), eq(attr));
    }
}