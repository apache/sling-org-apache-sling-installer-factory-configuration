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

import java.io.IOException;
import java.util.Dictionary;

import org.apache.felix.webconsole.spi.ConfigurationHandler;
import org.apache.felix.webconsole.spi.ValidationException;
import org.apache.sling.installer.api.info.InfoProvider;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

public class WebconsoleConfigurationHandler implements ConfigurationHandler {

    static final String META_TYPE_NAME = "org.osgi.service.metatype.MetaTypeService"; 

    private final InfoProvider infoProvider;

    private final ServiceTracker<Object, Object> metatypeTracker;

    private final BundleContext bundleContext;

    public WebconsoleConfigurationHandler(final BundleContext context, final InfoProvider infoProvider) {
        this.infoProvider = infoProvider;
        this.bundleContext = context;
        this.metatypeTracker = new ServiceTracker<>(context, META_TYPE_NAME, null);
        this.metatypeTracker.open();    
    }

    public void deactivate() {
        this.metatypeTracker.close();
    }

    @Override
    public void createConfiguration(final String pid) throws ValidationException, IOException {
        // nothing to do        
    }

    @Override
    public void createFactoryConfiguration(final String factoryPid, String name) throws ValidationException, IOException {
        // nothing to do        
    }

    @Override
    public void deleteConfiguration(final String factoryPid, final String pid) throws ValidationException, IOException {
        // nothing to do        
    }

    @Override
    public void updateConfiguration(final String factoryPid, final String pid, final Dictionary<String, Object> props)
            throws ValidationException, IOException {
        final Object mts = this.metatypeTracker.getService();
        if ( mts != null ) {
            final Dictionary<String, Object> defaultProps = ConfigTaskCreator.getDefaultProperties(infoProvider, pid);
            final MetatypeHandler mt = new MetatypeHandler(mts, this.bundleContext);
            mt.updateConfiguration(factoryPid, pid, props, defaultProps);
        }        
    }
    
}
