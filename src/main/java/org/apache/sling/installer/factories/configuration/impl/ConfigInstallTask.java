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
import java.text.MessageFormat;

import org.apache.sling.installer.api.tasks.InstallationContext;
import org.apache.sling.installer.api.tasks.ResourceState;
import org.apache.sling.installer.api.tasks.TaskResourceGroup;
import org.apache.sling.installer.factories.configuration.ConfigurationConstants;
import org.apache.sling.installer.factories.configuration.impl.Coordinator.Operation;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Task to install a configuration
 */
public class ConfigInstallTask extends AbstractConfigTask {

    private static final String CONFIG_INSTALL_ORDER = "20-";

    public ConfigInstallTask(final TaskResourceGroup r, final ConfigurationAdmin configAdmin) {
        super(r, configAdmin);
    }

    @Override
    public String getSortKey() {
        return CONFIG_INSTALL_ORDER + getRealPID();
    }

	@Override
    public void execute(final InstallationContext ctx) {
        synchronized ( Coordinator.SHARED ) {
            // Get or create configuration, but do not
            // update if the new one has the same values.
            boolean created = false;
            try {
                String location = (String)this.getResource().getDictionary().get(ConfigurationConstants.PROPERTY_BUNDLE_LOCATION);
                if ( location == null ) {
                    location = Activator.DEFAULT_LOCATION; // default
                } else if ( location.length() == 0 ) {
                    location = null;
                }

                Configuration config = ConfigUtil.getConfiguration(this.getConfigurationAdmin(), this.factoryPid, this.configPid);
                if (config == null) {

                    config = ConfigUtil.createConfiguration(this.getConfigurationAdmin(), this.factoryPid, this.configPid, location);
                    created = true;
                } else {
        			if (ConfigUtil.isSameData(config.getProperties(), getResource().getDictionary())) {
        			    this.getLogger().debug("Configuration {} already installed with same data, update request ignored: {}",
        	                        config.getPid(), getResource());
        				config = null;
        			} else {
                        config.setBundleLocation(location);
        			}
                }

                if (config != null) {
                    config.update(getDictionary());
                    ctx.log("Installed configuration {} from resource {}", config.getPid(), getResource());
                    this.getLogger().debug("Configuration " + config.getPid()
                                + " " + (created ? "created" : "updated")
                                + " from " + getResource());
                    final Operation op = new Coordinator.Operation(config.getPid(), config.getFactoryPid(), false);
                    Coordinator.SHARED.add(op);
                }
                // in any case set the state to "INSTALLED"
                // (it doesn't matter if the configuration hasn't been updated as it has been in the correct state already)
                this.setFinishedState(ResourceState.INSTALLED);
            } catch (IOException|IllegalStateException e) {
                this.getLogger().debug("Temporary exception during installation of config " + this.getResource() + " : " + e.getMessage() + ". Retrying later.", e);
            } catch (Exception e) {
                String message = MessageFormat.format("Exception during installation of config {0} : {1}", this.getResource(), e.getMessage());
                this.getLogger().error(message, e);
                this.setFinishedState(ResourceState.IGNORED, null, message);
            }
        }
    }
}