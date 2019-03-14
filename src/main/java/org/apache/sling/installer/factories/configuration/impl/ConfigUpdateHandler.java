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
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.tasks.ResourceUpdater;
import org.apache.sling.installer.api.tasks.UpdatableResourceGroup;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update handler for updating 1.x to 1.2
 */
public class ConfigUpdateHandler implements ResourceUpdater {

    /** Logger. */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** Configuration admin. */
    private final ConfigurationAdmin configAdmin;

    private final ServicesListener activator;


    public ConfigUpdateHandler(final ConfigurationAdmin configAdmin,
            final ServicesListener activator) {
        this.configAdmin = configAdmin;
        this.activator = activator;
    }

    public ServiceRegistration<?> register(final BundleContext bundleContext) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(Constants.SERVICE_DESCRIPTION, "Apache Sling Configuration Install Task Factory Update Handler");
        props.put(Constants.SERVICE_VENDOR, ServicesListener.VENDOR);

        final String [] serviceInterfaces = {
                ResourceUpdater.class.getName()
        };
        return bundleContext.registerService(serviceInterfaces, this, props);
    }

    @Override
    public void update(final Collection<UpdatableResourceGroup> groups) {
        for(final UpdatableResourceGroup group : groups) {
            update(group);
        }
        this.activator.finishedUpdating();
    }

    private void update(final UpdatableResourceGroup group) {
        if ( this.activator.isActive() ) {
            // check if the group handles configurations and has an alias (aka factory config)
            if ( InstallableResource.TYPE_CONFIG.equals(group.getResourceType()) && group.getAlias() != null ) {
                this.updateFactoryConfig(group);
            }
        }
    }

    protected String[] getFactoryPidAndPid(final String alias, final String oldId) {
        int pos = 0;
        while ( alias.charAt(pos) == oldId.charAt(pos) ) {
            pos++;
        }
        while (alias.charAt(pos - 1) != '.') {
            pos--;
        }

        final String factoryPid = alias.substring(0, pos - 1);
        final String pid = oldId.substring(factoryPid.length() + 1);

        return new String[] { factoryPid, pid };
    }

    private void updateFactoryConfig(final UpdatableResourceGroup group) {
        final String alias = group.getAlias();
        final String oldId = group.getId();

        // change group id
        final String[] result = getFactoryPidAndPid(alias, oldId);

        final String factoryPid = result[0];
        final String pid = result[1];

        final String newId = ConfigUtil.getPIDOfFactoryPID(factoryPid, pid);
        group.setId(newId);
        // clear alias
        group.setAlias(null);

        this.logger.debug("Updating factory configuration from {} to {}", oldId, newId);
        try {
            final Configuration cfg = ConfigUtil.getLegacyFactoryConfig(this.configAdmin, factoryPid, alias, pid);
            if ( cfg != null ) {
                // keep existing values / location
                final String location = cfg.getBundleLocation();
                final Dictionary<String, Object> dict = ConfigUtil.cleanConfiguration(cfg.getProperties());
                // delete old factory configuration
                cfg.delete();
                // create new named factory configuration with same properties and bundle location
                final Configuration upCfg = this.configAdmin.getFactoryConfiguration(factoryPid, pid, location);
                upCfg.update(dict);
            }
        } catch ( final IOException | InvalidSyntaxException io) {
            // ignore for now
        }
        group.update();
    }
}
