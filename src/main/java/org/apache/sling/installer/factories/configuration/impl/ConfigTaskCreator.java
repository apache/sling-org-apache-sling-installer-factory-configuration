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
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.ResourceChangeListener;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.api.tasks.ChangeStateTask;
import org.apache.sling.installer.api.tasks.InstallTask;
import org.apache.sling.installer.api.tasks.InstallTaskFactory;
import org.apache.sling.installer.api.tasks.RegisteredResource;
import org.apache.sling.installer.api.tasks.ResourceState;
import org.apache.sling.installer.api.tasks.ResourceTransformer;
import org.apache.sling.installer.api.tasks.TaskResource;
import org.apache.sling.installer.api.tasks.TaskResourceGroup;
import org.apache.sling.installer.api.tasks.TransformationResult;
import org.apache.sling.installer.factories.configuration.ConfigurationConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task creator for configurations.
 */
public class ConfigTaskCreator
    implements InstallTaskFactory, ConfigurationListener, ResourceTransformer {

    /** Logger. */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** Configuration admin. */
    private final ConfigurationAdmin configAdmin;

    /** Resource change listener. */
    private final ResourceChangeListener changeListener;

    /** Info Provider */
    private final InfoProvider infoProvider;

    public ConfigTaskCreator(final ResourceChangeListener listener,
            final ConfigurationAdmin configAdmin,
            final InfoProvider infoProvider) {
        this.changeListener = listener;
        this.configAdmin = configAdmin;
        this.infoProvider = infoProvider;
    }

    public ServiceRegistration<?> register(final BundleContext bundleContext) {
        final Hashtable<String, String> props = new Hashtable<>();
        props.put(Constants.SERVICE_DESCRIPTION, "Apache Sling Configuration Install Task Factory");
        props.put(Constants.SERVICE_VENDOR, ServicesListener.VENDOR);
        props.put(InstallTaskFactory.NAME, "org.osgi.service.cm");
        props.put(ResourceTransformer.NAME, "org.osgi.service.cm");

        final String [] serviceInterfaces = {
                InstallTaskFactory.class.getName(),
                ConfigurationListener.class.getName(),
                ResourceTransformer.class.getName()
        };
        final ServiceRegistration<?> reg = bundleContext.registerService(serviceInterfaces, this, props);
        this.logger.info("OSGi Configuration support for OSGi installer active, default location={}, merge schemes={}", 
                Activator.DEFAULT_LOCATION, Activator.MERGE_SCHEMES);
        return reg;
    }

    /**
     * Create a task to install or uninstall a configuration.
     *
	 * @see org.apache.sling.installer.api.tasks.InstallTaskFactory#createTask(org.apache.sling.installer.api.tasks.TaskResourceGroup)
	 */
	@Override
    public InstallTask createTask(final TaskResourceGroup group) {
        final TaskResource toActivate = group.getActiveResource();
        if ( !toActivate.getType().equals(InstallableResource.TYPE_CONFIG) ) {
            return null;
        }

        final InstallTask result;
		if (toActivate.getState() == ResourceState.UNINSTALL) {
            // if this is an uninstall, check if we have to install an older version
            // in this case we should do an update instead of uninstall/install (!)
            final TaskResource second = group.getNextActiveResource();
            if ( second != null
                && ( second.getState() == ResourceState.IGNORED || second.getState() == ResourceState.INSTALLED || second.getState() == ResourceState.INSTALL )
                && ( second.getDictionary() == null || second.getDictionary().get(InstallableResource.RESOURCE_IS_TEMPLATE) == null)) {
                result = new ChangeStateTask(group, ResourceState.UNINSTALLED);
            } else {
                result = new ConfigRemoveTask(group, this.configAdmin);
            }
		} else {
	        result = new ConfigInstallTask(group, this.configAdmin);
		}
		return result;
	}

    /**
     * @see org.osgi.service.cm.ConfigurationListener#configurationEvent(org.osgi.service.cm.ConfigurationEvent)
     */
    @Override
    public void configurationEvent(final ConfigurationEvent event) {
        synchronized ( Coordinator.SHARED ) {
            // pid in R7 format to be sent to change listener so that it can be installed as a factory config
            // and grouped together along with same factory configs
            final String pid = ConfigUtil.getPid(event);
            if ( event.getType() == ConfigurationEvent.CM_DELETED ) {
                final Coordinator.Operation op = Coordinator.SHARED.get(event.getPid(), event.getFactoryPid(), true);
                if ( op == null ) {
                    this.changeListener.resourceRemoved(InstallableResource.TYPE_CONFIG, pid);
                } else {
                    this.logger.debug("Ignoring configuration event for {}:{}", event.getPid(), event.getFactoryPid());
                }
            } else if ( event.getType() == ConfigurationEvent.CM_UPDATED ) {
                try {
                    // we just need to pass in the pid as we're using named factory configs
                    final Configuration config = ConfigUtil.getConfiguration(configAdmin,
                            null,
                            event.getPid());
                    final Coordinator.Operation op = Coordinator.SHARED.get(event.getPid(), event.getFactoryPid(), false);
                    if ( config != null && op == null ) {
                        final boolean persist = ConfigUtil.toBoolean(config.getProperties().get(ConfigurationConstants.PROPERTY_PERSISTENCE), true);

                        final Dictionary<String, Object> dict = ConfigUtil.cleanConfiguration(config.getProperties());
                        final Map<String, Object> attrs = new HashMap<>();
                        if ( !persist ) {
                            attrs.put(ResourceChangeListener.RESOURCE_PERSIST, Boolean.FALSE);
                        }
                        attrs.put(Constants.SERVICE_PID, pid);
                        attrs.put(InstallableResource.RESOURCE_URI_HINT, pid);
                        if ( config.getBundleLocation() != null ) {
                            attrs.put(InstallableResource.INSTALLATION_HINT, config.getBundleLocation());
                        }
                        // Factory?
                        if (event.getFactoryPid() != null) {
                            attrs.put(ConfigurationAdmin.SERVICE_FACTORYPID, event.getFactoryPid());
                        }

                        removeDefaultProperties(this.infoProvider, event.getPid(), dict);
                        this.changeListener.resourceAddedOrUpdated(InstallableResource.TYPE_CONFIG, pid, null, dict, attrs);

                    } else {
                        this.logger.debug("Ignoring configuration event for {}:{}", event.getPid(), event.getFactoryPid());
                    }
                } catch ( final Exception ignore) {
                    // ignore for now
                }
            }
        }
    }

    public static Dictionary<String, Object> getDefaultProperties(final InfoProvider infoProvider, final String pid) {
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

    public static void removeDefaultProperties(final InfoProvider infoProvider, final String pid, final Dictionary<String, Object> dict) {
        if ( Activator.MERGE_SCHEMES != null ) {
            final Dictionary<String, Object> defaultProps = getDefaultProperties(infoProvider, pid);
            if ( defaultProps != null ) {
                ConfigUtil.removeRedundantProperties(dict, defaultProps);
            }
        }
    }

    /**
     * @see org.apache.sling.installer.api.tasks.ResourceTransformer#transform(org.apache.sling.installer.api.tasks.RegisteredResource)
     */
    @Override
    public TransformationResult[] transform(final RegisteredResource resource) {
        if ( resource.getType().equals(InstallableResource.TYPE_PROPERTIES) ) {
            return checkConfiguration(resource);
        }
        return null;
    }

    private static String getResourceId(final String rawUrl) {
        final String url = separatorsToUnix(rawUrl);
        int pos = url.lastIndexOf('/');
        if ( pos == -1 ) {
            pos = url.indexOf(':');
        }

        final String lastIdPart;
        if ( pos != -1 ) {
            lastIdPart = url.substring(pos + 1);
        } else {
            lastIdPart = url;
        }
        return lastIdPart;
    }

    /**
     * Check if the registered resource is a configuration
     * @param resource The resource
     */
    private TransformationResult[] checkConfiguration(final RegisteredResource resource) {
        final String lastIdPart = getResourceId(resource.getURL());

        // remove extension if known
        final String pid = removeConfigExtension(lastIdPart);

        // split pid and factory pid alias
        final Map<String, Object> attr = new HashMap<>();
        final String factoryPid;
        final String configPid;
        int n = pid.indexOf('~');
        if ( n == -1 ) {
            n = pid.indexOf('-');
        }
        if (n > 0) {
            configPid = pid.substring(n + 1);
            factoryPid = pid.substring(0, n);
            attr.put(ConfigurationAdmin.SERVICE_FACTORYPID, factoryPid);
        } else {
            factoryPid = null;
            configPid = pid;
        }
        attr.put(Constants.SERVICE_PID, configPid);

        final TransformationResult tr = new TransformationResult();
        final String id = (factoryPid == null ? configPid : ConfigUtil.getPIDOfFactoryPID(factoryPid, configPid));
        tr.setId(id);
        tr.setResourceType(InstallableResource.TYPE_CONFIG);
        tr.setAttributes(attr);

        return new TransformationResult[] {tr};
    }

    private static final List<String> EXTENSIONS = Arrays.asList(".config", ".properties", ".cfg", ".cfg.json");
    private static String removeConfigExtension(final String id) {
        for(final String ext : EXTENSIONS) {
            if ( id.endsWith(ext) ) {
                return id.substring(0, id.length() - ext.length());
            }
        }
        return id;
    }

    /**
     * Converts all separators to the Unix separator of forward slash.
     *
     * @param path  the path to be changed, null ignored
     * @return the updated path
     */
    private static String separatorsToUnix(final String path) {
        if (path == null || path.indexOf('\\') == -1) {
            return path;
        }
        return path.replace('\\', '/');
    }
}
