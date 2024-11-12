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

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;

import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;

import static org.junit.Assert.assertEquals;

public class MetatypeHandlerTest {

    @Test
    public void testUpdateConfiguration() throws Exception {
        final BundleContext bundleContext = Mockito.mock(BundleContext.class);
        final MetaTypeService mts = Mockito.mock(MetaTypeService.class);
        final Bundle bundle = Mockito.mock(Bundle.class);
        Mockito.when(bundleContext.getBundles()).thenReturn(new Bundle[] {bundle});

        final MetaTypeInformation info = Mockito.mock(MetaTypeInformation.class);
        Mockito.when(mts.getMetaTypeInformation(bundle)).thenReturn(info);
        final MetatypeHandler handler = new MetatypeHandler(mts, bundleContext);

        final ObjectClassDefinition ocd = Mockito.mock(ObjectClassDefinition.class);
        Mockito.when(info.getObjectClassDefinition("my.pid", null)).thenReturn(ocd);

        final AttributeDefinition ada = Mockito.mock(AttributeDefinition.class);
        Mockito.when(ada.getID()).thenReturn("a");
        Mockito.when(ada.getDefaultValue()).thenReturn(new String[] {"1"});
        Mockito.when(ada.getCardinality()).thenReturn(1);
        Mockito.when(ada.getType()).thenReturn(AttributeDefinition.STRING);

        final AttributeDefinition adb = Mockito.mock(AttributeDefinition.class);
        Mockito.when(adb.getID()).thenReturn("b");
        Mockito.when(adb.getDefaultValue()).thenReturn(new String[] {"2"});
        Mockito.when(adb.getCardinality()).thenReturn(1);
        Mockito.when(adb.getType()).thenReturn(AttributeDefinition.STRING);

        final AttributeDefinition adc = Mockito.mock(AttributeDefinition.class);
        Mockito.when(adc.getID()).thenReturn("c");
        Mockito.when(adc.getDefaultValue()).thenReturn(new String[] {"3"});
        Mockito.when(adc.getCardinality()).thenReturn(1);
        Mockito.when(adc.getType()).thenReturn(AttributeDefinition.STRING);

        final AttributeDefinition add = Mockito.mock(AttributeDefinition.class);
        Mockito.when(add.getID()).thenReturn("d");
        Mockito.when(add.getDefaultValue()).thenReturn(new String[] {"4"});
        Mockito.when(add.getCardinality()).thenReturn(1);
        Mockito.when(add.getType()).thenReturn(AttributeDefinition.INTEGER);

        final AttributeDefinition adE = Mockito.mock(AttributeDefinition.class);
        Mockito.when(adE.getID()).thenReturn("e");
        Mockito.when(adE.getDefaultValue()).thenReturn(new String[] {"5"});
        Mockito.when(adE.getCardinality()).thenReturn(1);
        Mockito.when(adE.getType()).thenReturn(AttributeDefinition.INTEGER);

        final AttributeDefinition adF = Mockito.mock(AttributeDefinition.class);
        Mockito.when(adF.getID()).thenReturn("f");
        Mockito.when(adF.getDefaultValue()).thenReturn(new String[] {"/a", "/b"});
        Mockito.when(adF.getCardinality()).thenReturn(-100);
        Mockito.when(adF.getType()).thenReturn(AttributeDefinition.STRING);

        final AttributeDefinition adG = Mockito.mock(AttributeDefinition.class);
        Mockito.when(adG.getID()).thenReturn("g");
        Mockito.when(adG.getDefaultValue()).thenReturn(new String[] {"/x", "/y"});
        Mockito.when(adG.getCardinality()).thenReturn(-100);
        Mockito.when(adG.getType()).thenReturn(AttributeDefinition.STRING);

        Mockito.when(ocd.getAttributeDefinitions(ObjectClassDefinition.ALL))
                .thenReturn(new AttributeDefinition[] {ada, adb, adc, add, adE, adF, adG});

        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("a", "2");
        props.put("c", "3");
        props.put("d", 4);
        props.put("e", 5);
        props.put("f", Arrays.asList("/a", "/b"));
        props.put("g", Arrays.asList("/a", "/b"));

        final Dictionary<String, Object> defaultProps = new Hashtable<>();
        defaultProps.put("b", "5");
        defaultProps.put("d", 7);

        handler.updateConfiguration(null, "my.pid", props, defaultProps);

        assertEquals(3, props.size());
        assertEquals("2", props.get("a"));
        assertEquals(4, props.get("d"));
        assertEquals(Arrays.asList("/a", "/b"), props.get("g"));
    }
}
