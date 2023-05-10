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

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.metatype.AttributeDefinition;
import org.osgi.service.metatype.MetaTypeInformation;
import org.osgi.service.metatype.MetaTypeService;
import org.osgi.service.metatype.ObjectClassDefinition;
import org.osgi.util.converter.Converters;

public class MetatypeHandler {

    private final MetaTypeService srv;

    private final BundleContext bundleContext;

    public MetatypeHandler(final MetaTypeService mts, final BundleContext bundleContext) {
        this.srv = mts;
        this.bundleContext = bundleContext;
    }
    
    public void updateConfiguration(final String factoryPid,
            final String pid,
            final Dictionary<String, Object> props, 
            final Dictionary<String, Object> defaultProps) {
        // search metatype
        final ObjectClassDefinition ocd;
        if ( factoryPid != null ) {
            ocd = this.getObjectClassDefinition( factoryPid );
        } else {
            ocd = this.getObjectClassDefinition(  pid );
        }

        if ( ocd != null ) {
            for(final AttributeDefinition ad : ocd.getAttributeDefinitions(ObjectClassDefinition.ALL)) {
                final String propName = ad.getID();
                final Object newValue = props.get(propName);
                if ( newValue != null 
                        && (defaultProps == null || defaultProps.get(propName) == null) ) {
                    if ( ad.getCardinality() == 0 ) {
                        if ( !shouldSet(ad, newValue.toString())) {
                            props.remove(propName);                            
                        }
                    } else {
                        final String[] array = Converters.standardConverter().convert(newValue).to(String[].class);
                        if ( !shouldSet(ad, array)) {
                            props.remove(propName);
                        }                        
                    }        
                }
            }
        }
    }

    private ObjectClassDefinition getObjectClassDefinition( final String pid ) {
        for(final Bundle b : this.bundleContext.getBundles()) {
            try {
                final MetaTypeInformation mti = this.srv.getMetaTypeInformation( b );
                if ( mti != null ) {
                    final ObjectClassDefinition ocd = mti.getObjectClassDefinition( pid, null );;
                    if ( ocd != null ) {
                        return ocd;
                    }
                }
            } catch ( final IllegalArgumentException iae ) {
                // ignore
            }
        }
        return null;
    }

    boolean shouldSet(final AttributeDefinition ad, final String value) {
        if ( value.isEmpty() && ad.getDefaultValue() == null ) {
            return false;
        }
        if ( ad.getDefaultValue() != null && value.equals(ad.getDefaultValue()[0]) ) {
            return false;
        }
        return true;
    }

    boolean shouldSet(final AttributeDefinition ad, final String[] values) {
        if ( ad.getDefaultValue() == null ) {
            if ( values.length == 0 || (values.length == 1 && values[0].isEmpty() ) ) {
                return false;
            }
        }
        if ( ad.getDefaultValue() != null && Arrays.equals(ad.getDefaultValue(), values) ) {
            return false;
        }
        return true;
    }

}
